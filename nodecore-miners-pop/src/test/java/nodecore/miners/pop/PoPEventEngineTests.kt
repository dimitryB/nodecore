// VeriBlock PoP Miner
// Copyright 2017-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package nodecore.miners.pop

import io.kotlintest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import nodecore.miners.pop.contracts.VeriBlockHeader
import nodecore.miners.pop.events.NewVeriBlockFoundEvent
import nodecore.miners.pop.rules.Rule
import nodecore.miners.pop.rules.RuleContext
import org.junit.Test

class PoPEventEngineTests {
    @Test
    fun onNewVeriBlockFound() {
        // Given
        val latest: VeriBlockHeader = mockk()
        val previous: VeriBlockHeader = mockk()
        val event: NewVeriBlockFoundEvent = mockk {
            every { block } returns latest
            every { previousHead } returns previous
        }
        val rule: Rule = mockk()
        val rules: Set<Rule> = setOf(rule)
        val ruleContextSlot = slot<RuleContext>()
        val engine = PoPEventEngine(mockk(), rules)

        // When
        engine.onNewVeriBlockFound(event)

        // Then
        verify(exactly = 1) {
            rule.evaluate(capture(ruleContextSlot))
        }
        val captured: RuleContext = ruleContextSlot.captured
        captured.latestBlock shouldBeSameInstanceAs latest
        captured.previousHead shouldBeSameInstanceAs previous
    }
}
