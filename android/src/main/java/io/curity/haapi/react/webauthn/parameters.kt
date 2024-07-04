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

package io.curity.haapi.react.webauthn

import android.os.Bundle
import io.curity.haapi.react.HaapiException
import io.curity.haapi.react.JsonUtil


data class RegistrationResultParameters(private val credential: Map<String, Any>) {
    fun asMap(): Map<String, Any> {
        // In the response from OS, the response member contains fields not supported by the server.
        val response = credential["response"] as? Map<*, *>
            ?: throw HaapiException("Missing response in webauthn registration response")

        val clientDataJson = response["clientDataJSON"]
            ?: throw HaapiException("Missing clientDataJSON in webauthn registration response")

        val attestationObject = response["attestationObject"]
            ?: throw HaapiException("Missing clientDataJSON in webauthn registration response")

        val newResponse = mapOf("clientDataJSON" to clientDataJson, "attestationObject" to attestationObject)

        val mutableCredential = credential.toMutableMap()
        mutableCredential["response"] = newResponse

        return mapOf("credential" to mutableCredential)
    }

    companion object {
        fun from(bundle: Bundle): RegistrationResultParameters {
            val rawResponseJson = bundle.getString("androidx.credentials.BUNDLE_KEY_REGISTRATION_RESPONSE_JSON")
                ?: throw HaapiException("No platform device request in passkey haapi response")

            return RegistrationResultParameters(JsonUtil.jsonToMap(rawResponseJson))
        }
    }
}

data class AuthenticationResultParameters(private val credential: Map<String, Any>) {

    companion object {
        fun from(bundle: Bundle): AuthenticationResultParameters {
            val jsonString = bundle.getString("androidx.credentials.BUNDLE_KEY_AUTHENTICATION_RESPONSE_JSON")
                ?: throw HaapiException("Not a successful result")
            return AuthenticationResultParameters(JsonUtil.jsonToMap(jsonString))
        }
    }

    fun asMap(): Map<String, Any> = mapOf("credential" to credential)
}