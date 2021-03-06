// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package org.veriblock.lite.wallet

import io.kotlintest.shouldBe
import org.junit.Test
import org.veriblock.lite.core.*
import org.veriblock.sdk.VeriBlockTransaction

class WalletTransactionTest {

    @Test
    fun transactionDataByData() {
        // Given
        val type: Byte = 0x01
        val sourceAddress = randomAddress()
        val sourceAmount= randomCoin()
        val outputs = (1..10).map { randomOutput() }
        val signatureIndex: Long = 7
        val data = ByteArray(8)
        val signature = ByteArray(10)
        val publicKey = ByteArray(8)
        val networkByte: Byte? = Context.networkParameters.transactionPrefix
        val transactionMeta: TransactionMeta = randomTransactionMeta()
        val merklePath = randomVeriBlockMerklePath()

        // When
        val walletTransaction = randomWalletTransaction(type, sourceAddress, sourceAmount,
            outputs, signatureIndex, data, signature, publicKey, networkByte, transactionMeta, merklePath)

        // Then
        walletTransaction.type shouldBe type
        walletTransaction.sourceAddress shouldBe sourceAddress
        walletTransaction.sourceAmount shouldBe sourceAmount
        walletTransaction.outputs shouldBe outputs
        walletTransaction.signatureIndex shouldBe signatureIndex
        walletTransaction.data shouldBe data
        walletTransaction.signature shouldBe signature
        walletTransaction.publicKey shouldBe publicKey
        walletTransaction.networkByte shouldBe networkByte
        walletTransaction.transactionMeta shouldBe transactionMeta
        walletTransaction.merklePath shouldBe merklePath
    }

    @Test
    fun transactionDataByVeriblockTransaction() {
        // Given
        val type: Byte = 0x01
        val sourceAddress = randomAddress()
        val sourceAmount= randomCoin()
        val outputs = (1..10).map { randomOutput() }
        val signatureIndex: Long = 7
        val data = ByteArray(8)
        val signature = ByteArray(10)
        val publicKey = ByteArray(8)
        val networkByte: Byte? = Context.networkParameters.transactionPrefix

        // When
        val veriblockTransaction = VeriBlockTransaction(type, sourceAddress, sourceAmount, outputs,
            signatureIndex, data, signature, publicKey, networkByte)
        val walletTransaction = WalletTransaction.wrap(veriblockTransaction)

        // Then
        walletTransaction.type shouldBe type
        walletTransaction.sourceAddress shouldBe sourceAddress
        walletTransaction.sourceAmount shouldBe sourceAmount
        walletTransaction.outputs shouldBe outputs
        walletTransaction.signatureIndex shouldBe signatureIndex
        walletTransaction.data shouldBe data
        walletTransaction.signature shouldBe signature
        walletTransaction.publicKey shouldBe publicKey
        walletTransaction.networkByte shouldBe networkByte
        walletTransaction.merklePath shouldBe null
    }
}
