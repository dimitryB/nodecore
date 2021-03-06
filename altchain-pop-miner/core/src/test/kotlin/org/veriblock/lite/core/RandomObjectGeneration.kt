// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package org.veriblock.lite.core

import org.apache.commons.lang3.RandomStringUtils
import org.veriblock.core.utilities.AddressUtility
import org.veriblock.core.wallet.AddressKeyGenerator
import org.veriblock.lite.wallet.TransactionMonitor
import org.veriblock.lite.wallet.WalletTransaction
import org.veriblock.sdk.*
import java.security.MessageDigest
import java.util.*

private val random = Random()

fun randomBoolean() = random.nextBoolean()

fun randomInt(bound: Int) = random.nextInt(bound)

fun randomInt(min: Int, max: Int) = random.nextInt(max - min) + min

fun randomLong(min: Long, max: Long) = (random.nextDouble() * (max - min)).toLong() + min

fun randomAlphabeticString(length: Int = 10): String =
    RandomStringUtils.randomAlphabetic(length)

fun randomAlphabeticString(minLengthInclusive: Int = 8, maxLengthInclusive: Int = 12): String =
    RandomStringUtils.randomAlphabetic(minLengthInclusive, maxLengthInclusive)

fun randomByteArray(size: Int): ByteArray {
    return ByteArray(size) {
        randomInt(256).toByte()
    }
}

fun randomCoin(
    amount: Long = randomLong(1, 10_000_000_000)
): Coin {
    return Coin.valueOf(amount)
}

fun randomAddress(): Address {
    val pair = AddressKeyGenerator.generate()
    val address = AddressUtility.addressFromPublicKey(pair.public)
    return Address(address)
}

fun randomWalletTransaction(
    type: Byte = 0x01,
    sourceAddress: Address = randomAddress(),
    sourceAmount: Coin = randomCoin(),
    outputs: List<Output> = (1..10).map { randomOutput() },
    signatureIndex: Long = 7,
    data: ByteArray = ByteArray(8),
    signature: ByteArray = ByteArray(10),
    publicKey: ByteArray = ByteArray(8),
    networkByte: Byte? = Context.networkParameters.transactionPrefix,
    transactionMeta: TransactionMeta = randomTransactionMeta(),
    merklePath: VeriBlockMerklePath = randomVeriBlockMerklePath()
): WalletTransaction {
    return WalletTransaction(
        type,
        sourceAddress,
        sourceAmount,
        outputs,
        signatureIndex,
        data,
        signature,
        publicKey,
        networkByte,
        transactionMeta
    ).apply {
        this.merklePath = merklePath
    }
}

fun randomOutput(
    address: Address = randomAddress(),
    amount: Coin = randomCoin()
): Output {
    return Output(address, amount)
}

fun randomTransactionMeta(
    transactionId: Sha256Hash = randomSha256Hash(),
    metaState: TransactionMeta.MetaState = TransactionMeta.MetaState.UNKNOWN,
    depthCount: Int = 0
): TransactionMeta {
    return TransactionMeta(transactionId).apply {
        appearsInBestChainBlock = randomVBlakeHash()
        setState(metaState)
        depth = depthCount
    }
}

fun randomVBlakeHash(): VBlakeHash {
    return VBlakeHash.wrap(randomByteArray(VBlakeHash.VERIBLOCK_LENGTH))
}

private var messageDigest = MessageDigest.getInstance("SHA-256")
fun randomSha256Hash(): Sha256Hash {
    val randomBytes = randomAlphabeticString().toByteArray()
    return Sha256Hash.wrap(messageDigest.digest(randomBytes))
}

fun randomTransactionMonitor(
    address: Address = randomAddress(),
    walletTransactions: List<WalletTransaction> = (0..randomInt(20)).map { randomWalletTransaction() }
) = TransactionMonitor(address, walletTransactions)

fun randomVeriBlockMerklePath(
    treeIndex: Int = randomInt(1, 65535),
    index: Int = randomInt(1, 65535),
    subject: Sha256Hash = randomSha256Hash(),
    layers: List<Sha256Hash> = (1..5).map { randomSha256Hash() }
): VeriBlockMerklePath {
    return VeriBlockMerklePath(
        "$treeIndex:$index:$subject:${layers.joinToString(separator = ":") { it.toString() }}"
    )
}

fun randomMerklePath(
    index: Int = randomInt(1, 65535),
    subject: Sha256Hash = randomSha256Hash(),
    layers: List<Sha256Hash> = (1..5).map { randomSha256Hash() }
): MerklePath {
    return MerklePath(
        "$index:$subject:${layers.joinToString(separator = ":") { it.toString() }}"
    )
}

fun randomVeriBlockTransaction(
    type: Byte = 0x01,
    sourceAddress: Address = randomAddress(),
    sourceAmount: Coin = randomCoin(),
    outputs: List<Output> =  (1..10).map { randomOutput() },
    signatureIndex: Long = 7,
    data: ByteArray = ByteArray(8),
    signature: ByteArray = ByteArray(10),
    publicKey: ByteArray = ByteArray(8),
    networkByte: Byte? = Context.networkParameters.transactionPrefix
): VeriBlockTransaction {
    return VeriBlockTransaction(
        type,
        sourceAddress,
        sourceAmount,
        outputs,
        signatureIndex,
        data,
        signature,
        publicKey,
        networkByte
    )
}

fun randomFullBlock(
    height: Int = randomInt(0, Int.MAX_VALUE),
    version: Short = randomInt(0, Short.MAX_VALUE.toInt()).toShort(),
    previousBlock: VBlakeHash = randomVBlakeHash(),
    previousKeystone: VBlakeHash = randomVBlakeHash(),
    secondPreviousKeystone: VBlakeHash = randomVBlakeHash(),
    merkleRoot: Sha256Hash = randomSha256Hash(),
    timestamp: Int = randomInt(0, Int.MAX_VALUE),
    difficulty: Int = randomInt(0, Int.MAX_VALUE),
    nonce: Int = randomInt(0, Int.MAX_VALUE),
    normalTransactions: List<VeriBlockTransaction> = (1..10).map { randomVeriBlockTransaction() },
    poPTransactions: List<VeriBlockPoPTransaction> = (1..10).map { randomVeriBlockPoPTransaction() },
    metaPackage: BlockMetaPackage = randomBlockMetaPackage()
): FullBlock {
    return FullBlock(
        height,
        version,
        previousBlock,
        previousKeystone,
        secondPreviousKeystone,
        merkleRoot,
        timestamp,
        difficulty,
        nonce,
        normalTransactions,
        poPTransactions,
        metaPackage
    )
}

fun randomBlockMetaPackage(
    hash: Sha256Hash = randomSha256Hash()
): BlockMetaPackage {
    return BlockMetaPackage(hash)
}

fun randomVeriBlockPoPTransaction(
    address: Address = randomAddress(),
    publishedBlock: VeriBlockBlock = randomVeriBlockBlock(),
    bitcoinTransaction: BitcoinTransaction = randomBitcoinTransaction(),
    merklePath: MerklePath = randomMerklePath(),
    blockOfProof: BitcoinBlock = randomBitcoinBlock(),
    blockOfProofContext: List<BitcoinBlock> = (1..10).map { randomBitcoinBlock() },
    signature: ByteArray = ByteArray(10),
    publicKey: ByteArray = ByteArray(8),
    networkByte: Byte? = Context.networkParameters.transactionPrefix
): VeriBlockPoPTransaction {
    return VeriBlockPoPTransaction(
        address,
        publishedBlock,
        bitcoinTransaction,
        merklePath,
        blockOfProof,
        blockOfProofContext,
        signature,
        publicKey,
        networkByte
    )
}

fun randomVeriBlockBlock(
    height: Int = randomInt(1, 65535),
    version: Short = randomInt(1, Short.MAX_VALUE.toInt()).toShort(),
    previousBlock: VBlakeHash = randomVBlakeHash(),
    previousKeystone: VBlakeHash = randomVBlakeHash(),
    secondPreviousKeystone: VBlakeHash = randomVBlakeHash(),
    merkleRoot: Sha256Hash = randomSha256Hash(),
    timestamp: Int = randomInt(1, 65535),
    difficulty: Int = randomInt(1, 65535),
    nonce: Int = randomInt(1, 35535)
): VeriBlockBlock {
    return VeriBlockBlock(
        height,
        version,
        previousBlock,
        previousKeystone,
        secondPreviousKeystone,
        merkleRoot,
        timestamp,
        difficulty,
        nonce
    )
}

fun randomBitcoinTransaction(
    raw: ByteArray = randomByteArray(243)
): BitcoinTransaction {
    return BitcoinTransaction(raw)
}

fun randomBitcoinBlock(
    version: Int = randomInt(1, 65535),
    previousBlock: Sha256Hash = randomSha256Hash(),
    merkleRoot: Sha256Hash = randomSha256Hash(),
    timestamp: Int = randomInt(1, 65535),
    bits: Int = randomInt(1, 65535),
    nonce: Int = randomInt(1, 65535)
): BitcoinBlock {
    return BitcoinBlock(
        version,
        previousBlock,
        merkleRoot,
        timestamp,
        bits,
        nonce
    )
}
