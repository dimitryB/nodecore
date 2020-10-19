// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2020 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.veriblock.spv.net

import com.google.protobuf.ByteString
import io.ktor.network.selector.ActorSelectorManager
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.util.network.NetworkAddress
import io.ktor.util.network.hostname
import io.ktor.util.network.port
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.selects.select
import nodecore.api.grpc.VeriBlockMessages
import nodecore.api.grpc.VeriBlockMessages.Event.ResultsCase
import nodecore.api.grpc.VeriBlockMessages.LedgerProofReply.LedgerProofResult
import nodecore.api.grpc.VeriBlockMessages.LedgerProofRequest
import nodecore.api.grpc.VeriBlockMessages.TransactionAnnounce
import nodecore.api.grpc.utilities.ByteStringUtility
import nodecore.api.grpc.utilities.extensions.toHex
import org.veriblock.core.bitcoinj.Base58
import org.veriblock.core.crypto.BloomFilter
import org.veriblock.core.utilities.createLogger
import org.veriblock.core.crypto.Sha256Hash
import org.veriblock.core.utilities.debugWarn
import org.veriblock.core.wallet.AddressPubKey
import org.veriblock.sdk.models.VeriBlockBlock
import org.veriblock.spv.SpvContext
import org.veriblock.spv.model.DownloadStatus
import org.veriblock.spv.model.DownloadStatusResponse
import org.veriblock.spv.model.LedgerContext
import org.veriblock.spv.model.NetworkMessage
import org.veriblock.spv.model.NodeMetadata
import org.veriblock.spv.model.StandardTransaction
import org.veriblock.spv.model.Transaction
import org.veriblock.spv.model.TransactionTypeIdentifier
import org.veriblock.spv.model.mapper.LedgerProofReplyMapper
import org.veriblock.spv.serialization.MessageSerializer
import org.veriblock.spv.serialization.MessageSerializer.deserializeNormalTransaction
import org.veriblock.spv.service.Blockchain
import org.veriblock.spv.service.OutputData
import org.veriblock.spv.service.PendingTransactionContainer
import org.veriblock.spv.service.TransactionData
import org.veriblock.spv.service.TransactionInfo
import org.veriblock.spv.service.TransactionType
import org.veriblock.spv.util.SpvEventBus
import org.veriblock.spv.util.Threading
import org.veriblock.spv.util.buildMessage
import org.veriblock.spv.util.nextMessageId
import org.veriblock.spv.util.launchWithFixedDelay
import org.veriblock.spv.validator.LedgerProofReplyValidator
import java.io.IOException
import java.nio.channels.ClosedChannelException
import java.sql.SQLException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val logger = createLogger {}

const val DEFAULT_CONNECTIONS = 12
const val BLOOM_FILTER_TWEAK = 710699166
const val BLOOM_FILTER_FALSE_POSITIVE_RATE = 0.02
const val BLOCK_DIFFERENCE_TO_SWITCH_ON_ANOTHER_PEER = 200
const val AMOUNT_OF_BLOCKS_WHEN_WE_CAN_START_WORKING = 4//50

class SpvPeerTable(
    private val spvContext: SpvContext,
    private val p2PService: P2PService,
    peerDiscovery: PeerDiscovery,
    pendingTransactionContainer: PendingTransactionContainer
) {
    private val lock = ReentrantLock()
    private val running = AtomicBoolean(false)
    private val discovery: PeerDiscovery
    private val blockchain: Blockchain
    var maximumPeers = DEFAULT_CONNECTIONS
    var downloadPeer: SpvPeer? = null
    val bloomFilter: BloomFilter
    private val addressesState: MutableMap<String, LedgerContext> = ConcurrentHashMap()
    private val pendingTransactionContainer: PendingTransactionContainer

    private val peers = ConcurrentHashMap<String, SpvPeer>()
    private val pendingPeers = ConcurrentHashMap<String, SpvPeer>()
    private val incomingQueue: Channel<NetworkMessage> = Channel(UNLIMITED)

    private val hashDispatcher = Threading.HASH_EXECUTOR.asCoroutineDispatcher();
    private val coroutineDispatcher = Threading.PEER_TABLE_THREAD.asCoroutineDispatcher()
    private val coroutineScope = CoroutineScope(coroutineDispatcher)

    private val selectorManager = ActorSelectorManager(coroutineDispatcher)

    init {
        bloomFilter = createBloomFilter()
        blockchain = spvContext.blockchain
        discovery = peerDiscovery
        this.pendingTransactionContainer = pendingTransactionContainer

        SpvEventBus.pendingTransactionDownloadedEvent.register(
            spvContext.pendingTransactionDownloadedListener,
            spvContext.pendingTransactionDownloadedListener::onPendingTransactionDownloaded
        )
    }

    fun start() {
        running.set(true)

        SpvEventBus.peerConnectedEvent.register(this, ::onPeerConnected)
        SpvEventBus.peerDisconnectedEvent.register(this, ::onPeerDisconnected)
        SpvEventBus.messageReceivedEvent.register(this) {
            onMessageReceived(it.message, it.peer)
        }
        coroutineScope.launchWithFixedDelay (30_000L, 60_000L){
            requestAddressState()
        }
        coroutineScope.launchWithFixedDelay(200L, 20_000L) {
            discoverPeers()
        }
        coroutineScope.launchWithFixedDelay(5_000L, 20_000L) {
            requestPendingTransactions()
        }

        // Scheduling with a fixed delay allows it to recover in the event of an unhandled exception
        val messageHandlerScope = CoroutineScope(Threading.MESSAGE_HANDLER_THREAD.asCoroutineDispatcher())
        messageHandlerScope.launch {
            processIncomingMessages()
        }
    }

    fun shutdown() {
        running.set(false)

        // Close peer connections
        incomingQueue.close()
        pendingPeers.clear()
        peers.forEachValue(4) {
            it.closeConnection()
        }
    }

    suspend fun connectTo(address: NetworkAddress): SpvPeer {
        peers[address.hostname]?.let {
            return it
        }
        val socket = try {
            aSocket(selectorManager)
                .tcp()
                .connect(address)
        } catch (e: IOException) {
            logger.debug("Unable to open connection to $address", e)
            throw e
        }
        val peer = createPeer(socket)
        lock.withLock {
            pendingPeers[address.hostname] = peer
        }
        return peer
    }

    fun createPeer(socket: Socket): SpvPeer {
        return SpvPeer(spvContext, blockchain, NodeMetadata, socket)
    }

    fun startBlockchainDownload(peer: SpvPeer) {
        logger.debug("Beginning blockchain download")
        try {
            downloadPeer = peer
            peer.startBlockchainDownload()
        } catch (ex: Exception) {
            downloadPeer = null
            //TODO SPV-70 add bun on some time.
            logger.error(ex.message, ex)
        }
    }

    fun acknowledgeAddress(address: AddressPubKey) {
        addressesState.putIfAbsent(
            address.hash, LedgerContext(
            null, null, null, null
        )
        )
    }

    private suspend fun requestAddressState() {
        val addresses = spvContext.addressManager.getAll()
        if (addresses.isEmpty()) {
            return
        }
        try {
            val request = buildMessage {
                ledgerProofRequest = LedgerProofRequest.newBuilder().apply {
                    for (address in addresses) {
                        acknowledgeAddress(address)
                        addAddresses(ByteString.copyFrom(Base58.decode(address.hash)))
                    }
                }.build()
            }
            val response = requestMessage(request, 30_000L)
            val proofReply = response.ledgerProofReply.proofsList
            val ledgerContexts: List<LedgerContext> = proofReply.asSequence().filter { lpr: LedgerProofResult ->
                addressesState.containsKey(Base58.encode(lpr.address.toByteArray()))
            }.filter {
                LedgerProofReplyValidator.validate(it)
            }.map {
                LedgerProofReplyMapper.map(it)
            }.toList()
            updateAddressState(ledgerContexts)
        } catch (e: Exception) {
            logger.debugWarn(e) { "Unable to request address state" }
        }
    }

    private suspend fun requestPendingTransactions() {
        val pendingTransactionIds = pendingTransactionContainer.getPendingTransactionIds()
        try {
            for (sha256Hash in pendingTransactionIds) {
                val request = buildMessage {
                    transactionRequest = VeriBlockMessages.GetTransactionRequest.newBuilder()
                        .setId(ByteString.copyFrom(sha256Hash.bytes))
                        .build()
                }
                val response = requestMessage(request)
                if (response.transactionReply.success) {
                    pendingTransactionContainer.updateTransactionInfo(response.transactionReply.transaction.toModel())
                }
            }
        } catch (e: Exception) {
            logger.debugWarn(e) { "Unable to request pending transactions" }
        }
    }

    private suspend fun discoverPeers() {
        val maxConnections = maximumPeers
        if (maxConnections > 0 && countConnectedPeers() >= maxConnections) {
            return
        }
        val needed = maxConnections - (countConnectedPeers() + countPendingPeers())
        if (needed > 0) {
            val candidates = discovery.getPeers(needed)
            for (address in candidates) {
                if (peers.containsKey(address.hostname) || pendingPeers.containsKey(address.hostname)) {
                    continue
                }
                logger.debug("Attempting connection to {}:{}", address.hostname, address.port)
                val peer = try {
                    connectTo(address)
                } catch (e: IOException) {
                    continue
                }
                logger.debug("Discovered peer connected {}:{}", peer.address, peer.port)
            }
        }
    }

    private suspend fun processIncomingMessages() {
        try {
            for ((sender, message) in incomingQueue) {
                logger.debug { "Processing ${message.resultsCase.name} message from ${sender.address}" }
                when (message.resultsCase) {
                    ResultsCase.HEARTBEAT -> {
                        val heartbeat = message.heartbeat
                        // Copy reference to local scope
                        val downloadPeer = downloadPeer
                        if (downloadPeer == null && heartbeat.block.number > 0) {
                            startBlockchainDownload(sender)
                        } else if (downloadPeer != null &&
                            heartbeat.block.number - downloadPeer.bestBlockHeight > BLOCK_DIFFERENCE_TO_SWITCH_ON_ANOTHER_PEER
                        ) {
                            startBlockchainDownload(sender)
                        }
                    }
                    ResultsCase.ADVERTISE_BLOCKS -> {
                        val advertiseBlocks = message.advertiseBlocks
                        logger.debug {
                            val lastBlock = MessageSerializer.deserialize(advertiseBlocks.headersList.last())
                            "Received advertisement of ${advertiseBlocks.headersList.size} blocks," +
                                " height: ${lastBlock.height}"
                        }
                        val trustHashes = spvContext.trustPeerHashes && advertiseBlocks.headersList.size > 10
                        val veriBlockBlocks: List<VeriBlockBlock> = coroutineScope {
                            advertiseBlocks.headersList.map {
                                async(hashDispatcher) {
                                    val block = MessageSerializer.deserialize(it, trustHashes)
                                    // pre-calculate hash in parallel
                                    block.hash
                                    block
                                }
                            }.awaitAll()
                        }
                        if (downloadPeer == null && veriBlockBlocks.last().height > 0) {
                            startBlockchainDownload(sender)
                        }

                        val allBlocksAccepted = veriBlockBlocks
                            .sortedBy { it.height }
                            .all { blockchain.acceptBlock(it) }

                        // TODO(warchant): if allBlocksAccepted == false here, block can not be connected or invalid
                        // maybe ban peer? for now, do nothing
                    }
                    ResultsCase.TRANSACTION -> {
                        // TODO: Different Transaction types
                        val standardTransaction = deserializeNormalTransaction(message.transaction)
                        notifyPendingTransactionDownloaded(standardTransaction)
                    }
                    ResultsCase.TX_REQUEST -> {
                        val txIds = message.txRequest.transactionsList.map {
                            Sha256Hash.wrap(
                                ByteStringUtility.byteStringToHex(it.txId)
                            )
                        }
                        p2PService.onTransactionRequest(txIds, sender)
                    }
                    else -> {
                        // Ignore the other message types as they are irrelevant for SPV
                    }
                }
            }
        } catch (ignored: ClosedReceiveChannelException) {
            return
        } catch (t: Throwable) {
            logger.error("An unhandled exception occurred processing message queue", t)
        }
    }

    private fun createBloomFilter(): BloomFilter {
        val addresses = spvContext.addressManager.all
        val filter = BloomFilter(
            spvContext.addressManager.numAddresses + 10, BLOOM_FILTER_FALSE_POSITIVE_RATE,
            BLOOM_FILTER_TWEAK
        )
        for (address in addresses) {
            filter.insert(address.hash)
        }
        return filter
    }

    private fun updateAddressState(ledgerContexts: List<LedgerContext>) {
        for (ledgerContext in ledgerContexts) {
            if (addressesState.getValue(ledgerContext.address!!.address) > ledgerContext) {
                addressesState.replace(ledgerContext.address.address, ledgerContext)
            }
        }
    }

    private fun notifyPendingTransactionDownloaded(tx: StandardTransaction) {
        SpvEventBus.pendingTransactionDownloadedEvent.trigger(tx)
    }

    fun onPeerConnected(peer: SpvPeer) = lock.withLock {
        logger.debug("Peer {} connected", peer.address)
        pendingPeers.remove(peer.address)
        peers[peer.address] = peer

        // TODO: Wallet related setup (bloom filter)

        // Attach listeners
        peer.setFilter(bloomFilter)

        peer.sendMessage {
            stateInfoRequest = VeriBlockMessages.GetStateInfoRequest.getDefaultInstance()
        }

        if (downloadPeer == null) {
            startBlockchainDownload(peer)
        }
    }

    fun onPeerDisconnected(peer: SpvPeer) = lock.withLock {
        pendingPeers.remove(peer.address)
        peers.remove(peer.address)
        if (downloadPeer?.address?.equals(peer.address, ignoreCase = true) == true) {
            downloadPeer = null
        }
    }

    fun onMessageReceived(message: VeriBlockMessages.Event, sender: SpvPeer) {
        try {
            logger.debug("Message Received messageId: {}, from: {}:{}", message.id, sender.address, sender.port)
            incomingQueue.offer(NetworkMessage(sender, message))
        } catch (e: InterruptedException) {
            logger.error("onMessageReceived interrupted", e)
        } catch (e: ClosedChannelException) {
            logger.error("onMessageReceived interrupted", e)
        }
    }

    fun advertise(transaction: Transaction) {
        val advertise = VeriBlockMessages.Event.newBuilder()
            .setId(nextMessageId())
            .setAcknowledge(false)
            .setAdvertiseTx(
                VeriBlockMessages.AdvertiseTransaction.newBuilder()
                    .addTransactions(
                        TransactionAnnounce.newBuilder()
                            .setType(
                                if (transaction.transactionTypeIdentifier === TransactionTypeIdentifier.PROOF_OF_PROOF) TransactionAnnounce.Type.PROOF_OF_PROOF else TransactionAnnounce.Type.NORMAL
                            )
                            .setTxId(ByteString.copyFrom(transaction.txId.bytes))
                            .build()
                    )
                    .build()
            )
            .build()
        for (peer in peers.values) {
            try {
                peer.sendMessage(advertise)
            } catch (ex: Exception) {
                logger.error(ex.message, ex)
            }
        }
    }

    suspend fun requestMessage(
        event: VeriBlockMessages.Event,
        timeoutInMillis: Long = 5000L
    ): VeriBlockMessages.Event = withTimeout(timeoutInMillis) {
        // Create a flow that emits in execution order
        val executionOrderFlow = flow {
            // Open a select scope for being able to call onAwait concurrently for all peers
            select {
                // Perform the request for all the peers asynchronously
                // TODO: consider a less expensive approach such as asking a random peer. There can be peer behavior score weighting and/or retries.
                for (peer in peers.values) {
                    async {
                        peer.requestMessage(event, timeoutInMillis)
                    }.onAwait {
                        // Emit in the flow on completion, so the first one to complete will get the other jobs cancelled
                        emit(it)
                    }
                }
            }
        }
        // Choose the first one to complete
        executionOrderFlow.first()
    }

    fun getSignatureIndex(address: String): Long? {
        return addressesState[address]?.ledgerValue?.signatureIndex
    }

    fun getAvailablePeers(): Int = peers.size

    fun getBestBlockHeight(): Int = peers.values.maxOfOrNull {
        it.bestBlockHeight
    } ?: 0

    fun getDownloadStatus(): DownloadStatusResponse {
        val status: DownloadStatus
        val currentHeight = blockchain.getChainHead().height
        val bestBlockHeight = downloadPeer?.bestBlockHeight ?: 0
        status = when {
            downloadPeer == null || (currentHeight == 0 && bestBlockHeight == 0) ->
                DownloadStatus.DISCOVERING
            bestBlockHeight > 0 && bestBlockHeight - currentHeight < AMOUNT_OF_BLOCKS_WHEN_WE_CAN_START_WORKING ->
                DownloadStatus.READY
            else ->
                DownloadStatus.DOWNLOADING
        }
        return DownloadStatusResponse(status, currentHeight, bestBlockHeight)
    }

    fun getAddressesState(): Map<String, LedgerContext> {
        return addressesState
    }

    fun getAddressState(address: String): LedgerContext? {
        return addressesState[address]
    }

    fun getConnectedPeers(): Collection<SpvPeer> = Collections.unmodifiableCollection(peers.values)

    fun countConnectedPeers(): Int {
        return peers.size
    }

    fun countPendingPeers(): Int {
        return pendingPeers.size
    }
}

private fun VeriBlockMessages.TransactionInfo.toModel() = TransactionInfo(
    confirmations = confirmations,
    transaction = transaction.toModel(),
    blockNumber = blockNumber,
    timestamp = timestamp,
    endorsedBlockHash = endorsedBlockHash.toHex(),
    bitcoinBlockHash = bitcoinBlockHash.toHex(),
    bitcoinTxId = bitcoinTxId.toHex(),
    bitcoinConfirmations = bitcoinConfirmations,
    blockHash = blockHash.toHex(),
    merklePath = merklePath
)

private fun VeriBlockMessages.Transaction.toModel() = TransactionData(
    type = TransactionType.valueOf(type.name),
    sourceAddress = sourceAddress.toHex(),
    sourceAmount = sourceAmount,
    outputs = outputsList.map { it.toModel() },
    transactionFee = transactionFee,
    data = data.toHex(),
    bitcoinTransaction = bitcoinTransaction.toHex(),
    endorsedBlockHeader = endorsedBlockHeader.toHex(),
    bitcoinBlockHeaderOfProof = "",
    merklePath = merklePath,
    contextBitcoinBlockHeaders = listOf(),
    timestamp = timestamp,
    size = size,
    txId = Sha256Hash.wrap(txId.toHex())
)

private fun VeriBlockMessages.Output.toModel() = OutputData(
    address = address.toHex(),
    amount = amount
)