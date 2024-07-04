/*
 *  Copyright 2024 Curity AB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.curity.haapi.react

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableNativeMap
import com.google.gson.Gson

object JsonUtil {
    private val gson = Gson()

    fun jsonToNativeMap(json: String): WritableNativeMap {
        val map = jsonToMap(json)
        return Arguments.makeNativeMap(map)
    }

    fun mapToNativeMap(map: Map<String, Any>): WritableNativeMap {
        return Arguments.makeNativeMap(map)
    }

    fun jsonToMap(json: String): Map<String, Any> {
        val map = HashMap<String, Any>()
        return gson.fromJson(json, map::class.java)
    }

    fun toJsonString(any: Any): String = gson.toJson(any)
}