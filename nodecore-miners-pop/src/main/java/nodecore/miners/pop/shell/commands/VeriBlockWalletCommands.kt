// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package nodecore.miners.pop.shell.commands

import com.google.gson.Gson
import io.grpc.StatusRuntimeException
import nodecore.miners.pop.contracts.PoPEndorsementInfo
import nodecore.miners.pop.services.NodeCoreService
import nodecore.miners.pop.shell.toShellResult
import org.veriblock.shell.CommandParameter
import org.veriblock.shell.CommandParameterType
import org.veriblock.shell.Shell
import org.veriblock.shell.command
import org.veriblock.shell.core.failure
import org.veriblock.shell.core.success

fun Shell.veriBlockWalletCommands(
    nodeCoreService: NodeCoreService,
    prettyPrintGson: Gson
) {
    command(
        name = "Lock VeriBlock Wallet",
        form = "lockwallet",
        description = "Locks an encrypted VeriBlock wallet to disable creation of PoP transactions"
    ) {
        try {
            nodeCoreService.lockWallet().toShellResult()
        } catch (e: StatusRuntimeException) {
            failure {
                addMessage("V500", "NodeCore Communication Error", e.status.code.toString(), true)
            }
        } catch (e: Exception) {
            failure {
                addMessage("V500", "Command Error", e.message!!, true)
            }
        }
    }

    command(
        name = "Unlock VeriBlock Wallet",
        form = "unlockwallet",
        description = "Unlocks an encrypted VeriBlock wallet to allow creation of PoP transactions",
        parameters = listOf(
            CommandParameter("passphrase", CommandParameterType.STRING, true)
        )
    ) {
        try {
            val passphrase: String = getParameter("passphrase")
            nodeCoreService.unlockWallet(passphrase).toShellResult()
        } catch (e: StatusRuntimeException) {
            failure {
                addMessage("V500", "NodeCore Communication Error", e.status.code.toString(), true)
            }
        } catch (e: Exception) {
            failure {
                addMessage("V500", "Command Error", e.message!!, true)
            }
        }
    }

    command(
        name = "Get PoP Endoresement Info",
        form = "getpopendorsementinfo",
        description = "Returns information regarding PoP endorsements for a given address"
    ) {
        try {
            val endorsements = nodeCoreService.poPEndorsementInfo
            printInfo("${prettyPrintGson.toJson(endorsements)}\n\n")
            success()
        } catch (e: StatusRuntimeException) {
            failure {
                addMessage("V500", "NodeCore Communication Error", e.status.code.toString())
            }
        } catch (e: Exception) {
            failure {
                addMessage("V500", "Command Error", e.message!!, true)
            }
        }
    }

    command(
        name = "View Recent Rewards",
        form = "viewrecentrewards",
        description = "Lists recent and upcoming rewards"
    ) {
        try {
            val endorsements: List<PoPEndorsementInfo> = nodeCoreService.poPEndorsementInfo.sortedBy {
                it.endorsedBlockNumber
            }
            for (e in endorsements) {
                printInfo("{endorsed_block: ${e.endorsedBlockNumber}, ${if (e.finalized) "reward" else "projected_reward"}:" +
                    " ${e.reward}, paid_in_block: ${e.endorsedBlockNumber + 500}}")
            }
            success()
        } catch (e: StatusRuntimeException) {
            failure {
                addMessage("V500", "NodeCore Communication Error", e.status.code.toString())
            }
        } catch (e: Exception) {
            failure {
                addMessage("V500", "Command Error", e.message!!, true)
            }
        }
    }
}
