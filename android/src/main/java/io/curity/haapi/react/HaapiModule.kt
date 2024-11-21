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
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.google.gson.Gson
import io.curity.haapi.react.JsonUtil.jsonToNativeMap
import io.curity.haapi.react.JsonUtil.mapToNativeMap
import io.curity.haapi.react.events.EventEmitter
import io.curity.haapi.react.events.EventType
import io.curity.haapi.react.events.EventType.*
import io.curity.haapi.react.webauthn.WebAuthnHandler
import se.curity.identityserver.haapi.android.driver.HaapiLogger
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
import se.curity.identityserver.haapi.android.sdk.models.WebAuthnAuthenticationClientOperationStep
import se.curity.identityserver.haapi.android.sdk.models.WebAuthnRegistrationClientOperationStep
import se.curity.identityserver.haapi.android.sdk.models.actions.Action
import se.curity.identityserver.haapi.android.sdk.models.actions.FormActionModel
import se.curity.identityserver.haapi.android.sdk.models.oauth.ErrorTokenResponse
import se.curity.identityserver.haapi.android.sdk.models.oauth.SuccessfulTokenResponse
import se.curity.identityserver.haapi.android.sdk.models.oauth.TokenResponse

const val TAG = "HaapiNative"

class HaapiModule(private val _reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(_reactContext),
    LifecycleEventListener {

    override fun getName() = "HaapiModule"

    private var _haapiResponse: HaapiRepresentation? = null
    private var _tokenResponse: SuccessfulTokenResponse? = null
    private val _gson = Gson()
    private val _handler = HaapiInteractionHandler(_reactContext)
    private val _eventEmitter = EventEmitter(_reactContext)
    private val _webAuthnHandler = WebAuthnHandler(_reactContext)

    init {
        HaapiLogger.enabled = true
        HaapiLogger.isDebugEnabled = true
        _reactContext.addLifecycleEventListener(this)
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
        try {
            _handler.loadConfiguration(conf.toHashMap())
        } catch (e: HaapiException) {
            rejectRequest(e, promise)
        }
        promise.resolve(true)
    }

    @ReactMethod
    fun start(promise: Promise) {
        Log.d(TAG, "Start was called")

        try {
            _handler.startAuthentication { response -> handleHaapiResponse(response, promise) }
        } catch (e: Exception) {
            Log.e(TAG, e.message ?: "Failed to attest $e")
            rejectRequest(e, promise)
        }
    }

    @ReactMethod
    fun logout(promise: Promise) {
        Log.d(TAG, "Logout was called, revoking tokens")

        if (_tokenResponse != null) {
            _handler.logoutAndRevokeTokens(_tokenResponse!!.accessToken, _tokenResponse!!.refreshToken)
        } else {
            _handler.closeHaapiConnection()
        }

        _tokenResponse = null
        resolveRequest(LoggedOut, "{}", promise)
    }

    @ReactMethod
    fun refreshAccessToken(refreshToken: String, promise: Promise) {
        Log.d(TAG, "Refreshing access token")

        try {
            _handler.refreshAccessToken(refreshToken) { tokenResponse ->
                handleTokenResponse(tokenResponse, promise)
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
            _handler.followLink(link) { response -> handleHaapiResponse(response, promise) }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to navigate to link: ${e.message}")
            rejectRequest(e, promise)
        }
    }

    @ReactMethod
    fun submitForm(actionMap: ReadableMap, parameters: ReadableMap, promise: Promise) {

        val action = findAction(actionMap, _haapiResponse as HaapiRepresentation)
        if (action == null) {
            Log.d(TAG, "Failed to find action to submit. Possible re-submit")
            return
        }

        submitModel(action.model, parameters.toHashMap(), promise)
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
        Log.d(TAG, "Submitting form $model")
        try {
            _handler.submitForm(model, parameters) { response ->
                handleHaapiResponse(response, promise)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to submit form: ${e.message}")
            rejectRequest(e, promise)
        }

    }

    private fun handleCodeResponse(response: OAuthAuthorizationResponseStep, promise: Promise) {
        try {
            _handler.exchangeCode(response) { tokenResponse ->
                handleTokenResponse(tokenResponse, promise)
            }

        } catch (e: Exception) {
            Log.w(TAG, "Failed to exchange code: ${e.message}")
            rejectRequest(e, promise)
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
            resolveRequest(TokenResponse, _gson.toJson(tokenMap), promise)
        } else {
            tokenResponse as ErrorTokenResponse
            val errorResponseMap = mapOf(
                "error" to tokenResponse.error, "error_description" to tokenResponse.errorDescription
            )
            resolveRequest(TokenResponseError, JsonUtil.toJsonString(errorResponseMap), promise)
        }
    }

    private fun findAction(
        actionMap: ReadableMap,
        representation: HaapiRepresentation,
    ): Action.Form? {
        // TODO: Match more precise
        val submitKind = actionMap.getString("kind")
        Log.d(TAG, "Looking for form with kind $submitKind")
        Log.d(TAG, "Current response: $_haapiResponse")

        val action = representation.actions.find {
            it.toJsonObject().get("kind") == submitKind
        }
        return action as? Action.Form
    }

    private fun handleHaapiResponse(response: HaapiResponse, promise: Promise) {

        when (response) {
            is HaapiRepresentation -> {
                Log.d(TAG, "Updating reference to representation")
                _haapiResponse = response

                when (response) {
                    is WebAuthnRegistrationClientOperationStep -> handleWebAuthnRegistration(response, promise)

                    is WebAuthnAuthenticationClientOperationStep -> handleWebAuthnAuthentication(response, promise)

                    is AuthenticatorSelectorStep -> resolveRequest(
                        AuthenticationSelectorStep, response.toJsonString(), promise
                    )

                    is ContinueSameStep -> resolveRequest(
                        ContinueSameStep, response.toJsonString(), promise
                    )

                    is OAuthAuthorizationResponseStep -> handleCodeResponse(response, promise)
                    is PollingStep -> handlePollingStep(response, promise)

                    else -> when (response.type) {
                        RepresentationType.AUTHENTICATION_STEP -> {
                            resolveRequest(AuthenticationStep, response.toJsonString(), promise)
                        }

                        RepresentationType.REGISTRATION_STEP -> {
                            resolveRequest(RegistrationStep, response.toJsonString(), promise)
                        }

                        else -> {
                            Log.d(TAG, "Unknown step ${response.type}")
                            resolveRequest(UnknownResponse, response.toJsonString(), promise)
                        }
                    }
                }
            }

            is ProblemRepresentation -> {
                when (response.type) {
                    ProblemType.IncorrectCredentialsProblem -> {
                        resolveRequest(IncorrectCredentials, response.toJsonString(), promise)
                    }

                    ProblemType.SessionAndAccessTokenMismatchProblem -> {
                        resolveRequest(SessionTimedOut, response.toJsonString(), promise)
                    }

                    else -> {
                        resolveRequest(ProblemRepresentation, response.toJsonString(), promise)
                    }
                }
            }
        }

        Log.d(TAG, response.toJsonString())
    }

    private fun handleWebAuthnAuthentication(
        response: WebAuthnAuthenticationClientOperationStep,
        promise: Promise
    ) =
        _webAuthnHandler.authenticationFlow(response, promise) { parameters ->
            submitModel(response.continueFormActionModel, parameters, promise)
        }

    private fun handleWebAuthnRegistration(
        response: WebAuthnRegistrationClientOperationStep,
        promise: Promise
    ) =
        _webAuthnHandler.registrationFlow(response, promise) { parameters ->
            submitModel(response.continueFormActionModel, parameters, promise)
        }

    private fun handlePollingStep(pollingStep: PollingStep, promise: Promise) {

        when (pollingStep.properties.status) {
            PollingStatus.PENDING -> {
                _eventEmitter.sendEvent(PollingStep, pollingStep.toJsonString())
                resolveRequest(PollingStepResult, pollingStep.toJsonString(), promise)
            }

            PollingStatus.FAILED -> {
                _eventEmitter.sendEvent(StopPolling, "{}")
                submitModel(pollingStep.mainAction.model, emptyMap(), promise)
            }

            PollingStatus.DONE -> {
                _eventEmitter.sendEvent(StopPolling, "{}")
                submitModel(pollingStep.mainAction.model, emptyMap(), promise)
            }
        }
    }

    private fun rejectRequest(exception: Exception, promise: Promise) {
        val errorDescription = exception.message ?: "general error"
        val jsonMap = mutableMapOf("error" to "HaapiError", "error_description" to errorDescription)

        Log.d(TAG, "Rejecting request using with description '$errorDescription'")

        _eventEmitter.sendEvent(HaapiError, JsonUtil.toJsonString(jsonMap))
        promise.reject(exception, mapToNativeMap(jsonMap))
    }

    private fun resolveRequest(eventType: EventType, body: String, promise: Promise) {
        Log.d(TAG, "Resolving request using event type $eventType and body: $body")

        _eventEmitter.sendEvent(eventType, body)
        promise.resolve(jsonToNativeMap(body))
    }
}
