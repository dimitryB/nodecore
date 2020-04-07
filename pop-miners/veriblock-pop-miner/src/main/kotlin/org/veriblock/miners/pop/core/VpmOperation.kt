// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2020 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package org.veriblock.miners.pop.core

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.bitcoinj.core.TransactionConfidence
import org.veriblock.miners.pop.EventBus
import org.veriblock.miners.pop.model.PopMiningInstruction
import java.time.LocalDateTime

class VpmOperation(
    id: String,
    changeHistory: List<StateChangeEvent> = emptyList(),
    endorsedBlockHeight: Int? = null,
    createdAt: LocalDateTime = LocalDateTime.now(),
    reconstituting: Boolean = false
) : MiningOperation<PopMiningInstruction, VpmSpTransaction, VpmSpBlock, VpmMerklePath, VpmContext>(
    id, endorsedBlockHeight, changeHistory, createdAt, reconstituting
) {
    val transactionConfidenceEventChannel = BroadcastChannel<TransactionConfidence.ConfidenceType>(Channel.CONFLATED)

    private val transactionListener = { confidence: TransactionConfidence, reason: TransactionConfidence.Listener.ChangeReason ->
        if (reason == TransactionConfidence.Listener.ChangeReason.TYPE) {
            transactionConfidenceEventChannel.offer(confidence.confidenceType)
        }
    }

    override fun onStateChanged() {
        EventBus.popMiningOperationStateChangedEvent.trigger(this)
    }

    override fun onTransactionSet(transaction: VpmSpTransaction) {
        transaction.transaction.confidence.addEventListener(transactionListener)
        GlobalScope.launch {
            for (confidenceType in transactionConfidenceEventChannel.openSubscription()) {
                if (confidenceType == TransactionConfidence.ConfidenceType.PENDING) {
                    EventBus.transactionSufferedReorgEvent.trigger(this@VpmOperation)
                    // Reset the state to the endorsement transaction pending for confirmation
                    setState(OperationState.ENDORSEMENT_TRANSACTION)
                }
            }
        }
    }

    override fun onCompleted() {
        EventBus.popMiningOperationCompletedEvent.trigger(id)
        endorsementTransaction?.transaction?.confidence?.removeEventListener(transactionListener)
    }

    override fun onFailed() {
        endorsementTransaction?.transaction?.confidence?.removeEventListener(transactionListener)
    }
}
