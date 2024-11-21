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

import android.util.Log
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.domerrors.InvalidStateError
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import io.curity.haapi.react.HaapiException
import io.curity.haapi.react.JsonUtil
import io.curity.haapi.react.TAG
import io.curity.haapi.react.events.EventEmitter
import io.curity.haapi.react.events.EventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import se.curity.identityserver.haapi.android.sdk.internal.JsonObjectable
import se.curity.identityserver.haapi.android.sdk.models.WebAuthnAuthenticationClientOperationStep
import se.curity.identityserver.haapi.android.sdk.models.WebAuthnRegistrationClientOperationStep

class WebAuthnHandler(private val _reactContext: ReactApplicationContext) {
    private val _eventEmitter = EventEmitter(_reactContext)
    private val _passkeyScope = CoroutineScope(Dispatchers.IO)

    fun authenticationFlow(
        clientOperation: WebAuthnAuthenticationClientOperationStep,
        promise: Promise,
        onSuccess: (parameters: Map<String, Any>) -> Unit
    ) {
        // Give the app the chance to show a webauthn step
        _eventEmitter.sendEvent(EventType.WebAuthnAuthenticationStep, clientOperation.toJsonString())

        // Start passkey authentication
        val credentialManager = CredentialManager.create(_reactContext.applicationContext)
        val requestOptions = WebAuthnAuthenticationRequest.from(clientOperation.actionModel.publicKey)
        val getPublicKeyCredentialOption = GetPublicKeyCredentialOption(JsonUtil.toJsonString(requestOptions))
        val credentialRequest = GetCredentialRequest(listOf(getPublicKeyCredentialOption))

        _passkeyScope.launch {
            try {
                Log.d(TAG, "Querying for passkey credential")
                val result = credentialManager.getCredential(_reactContext.applicationContext, credentialRequest)
                val parameters = AuthenticationResultParameters.from(result.credential.data).asMap()
                Log.d(TAG, "Successful passkey authentication, continuing flow with parameters $parameters")
                onSuccess(parameters)
            } catch (e: GetCredentialException) {
                handleAuthenticationFailure(e, clientOperation, promise)
            }
        }
    }

    fun registrationFlow(
        clientOperation: WebAuthnRegistrationClientOperationStep,
        promise: Promise,
        onSuccess: (parameters: Map<String, Any>) -> Unit
    ) {
        val credentialManager = CredentialManager.create(_reactContext.applicationContext)
        val request = CreatePublicKeyCredentialRequest(createRegisterJson(clientOperation))

        _passkeyScope.launch {
            try {
                Log.d(TAG, "Asking user to register a passkey credential")

                val result = credentialManager.createCredential(_reactContext.applicationContext, request)

                val parameters = RegistrationResultParameters.from(result.data).asMap()
                Log.d(TAG, "Successful passkey registration, continuing flow with parameters $parameters")

                onSuccess(parameters)
            } catch (e: CreateCredentialException) {
                Log.d(TAG, "Failed to register webauthn device")
                handleRegistrationFailure(e, clientOperation, promise)
            }
        }
    }

    private fun createRegisterJson(response: WebAuthnRegistrationClientOperationStep): String {
        val publicKeyRequest = response?.requestPublicKey
            ?: throw HaapiException("No platform device request in passkey haapi response")

        val json = JsonUtil.toJsonString(WebAuthnRegistrationRequest.from(publicKeyRequest))
        Log.d(TAG, "Created registration json: $json")
        return json
    }


    private fun handleRegistrationFailure(
        e: CreateCredentialException,
        clientOperation: WebAuthnRegistrationClientOperationStep,
        promise: Promise
    ) {
        when (e) {
            is CreateCredentialCancellationException -> {
                Log.d(TAG, "User cancelled the registration")
                webauthnFailed(EventType.WebAuthnUserCancelled, clientOperation, promise)
            }

            is CreatePublicKeyCredentialDomException -> {
                Log.i(TAG, "Registration failed: ${e.message}, type: ${e.type}")
                if (e.domError is InvalidStateError) {
                    webauthnFailed(EventType.WebAuthnRegistrationFailedKeyRegistered, clientOperation, promise)
                } else {
                    webauthnFailed(EventType.WebAuthnRegistrationFailed, clientOperation, promise)
                }
            }

            else -> Log.i(TAG, "Registration failed: ${e.message}, type: ${e.type}")
        }
    }

    private fun handleAuthenticationFailure(
        e: GetCredentialException,
        clientOperation: WebAuthnAuthenticationClientOperationStep,
        promise: Promise
    ) {
        when (e) {
            is GetCredentialCancellationException -> {
                Log.d(TAG, "User cancelled passkey authentication. Allow the try again or register a device")
                webauthnFailed(EventType.WebAuthnUserCancelled, clientOperation, promise)
            }

            else -> {
                Log.d(TAG, "Failed to authenticate using webauthn device")
                webauthnFailed(EventType.WebAuthnRegistrationFailed, clientOperation, promise)
            }
        }
    }

    private fun webauthnFailed(
        eventType: EventType,
        clientOperation: JsonObjectable,
        promise: Promise,
    ) {
        val jsonString = clientOperation.toJsonString()
        _eventEmitter.sendEvent(eventType, jsonString)
        promise.resolve(JsonUtil.jsonToNativeMap(jsonString))
    }
}