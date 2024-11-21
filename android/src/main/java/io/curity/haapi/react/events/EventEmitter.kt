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

package io.curity.haapi.react.events

import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule
import io.curity.haapi.react.JsonUtil.jsonToNativeMap
import io.curity.haapi.react.TAG

class EventEmitter(private val _reactContext: ReactApplicationContext) {

    fun sendEvent(type: EventType, json: String = "{}") {
        Log.d(TAG, "Firing event $type")

        val reactMap = jsonToNativeMap(json)
        _reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java).emit(type.toString(),
            reactMap)
    }
}