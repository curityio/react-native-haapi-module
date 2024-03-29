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
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableNativeMap
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.google.gson.Gson
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import se.curity.identityserver.haapi.android.driver.HaapiLogger
import se.curity.identityserver.haapi.android.sdk.HaapiManager
import se.curity.identityserver.haapi.android.sdk.OAuthTokenManager
import se.curity.identityserver.haapi.android.sdk.models.AuthenticatorSelectorStep
import se.curity.identityserver.haapi.android.sdk.models.ContinueSameStep
import se.curity.identityserver.haapi.android.sdk.models.HaapiRepresentation
import se.curity.identityserver.haapi.android.sdk.models.HaapiResponse
import se.curity.identityserver.haapi.android.sdk.models.Link
import se.curity.identityserver.haapi.android.sdk.models.OAuthAuthorizationResponseStep
import se.curity.identityserver.haapi.android.sdk.models.PollingStatus
import se.curity.identityserver.haapi.android.sdk.models.PollingStep
import se.curity.identityserver.haapi.android.sdk.models.ProblemRepresentation
import se.curity.identityserver.haapi.android.sdk.models.ProblemType
import se.curity.identityserver.haapi.android.sdk.models.RepresentationType
import se.curity.identityserver.haapi.android.sdk.models.actions.Action
import se.curity.identityserver.haapi.android.sdk.models.actions.FormActionModel
import se.curity.identityserver.haapi.android.sdk.models.oauth.ErrorTokenResponse
import se.curity.identityserver.haapi.android.sdk.models.oauth.SuccessfulTokenResponse
import se.curity.identityserver.haapi.android.sdk.models.oauth.TokenResponse
import kotlin.coroutines.CoroutineContext

const val TAG = "HaapiNative"

class HaapiModule(private val _reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(_reactContext),
    LifecycleEventListener {

    override fun getName() = "HaapiModule"

    private var _haapiResponse: HaapiRepresentation? = null
    private var _tokenResponse: SuccessfulTokenResponse? = null
    private val _gson = Gson()

    init {
        HaapiLogger.enabled = true
        HaapiLogger.isDebugEnabled = true
        _reactContext.addLifecycleEventListener(this)
    }

    companion object {
        private var _accessorRepository: HaapiAccessorRepository? = null
    }

    override fun onHostResume() {
        Log.d(TAG, "App was resumed")
    }

    override fun onHostPause() {
        Log.d(TAG, "App was paused")
    }

    override fun onHostDestroy() {
        Log.d(TAG, "App was destroyed")
    }

    @ReactMethod
    fun load(conf: ReadableMap, promise: Promise) {
        if(_accessorRepository != null) {
            _accessorRepository!!.close()
        }

        try {
            _accessorRepository = HaapiAccessorRepository(conf, _reactContext)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load configuration ${e.message}")
            rejectRequest(e, promise)
            return
        }
        promise.resolve(true)
    }

    @ReactMethod
    fun start(promise: Promise) {

        Log.d(TAG, "Start was called")
        try {
            withHaapiManager(promise) { haapiManager, context ->
                haapiManager.start(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, e.message ?: "Failed to attest $e")
            rejectRequest(e, promise)
        }
    }

    @ReactMethod
    fun logout(promise: Promise) {
        Log.d(TAG, "Logout was called, revoking tokens")
        try {
            if (_tokenResponse != null && _tokenResponse!!.refreshToken != null) {
                Log.d(TAG, "Revoking refresh token")
                withTokenManager(promise) { tokenManager, context ->
                    tokenManager.revokeRefreshToken(_tokenResponse!!.refreshToken!!, context)
                    null
                }
            } else if (_tokenResponse != null) {
                Log.d(TAG, "Revoking access token")
                withTokenManager(promise){ tokenManager, context ->
                    tokenManager.revokeAccessToken(_tokenResponse!!.accessToken, context)
                    null
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to revoke tokens: ${e.message}")
        }

        _accessorRepository?.close()
        _tokenResponse = null
        resolveRequest("LoggedOut", "{}", promise)
    }

    @ReactMethod
    fun refreshAccessToken(refreshToken: String, promise: Promise) {
        Log.d(TAG, "Refreshing access token")
        try {
            withTokenManager(promise) { tokenManager, coroutineContext ->
                tokenManager.refreshAccessToken(refreshToken, coroutineContext)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to revoke tokens: ${e.message}")
            rejectRequest(e, promise)
        }
    }

    @ReactMethod
    fun navigate(linkMap: ReadableMap, promise: Promise) {
        val linkJson = _gson.toJson(linkMap.toHashMap())
        val link = _gson.fromJson(linkJson, Link::class.java)
        try {
            withHaapiManager(promise) { haapiManager, context ->
                haapiManager.followLink(link, context)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to navigate to link: ${e.message}")
            rejectRequest(e, promise)
        }
    }


    @ReactMethod
    fun submitForm(action: ReadableMap, parameters: ReadableMap, promise: Promise) {
        getAction(action, _haapiResponse as HaapiRepresentation, parameters, promise)
    }

    /**
     * Needs to be here to suppress warnings from react-native
     */
    @ReactMethod
    fun addListener(eventName: String) {

    }

    /**
     * Needs to be here to suppress warnings from react-native
     */
    @ReactMethod
    fun removeListeners(count: Int) {

    }

    private fun submitModel(model: FormActionModel, parameters: Map<String, Any>, promise: Promise) {

        Log.d(TAG, "Submitting form $model}")
        try {
            withHaapiManager(promise) { haapiManager, _ ->
                haapiManager.submitForm(model, parameters)
            }
        } catch (e: Exception) {
            rejectRequest(e, promise)
        }
    }

    private fun handleCodeResponse(response: OAuthAuthorizationResponseStep, promise: Promise) {
        withTokenManager(promise) { manager, context ->
            manager.fetchAccessToken(response.properties.code, context)
        }
    }

    private fun handleTokenResponse(tokenResponse: TokenResponse, promise: Promise) {
        if (tokenResponse is SuccessfulTokenResponse) {
            val tokenMap = mapOf(
                "accessToken" to tokenResponse.accessToken,
                "refreshToken" to tokenResponse.refreshToken,
                "scope" to tokenResponse.scope,
                "idToken" to tokenResponse.idToken,
                "expiresIn" to tokenResponse.expiresIn.seconds
            )
            _tokenResponse = tokenResponse
            resolveRequest("TokenResponse", _gson.toJson(tokenMap), promise)
        } else {
            tokenResponse as ErrorTokenResponse
            val errorResponseMap = mapOf(
                "error" to tokenResponse.error, "error_description" to tokenResponse.errorDescription
            )
            resolveRequest("TokenResponseError", _gson.toJson(errorResponseMap), promise)
        }
    }

    private fun getAction(actionMap: ReadableMap, representation: HaapiRepresentation, parameters: ReadableMap, promise: Promise) {
        // TODO: Match more precise
        val submitKind = actionMap.getString("kind")
        Log.d(TAG, "Looking for form with kind $submitKind")
        Log.d(TAG, "Current response: $_haapiResponse")

        val action = representation.actions.find {
            it.toJsonObject().get("kind") == submitKind
        }
        (action as? Action.Form)?.let { submitModel(it.model, parameters.toHashMap(), promise) }
    }

    private fun handleHaapiResponse(response: HaapiResponse, promise: Promise) {
        when (response) {
            is HaapiRepresentation -> {
                Log.d(TAG, "Updating reference to representation")
                _haapiResponse = response

                when (response) {
                    is AuthenticatorSelectorStep -> resolveRequest(
                        "AuthenticationSelectorStep", response.toJsonString(), promise
                    )

                    is ContinueSameStep -> resolveRequest(
                        "ContinueSameStep", response.toJsonString(), promise
                    )

                    is OAuthAuthorizationResponseStep -> handleCodeResponse(response, promise)
                    is PollingStep -> handlePollingStep(response, promise)

                    else -> if (response.type == RepresentationType.AUTHENTICATION_STEP) {
                        resolveRequest("AuthenticationStep", response.toJsonString(), promise)
                    } else {
                        Log.d(TAG, "Unknown step ${response.type}")
                        resolveRequest("UnknownResponse", response.toJsonString(), promise)
                    }

                }
            }

            is ProblemRepresentation -> {
                when (response.type) {
                    ProblemType.IncorrectCredentialsProblem -> {
                        resolveRequest("IncorrectCredentials", response.toJsonString(), promise)
                    }

                    ProblemType.SessionAndAccessTokenMismatchProblem -> {
                        resolveRequest("SessionTimedOut", response.toJsonString(), promise)
                    }

                    else -> {
                        resolveRequest("ProblemRepresentation", response.toJsonString(), promise)
                    }
                }
            }
        }

        Log.d(TAG, response.toJsonString())
    }

    private fun handlePollingStep(pollingStep: PollingStep, promise: Promise) {
        sendEvent("PollingStep", pollingStep.toJsonString())
        when (pollingStep.properties.status) {
            PollingStatus.PENDING -> resolveRequest("PollingStepResult", pollingStep.toJsonString(), promise)
            PollingStatus.FAILED -> {
                sendEvent("StopPolling", "{}")
                submitModel(pollingStep.mainAction.model, emptyMap(), promise)
            }
            PollingStatus.DONE -> {
                sendEvent("StopPolling", "{}")
                submitModel(pollingStep.mainAction.model, emptyMap(), promise)
            }
        }
    }

    @Throws(HaapiException::class)
    private fun withTokenManager(promise: Promise, accessorRequest: suspend (tokenManager: OAuthTokenManager, coroutineContext: CoroutineContext) -> TokenResponse?) {
        _accessorRepository ?: handleNotInitialized()

        runBlocking {
            launch {
                try {
                    val response =
                        accessorRequest(_accessorRepository!!.accessor.oAuthTokenManager, this.coroutineContext)
                    if (response != null) {
                        handleTokenResponse(response, promise)
                        promise.resolve(response.toJsonString())
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to make token request: ${e.message}")
                    throw FailedTokenManagerRequestException("Failed to make token request", e)
                }
            }
        }

    }

    @Throws(HaapiException::class)
    private fun withHaapiManager(promise: Promise, accessorRequest: suspend (manager: HaapiManager, context: CoroutineContext) -> HaapiResponse) {
        _accessorRepository ?: handleNotInitialized()

        runBlocking {
            launch {
                try {
                    val response = accessorRequest(_accessorRepository!!.accessor.haapiManager, this.coroutineContext)
                    handleHaapiResponse(response, promise)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to make HAAPI request: ${e.message}")
                    throw FailedHaapiRequestException("Failed to make HAAPI request: ${e.message}", e)
                }
            }
        }
    }

    private fun handleNotInitialized() {
        Log.w(
            TAG, "Accessor repository not initialized. " +
                    "Please run load() with configuration before accessing HAAPI functionality"
        )
        throw HaapiNotInitializedException()
    }


    private fun sendEvent(eventName: String, json: String) {
        Log.d(TAG, "Firing event $eventName")

        val reactMap = jsonToMap(json)
        _reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java).emit(eventName, reactMap)
    }

    private fun rejectRequest(exception: Exception, promise: Promise) {
        val map = Arguments.createMap()
        map.putString("error", "HaapiError")
        map.putString("error_description", exception.message)
        _reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java).emit("HaapiError", map.copy())
        promise.reject(exception, map)
    }

    private fun jsonToMap(json: String): WritableNativeMap? {
        var map = HashMap<String, Any>()
        map = _gson.fromJson(json, map::class.java)
        return Arguments.makeNativeMap(map)
    }

    private fun resolveRequest(eventType: String, body: String, promise: Promise) {
        sendEvent(eventType, body)
        promise.resolve(jsonToMap(body))
    }

}
