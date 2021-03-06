// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package org.veriblock.alt.plugins

import com.github.kittinunf.fuel.httpPost
import org.veriblock.core.utilities.Configuration
import org.veriblock.core.utilities.createLogger
import org.veriblock.sdk.AltPublication
import org.veriblock.sdk.PublicationData
import org.veriblock.sdk.VeriBlockPublication
import org.veriblock.sdk.alt.PluginSpec
import org.veriblock.sdk.alt.PublicationDataWithContext
import org.veriblock.sdk.alt.SecurityInheritingChain
import org.veriblock.sdk.asHexBytes
import org.veriblock.sdk.toHex
import kotlin.random.Random

private val logger = createLogger {}

class TestConfig(
    val host: String = "http://localhost:10600/api",
    val autoMinePeriod: Int? = null
)

private val config = Configuration.extract("securityInheriting.test") ?: TestConfig()

private data class VbkInfo(
    val lastBlock: VbkBlockData
)

private class BlockHeaderContainer(
    val header: BlockHeader
)

private class BlockHeader(
    val hash: String,
    val header: String
)

private class VbkBlockData(
    val hash: String,
    val number: Int
)

private class BtcBlockData(
    val hash: String,
    val height: Int,
    val header: String
)

private fun getInfo(): VbkInfo = config.host.httpPost()
    .body(JsonRpcRequestBody("getinfo", Any()).toJson())
    .rpcResponse()

private fun getLastBitcoinBlockHash() = config.host.httpPost()
    .body(JsonRpcRequestBody("getlastbitcoinblock", Any()).toJson())
    .rpcResponse<BtcBlockData>().hash

private fun getLastBlockHash() = config.host.httpPost()
    .body(JsonRpcRequestBody("getlastblock", Any()).toJson())
    .rpcResponse<BlockHeaderContainer>().header.hash

@PluginSpec(name = "Test", key = "test")
class TestChain : SecurityInheritingChain {

    val operations = HashMap<String, String>()

    override fun getChainIdentifier(): Long {
        return -1L
    }

    override fun shouldAutoMine(): Boolean {
        return config.autoMinePeriod != null
    }

    override fun shouldAutoMine(blockHeight: Int): Boolean {
        return config.autoMinePeriod != null && blockHeight % config.autoMinePeriod == 0
    }

    override fun getBestBlockHeight(): Int {
        return (System.currentTimeMillis() / 10000).toInt() // "New block" every 10 seconds
    }

    override fun getPublicationData(blockHeight: Int?): PublicationDataWithContext {
        logger.debug { "Retrieving last known blocks from NodeCore at ${config.host}..." }
        val lastVbkHash = getLastBlockHash().asHexBytes()
        val lastBtcHash = getLastBitcoinBlockHash().asHexBytes()

        val header = Random.nextBytes(20)
        val context = Random.nextBytes(100)
        operations[header.toHex()] = context.toHex()
        val publicationData = PublicationData(
            getChainIdentifier(),
            header,
            ByteArray(0),
            context
        )
        return PublicationDataWithContext(publicationData, listOf(lastVbkHash), listOf(lastBtcHash))
    }

    override fun submit(proofOfProof: AltPublication, veriBlockPublications: List<VeriBlockPublication>): String {
        val publicationData = proofOfProof.transaction.publicationData
        val publicationDataHeader = publicationData.header.toHex()
        val publicationDataContextInfo = publicationData.contextInfo.toHex()
        val expectedContextInfo = operations[publicationDataHeader]
            ?: error("Couldn't find operation with initial header $publicationDataHeader")
        if (publicationDataContextInfo != expectedContextInfo) {
            error("Expected publication data context differs from the one PoP supplied back")
        }
        return "Test successful!"
    }
}
