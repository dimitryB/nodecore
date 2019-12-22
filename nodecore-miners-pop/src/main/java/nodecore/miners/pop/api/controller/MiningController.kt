// VeriBlock NodeCore
// Copyright 2017-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package nodecore.miners.pop.api.controller

import io.ktor.application.call
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import nodecore.miners.pop.Configuration
import nodecore.miners.pop.PoPMiner
import nodecore.miners.pop.api.model.*
import nodecore.miners.pop.contracts.PoPEndorsementInfo
import nodecore.miners.pop.services.NodeCoreService
import org.veriblock.core.utilities.BlockUtility
import java.util.Comparator

class MiningController(
    private val miner: PoPMiner,
    private val configuration: Configuration,
    private val popService: NodeCoreService
) : ApiController {

    override fun Route.registerApi() {
        get("/operations") {
            val operationSummaries = miner.listOperations()
                ?: throw NotFoundException("No operations found")

            val responseModel = operationSummaries.map { it.toResponse() }
            call.respond(responseModel)
        }
        get("/operations/{id}") {
            val id = call.parameters["id"]!!

            val operationState = miner.getOperationState(id)
                ?: throw NotFoundException("Operation $id not found")

            val responseModel = operationState.toResponse()
            call.respond(responseModel)
        }
        get("/operationstatus{id}") {
            val id = call.parameters["id"]!!

            val operationState = miner.getOperationState(id)
                    ?: throw NotFoundException("Operation $id not found")

            var blockNumber = 0
            if (operationState.miningInstruction.endorsedBlockHeader != null &&
                    operationState.miningInstruction.endorsedBlockHeader.size > 0) {
                blockNumber = BlockUtility.extractBlockHeightFromBlockHeader(operationState.miningInstruction.endorsedBlockHeader)
            }

            val operationStatus = OperationStatusResponse (
                btcTransactionId = operationState.submittedTransactionId,
                currentAction = operationState.currentAction?.toString(),
                endorsedBlockNumber = blockNumber,
                status = operationState.status?.toString()
            )

            call.respond(operationStatus)
        }
        post("/mine") {
            val payload: MineRequestPayload = call.receive()

            val result = miner.mine(payload.block)

            val responseModel = result.toResponse()
            call.respond(responseModel)
        }
        get("/mine{blockNo,fee}") {
            val block = call.parameters["blockNo"]!!
            val fee = call.parameters["fee"]

            fee?.let {
                configuration.setTransactionFeePerKB(fee)
            }

            val result = miner.mine(block.toInt())

            val responseModel = result.toResponse()
            call.respond(responseModel)
        }
        get("/miner") {
            val responseModel = MinerInfoResponse(
                bitcoinBalance = miner.bitcoinBalance.longValue(),
                bitcoinAddress = miner.bitcoinReceiveAddress,
                minerAddress = miner.minerAddress,
                isReady = miner.isReady
            )
            call.respond(responseModel)
        }

        get("/recentrewards") {

            val endorsements = popService.getPoPEndorsementInfo()
            endorsements.sortBy{ it.endorsedBlockNumber }

            call.respond(endorsements)
        }

        get("/miner/isready") {
            call.respond(miner.isReady)
        }
        get("/waitingoperations") {

            val operations = miner.listOperations()
            val summaries = mutableListOf<OperationFeeResponse>()

            for (summary in operations!!) {
                if (summary.getState() == "RUNNING" &&
                        summary.getAction().startsWith("Waiting")) {
                    val bean = OperationFeeResponse(
                        endorsedBlockNumber = summary.endorsedBlockNumber.toString(),
                        state = summary.state,
                        operationId= summary.operationId,
                        action = summary.action,
                        fee = summary.fee)
                    summaries.add(bean)
                }
            }

            summaries.sortBy{it.fee}
            call.respond(summaries)
        }
    }
}
