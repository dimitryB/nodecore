// VeriBlock NodeCore
// Copyright 2017-2020 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package org.veriblock.miners.pop.api.controller

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.put
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import org.veriblock.core.utilities.Configuration
import org.veriblock.miners.pop.AutoMineConfig
import org.veriblock.miners.pop.api.model.SetConfigRequest
import org.veriblock.miners.pop.automine.AutoMineEngine
import org.veriblock.miners.pop.service.BitcoinService

//@Group("Configuration")
@Location("/api/config")
class config

//@Group("Configuration")
@Location("/api/config/automine")
class automine

//@Group("Configuration")
@Location("/api/config/btc-fee")
class btcfee

class ConfigurationController(
    private val configuration: Configuration,
    private val autoMineEngine: AutoMineEngine,
    private val bitcoinService: BitcoinService
) : ApiController {

    override fun Route.registerApi() {
        get<config>(
        //    "config"
        //        .description("Get all configuration values")
        ) {
            val configValues = configuration.list()
            call.respond(configValues)
        }
        put<config>(//, SetConfigRequest>(
        //    "config"
        //        .description("Set one configuration value")
        ) {// _, request ->
            val request = call.receive<SetConfigRequest>()
            if (request.key == null) {
                throw BadRequestException("'key' must be set")
            }
            if (request.value == null) {
                throw BadRequestException("'value' must be set")
            }
            val configValues = configuration.list()
            if (!configValues.containsKey(request.key)) {
                throw BadRequestException("'${request.key}' is not part of the configurable properties")
            }
            configuration.setProperty(request.key, request.value)
            configuration.saveOverriddenProperties()
            call.respond(HttpStatusCode.OK)
        }
        get<automine>(
        //    "automine"
        //        .description("Get the automine config")
        ) {
            val configValues = autoMineEngine.config
            call.respond(configValues)
        }
        put<automine>(//, AutoMineConfigRequest>(
        //    "automine"
        //        .description("Set the automine config")
        ) {// _, request ->
            val request = call.receive<AutoMineConfigRequest>()
            autoMineEngine.config = AutoMineConfig(
                request.round1 ?: autoMineEngine.config.round1,
                request.round2 ?: autoMineEngine.config.round2,
                request.round3 ?: autoMineEngine.config.round3,
                request.round4 ?: autoMineEngine.config.round4
            )
            call.respond(HttpStatusCode.OK)
        }
        get<btcfee>(
        //    "btcfee"
        //        .description("Get the btcfee config")
        ) {
            val configValues = BtcFeeConfigRequest(
                bitcoinService.maxFee,
                bitcoinService.feePerKb
            )
            call.respond(configValues)
        }
        put<btcfee>(//, BtcFeeConfigRequest>(
        //    "btcfee"
        //        .description("Set the btcfee config")
        ) {// _, request ->
            val request = call.receive<BtcFeeConfigRequest>()
            if (request.maxFee != null) {
                bitcoinService.maxFee = request.maxFee
            }
            if (request.feePerKB != null) {
                bitcoinService.feePerKb = request.feePerKB
            }
            call.respond(HttpStatusCode.OK)
        }
    }
}

data class AutoMineConfigRequest(
    val round1: Boolean? = null,
    val round2: Boolean? = null,
    val round3: Boolean? = null,
    val round4: Boolean? = null
)

class BtcFeeConfigRequest(
    val maxFee: Long? = null,
    val feePerKB: Long? = null
)