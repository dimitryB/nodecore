// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.
package org.veriblock.sdk

import org.junit.Assert
import org.junit.Test
import org.veriblock.sdk.models.Coin.Companion.parse
import org.veriblock.sdk.models.asCoin
import org.veriblock.sdk.services.SerializeDeserializeService
import org.veriblock.sdk.util.StreamUtils
import org.veriblock.core.utilities.Utility
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer

class CoinTests {
    @Test
    fun parse() {
        val input = 123456789L.asCoin()
        val serialized = SerializeDeserializeService.serialize(input)
        val deserialized = parse(ByteBuffer.wrap(serialized))
        Assert.assertEquals(input, deserialized)
    }

    @Test
    @Throws(IOException::class)
    fun parseWhenInvalid() {
        val array = Utility.fillBytes(0xFF.toByte(), 9)
        try {
            ByteArrayOutputStream().use { stream ->
                StreamUtils.writeSingleByteLengthValueToStream(stream, array)
                val buffer = ByteBuffer.wrap(stream.toByteArray())
                parse(buffer)
                Assert.fail("Expected IllegalArgumentException")
            }
        } catch (e: IllegalArgumentException) {
            Assert.assertTrue(e.message!!.startsWith("Unexpected length"))
        }
    }
}