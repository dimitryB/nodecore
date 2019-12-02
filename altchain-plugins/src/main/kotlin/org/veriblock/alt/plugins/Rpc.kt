// VeriBlock Blockchain Project
// Copyright 2017-2018 VeriBlock, Inc
// Copyright 2018-2019 Xenios SEZC
// All rights reserved.
// https://www.veriblock.org
// Distributed under the MIT software license, see the accompanying
// file LICENSE or http://www.opensource.org/licenses/mit-license.php.

package org.veriblock.alt.plugins

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

data class JsonRpcRequestBody(
    val method: String,
    val params: Any? = emptyList<Any>(),
    val jsonRpc: String = "1.0"
)

data class RpcResponse(
    val result: JsonElement?,
    val error: RpcError?
)

data class RpcError(
    val code: Int,
    val message: String
)

class RpcException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

private val gson = Gson()

inline fun <reified T : Any> RpcResponse.handle(): T = try {
    val type: Type = object : TypeToken<T>() {}.type
    when {
        result != null ->
            result.fromJson<T>(type)
        error != null ->
            throw RpcException(error.message)
        else ->
            throw IllegalStateException()
    }
} catch (e: Exception) {
    throw RpcException("Failed to perform request to the API: ${e.message}", e)
}

fun JsonRpcRequestBody.toJson(): String = gson.toJson(this)
