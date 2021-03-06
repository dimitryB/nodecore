// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package org.veriblock.miners.pop.service

import org.koin.standalone.KoinComponent
import org.koin.standalone.inject
import org.veriblock.alt.plugins.FamilyPluginsContainer
import org.veriblock.alt.plugins.NormalPluginsContainer
import org.veriblock.core.utilities.Configuration
import org.veriblock.sdk.alt.SecurityInheritingChain
import org.veriblock.core.utilities.createLogger

private val logger = createLogger {}

class PluginConfig(
    val id: Long? = null,
    val family: String? = null,
    val name: String? = null
)

class PluginService() : KoinComponent {
    val normalPlugins: NormalPluginsContainer by inject("normal-plugins")
    val familyPlugins: FamilyPluginsContainer by inject("family-plugins")

    private var loadedPlugins: Map<String, SecurityInheritingChain> = emptyMap()

    private val configuredPlugins: Map<String, PluginConfig> = Configuration.extract("securityInheriting") ?: emptyMap()

    fun loadPlugins() {
        logger.info { "Loading plugins..." }
        logger.info { "Implemented normal plugins: ${normalPlugins.keys.joinToString()}" }
        logger.info { "Implemented family plugins: ${familyPlugins.keys.joinToString()}" }

        loadedPlugins = configuredPlugins.asSequence().mapNotNull { (key, config) ->
            val family = config.family
            if (family == null) {
                val plugin = normalPlugins[key]
                    ?: return@mapNotNull null
                logger.info { "Loaded plugin $key from config" }
                key to plugin
            } else {
                val chainId = config.id ?: run {
                    logger.warn { "Chain $key is configured to family $family but it does not have an id! Ignoring..." }
                    return@mapNotNull null
                }
                val chainSupplier = familyPlugins[family]
                    ?: return@mapNotNull null

                logger.info { "Loaded plugin $key ($family family) from config" }
                key to chainSupplier(chainId, key, config.name ?: "")
            }
        }.associate {
            it.first to it.second
        }
    }

    fun getPlugins(): Map<String, SecurityInheritingChain> =
        loadedPlugins.toMap()

    operator fun get(key: String): SecurityInheritingChain? {
        return loadedPlugins[key]
    }
}
