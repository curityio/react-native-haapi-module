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

import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import se.curity.identityserver.haapi.android.sdk.HaapiAccessor
import se.curity.identityserver.haapi.android.sdk.HaapiAccessorFactory

class HaapiAccessorRepository(
    conf: Map<String, Any>,
    reactContext: ReactApplicationContext
) {
    private val _factory: HaapiAccessorFactory
    private var backingAccessor: HaapiAccessor? = null

    init {
        val haapiConfiguration = HaapiConfigurationUtil.createConfiguration(conf, reactContext)
        val factory = HaapiAccessorFactory(haapiConfiguration)
        HaapiConfigurationUtil.addFallbackConfiguration(factory, conf, reactContext)
        _factory = factory
        backingAccessor = null
    }

    val accessor: HaapiAccessor
        get() = getHaapiAccessor()

    private fun getHaapiAccessor(): HaapiAccessor {
        if (backingAccessor != null) {
            return backingAccessor!!
        }
        var created: HaapiAccessor?
        runBlocking {
            launch {
                created = try {
                    _factory.create()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to create accessor: ${e.message}")
                    throw e
                }
                Log.i(TAG, "Created accessor: $created")
                backingAccessor = created
            }
        }
        return backingAccessor!!
    }

    fun close() {
        Log.i(TAG, "Closing the HAAPI manager")
        backingAccessor?.haapiManager?.close()
        backingAccessor = null
    }

}