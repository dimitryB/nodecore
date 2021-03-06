// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package nodecore.miners.pop.shell.commands

import nodecore.miners.pop.PoPMiner
import nodecore.miners.pop.common.Utility
import nodecore.miners.pop.shell.toShellResult
import org.veriblock.shell.CommandParameter
import org.veriblock.shell.CommandParameterType
import org.veriblock.shell.Shell
import org.veriblock.shell.command
import org.veriblock.shell.core.failure
import org.veriblock.shell.core.success
import java.math.BigDecimal

fun Shell.bitcoinWalletCommands(
    miner: PoPMiner
) {
    command(
        name = "Show Bitcoin Balance",
        form = "showbitcoinbalance",
        description = "Displays the current balance for the Bitcoin wallet"
    ) {
        val bitcoinBalance = miner.bitcoinBalance
        val formattedBalance = Utility.formatBTCFriendlyString(bitcoinBalance)
        printInfo("Bitcoin Balance: $formattedBalance")
        success {
            addMessage("V200", "Success", formattedBalance)
        }
    }

    command(
        name = "Show Bitcoin Address",
        form = "showbitcoinaddress",
        description = "Displays the current address for receiving Bitcoin"
    ) {
        val address = miner.bitcoinReceiveAddress
        printInfo("Bitcoin Receive Address: $address")
        success {
            addMessage("V200", "Success", address)
        }
    }

    command(
        name = "Import Bitcoin Wallet",
        form = "importwallet",
        description = "Imports a Bitcoin wallet using comma-separated list of seed words and, optionally, a wallet creation date",
        parameters = listOf(
            CommandParameter("seedWords", CommandParameterType.STRING, true),
            CommandParameter("creationTime", CommandParameterType.LONG, false)
        )
    ) {
        val seedWords: String = getParameter("seedWords")
        val words = seedWords.split(",")
        if (words.size != 12) {
            return@command failure {
                addMessage("V400", "Invalid input", "The seed words parameter should contain 12 words in a comma-separated format (no spaces)", true)
            }
        }

        val creationTime: Long? = getParameter("creationTime")
        if (!miner.importWallet(words, creationTime)) {
            return@command failure {
                addMessage("V500", "Unable to Import", "Unable to import the wallet from the seed supplied. Check the logs for more detail.", true)
            }
        }

        success()
    }

    command(
        name = "Withdraw Bitcoin to Address",
        form = "withdrawbitcointoaddress",
        description = "Sends a Bitcoin amount to a given address",
        parameters = listOf(
            CommandParameter("address", CommandParameterType.STRING, true),
            CommandParameter("amount", CommandParameterType.AMOUNT, true)
        )
    ) {
        val address: String = getParameter("address")
        val amount: BigDecimal = getParameter("amount")
        miner.sendBitcoinToAddress(address, amount).toShellResult()
    }

    command(
        name = "Export Bitcoin Private Keys",
        form = "exportbitcoinkeys",
        description = "Exports the private keys in the Bitcoin wallet to a specified file in WIF format"
    ) {
        miner.exportBitcoinPrivateKeys().toShellResult()
    }

    command(
        name = "Reset Bitcoin Wallet",
        form = "resetwallet",
        description = "Resets the Bitcoin wallet, marking it for resync"
    ) {
        miner.resetBitcoinWallet().toShellResult()
    }
}
