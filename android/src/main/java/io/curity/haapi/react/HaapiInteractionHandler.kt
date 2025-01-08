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
import io.curity.haapi.react.events.EventEmitter
import io.curity.haapi.react.events.EventType
import io.curity.haapi.react.events.EventType.HaapiLoading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import se.curity.identityserver.haapi.android.sdk.HaapiManager
import se.curity.identityserver.haapi.android.sdk.OAuthTokenManager
import se.curity.identityserver.haapi.android.sdk.models.HaapiResponse
import se.curity.identityserver.haapi.android.sdk.models.Link
import se.curity.identityserver.haapi.android.sdk.models.OAuthAuthorizationResponseStep
import se.curity.identityserver.haapi.android.sdk.models.actions.FormActionModel
import se.curity.identityserver.haapi.android.sdk.models.oauth.TokenResponse
import kotlin.coroutines.CoroutineContext

class HaapiInteractionHandler(private val _reactContext: ReactApplicationContext) {

    companion object {
        private var _accessorRepository: HaapiAccessorRepository? = null
    }

    private val _haapiScope = CoroutineScope(Dispatchers.IO)
    private val _eventEmitter = EventEmitter(_reactContext)


    /**
     * Load the configuration. This needs to be called before any interaction with HAAPI
     * If called multiple times, the repo is closed to be able to load new configuration.
     *
     * @throws FailedToInitalizeHaapiException when the the configuration fails to load
     */
    @Throws(FailedToInitalizeHaapiException::class)
    fun loadConfiguration(conf: Map<String, Any>) {
        // In case the app was recycled in dev mode, close the repo so we may set it up again
        if (_accessorRepository != null) {
            _accessorRepository!!.close()
        }

        try {
            _accessorRepository = HaapiAccessorRepository(conf, _reactContext)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load configuration ${e.message}")
            throw FailedToInitalizeHaapiException("Failed to load configuration", e)
        }
    }

    /**
     * Starts the flow by performing an authorization request to the configured server.
     * This will perform attestation and obtain an API access token
     */
    fun startAuthentication(
        onSuccess: (HaapiResponse) -> Unit,
        onError: (FailedHaapiRequestException) -> Unit
    ) {
        withHaapiManager(onSuccess, onError) { haapiManager, context ->
            haapiManager.start(context)
        }
    }

    fun followLink(
        link: Link,
        onSuccess: (HaapiResponse) -> Unit,
        onError: (FailedHaapiRequestException) -> Unit
    ) {
        withHaapiManager(onSuccess, onError) { haapiManager, context ->
            haapiManager.followLink(link, context)
        }
    }

    fun submitForm(
        form: FormActionModel,
        parameters: Map<String, Any>,
        onSuccess: (HaapiResponse) -> Unit,
        onError: (FailedHaapiRequestException) -> Unit
    ) {
        withHaapiManager(onSuccess, onError) { haapiManager, context ->
            haapiManager.submitForm(form, parameters, context)
        }
    }

    fun exchangeCode(
        codeResponse: OAuthAuthorizationResponseStep,
        onSuccess: (TokenResponse) -> Unit,
        onError: (FailedTokenManagerRequestException) -> Unit
    ) {
        withTokenManager(onSuccess, onError) { tokenManager, context ->
            tokenManager.fetchAccessToken(codeResponse.properties.code, context)
        }
    }

    fun refreshAccessToken(
        refreshToken: String,
        onSuccess: (TokenResponse) -> Unit,
        onError: (FailedTokenManagerRequestException) -> Unit
    ) {
        Log.d(TAG, "Refreshing access token")
        withTokenManager(onSuccess, onError) { tokenManager, coroutineContext ->
            tokenManager.refreshAccessToken(refreshToken, coroutineContext)
        }
    }

    fun logoutAndRevokeTokens(
        accessToken: String,
        refreshToken: String? = null,
        onSuccess: (TokenResponse) -> Unit,
        onError: (FailedTokenManagerRequestException) -> Unit
    ) {
        if (refreshToken != null) {
            Log.d(TAG, "Revoking refresh token")
            withTokenManager(onSuccess, onError) { tokenManager, context ->
                tokenManager.revokeRefreshToken(refreshToken, context)
                null
            }
        } else {
            Log.d(TAG, "Revoking access token")
            withTokenManager(onSuccess, onError) { tokenManager, context ->
                tokenManager.revokeAccessToken(accessToken, context)
                null
            }
        }

        closeHaapiConnection()
    }

    fun closeHaapiConnection() {
        _accessorRepository?.close()
    }

    private fun withHaapiManager(
        onSuccess: (HaapiResponse) -> Unit,
        onError: ((FailedHaapiRequestException) -> Unit)? = null,
        accessorRequest: suspend (manager: HaapiManager, context: CoroutineContext) -> HaapiResponse
    ) {
        _eventEmitter.sendEvent(HaapiLoading)

        val manager = _accessorRepository?.accessor?.haapiManager ?: throw notInitialized()

        _haapiScope.launch {
            try {
                val response = accessorRequest(manager, this.coroutineContext)
                onSuccess(response)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to make Haapi request: ${e.message}")
                _eventEmitter.sendEvent(EventType.HaapiFinishedLoading)
                if (onError != null) {
                    onError(
                        FailedHaapiRequestException(
                            "Failed to make Haapi request: ${e.message}",
                            e
                        )
                    )
                }
            } finally {
                _eventEmitter.sendEvent(EventType.HaapiFinishedLoading)
            }
        }
    }

    private fun withTokenManager(
        onSuccess: ((TokenResponse) -> Unit)? = null,
        onError: ((FailedTokenManagerRequestException) -> Unit)? = null,
        accessorRequest: suspend (tokenManager: OAuthTokenManager, coroutineContext: CoroutineContext) -> TokenResponse?
    ) {
        _eventEmitter.sendEvent(HaapiLoading)

        val manager = _accessorRepository?.accessor?.oAuthTokenManager ?: throw notInitialized()

        _haapiScope.launch {
            try {
                val response = accessorRequest(manager, this.coroutineContext)
                if (onSuccess != null && response != null) {
                    onSuccess(response)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to make HAAPI token request: ${e.message}")
                _eventEmitter.sendEvent(EventType.HaapiFinishedLoading)
                if (onError != null) {
                    onError(
                        FailedTokenManagerRequestException(
                            "Failed to make HAAPI token request: ${e.message}",
                            e
                        )
                    )
                }
            } finally {
                _eventEmitter.sendEvent(EventType.HaapiFinishedLoading)
            }
        }

    }

    private fun notInitialized(): HaapiNotInitializedException {
        Log.w(
            TAG, "Accessor repository not initialized. " +
                    "Please run load() with configuration before accessing HAAPI functionality"
        )
        _eventEmitter.sendEvent(EventType.HaapiFinishedLoading)

        throw HaapiNotInitializedException()
    }
}
