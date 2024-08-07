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

import android.annotation.SuppressLint
import android.content.Context
import com.facebook.react.bridge.ReactApplicationContext
import se.curity.identityserver.haapi.android.driver.ClientAuthenticationMethodConfiguration
import se.curity.identityserver.haapi.android.driver.KeyPairAlgorithmConfig
import se.curity.identityserver.haapi.android.driver.TokenBoundConfiguration
import se.curity.identityserver.haapi.android.sdk.DcrConfiguration
import se.curity.identityserver.haapi.android.sdk.HaapiAccessorFactory
import se.curity.identityserver.haapi.android.sdk.HaapiConfiguration
import se.curity.identityserver.haapi.android.sdk.OAuthAuthorizationParameters
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLConnection
import java.security.cert.X509Certificate
import java.time.Duration
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

object HaapiConfigurationUtil {

    fun createConfiguration(conf: HashMap<String, Any>, reactContext: ReactApplicationContext) = HaapiConfiguration(
        keyStoreAlias = asStringOrDefault(
            conf, "keyStoreAlias", "haapi-react-native-android"
        ),
        tokenEndpointUri = URI(asStringOrThrow(conf, "tokenEndpointUri")),
        authorizationEndpointUri = URI(
            asStringOrThrow(
                conf, "authorizationEndpointUri"
            )
        ),
        revocationEndpointUri = asOptionalUri(conf, "revocationEndpointUri"),
        baseUri = URI(asStringOrThrow(conf, "baseUri")),
        clientId = asStringOrThrow(conf, "clientId"),
        appRedirect = asStringOrDefault(conf, "appRedirect", "haapi://"),
        isAutoRedirect = true,
        minTokenTtl = Duration.ofSeconds(300L),
        authorizationParametersProvider = {
            OAuthAuthorizationParameters(
                scope = asStringOrDefault(conf, "scope", "").split(" "),
                acrValues = asStringOrDefault(conf, "acrValues", "").split(" "),
                extraRequestParameters = asStringMap(conf, "extraRequestParameters")
            )
        },
        httpHeadersProvider = {
            asStringMap(conf, "extraHttpHeaders")
        },
        httpUrlConnectionProvider = { url ->
            val validateCertificate = conf["validateTlsCertificate"] as? Boolean? ?: true
            url.openConnection().apply {
                connectTimeout = 8000
                if (!validateCertificate) {
                    disableSslTrustVerification()
                }
            } as HttpURLConnection
        },
        tokenBoundConfiguration = createTokenBoundConfiguration(reactContext)
    )

    fun addFallbackConfiguration(accessorFactory: HaapiAccessorFactory, conf: HashMap<String, Any>, context: Context) {
        val registrationEndpoint = asOptionalUri(conf, "registrationEndpointUri") ?: return
        val fallbackTemplate = asStringOrThrow(conf, "fallback_template_id")
        val registrationClientSecret = asStringOrThrow(conf, "registration_secret")
        val dcrConfiguration =
            DcrConfiguration(fallbackTemplate, registrationEndpoint, context)
        accessorFactory.setDcrConfiguration(dcrConfiguration)
        accessorFactory.setClientAuthenticationMethodConfiguration(
            ClientAuthenticationMethodConfiguration.Secret(
                registrationClientSecret
            )
        )
    }

    private fun createTokenBoundConfiguration(reactContext: ReactApplicationContext) = TokenBoundConfiguration(
        keyAlias = "haapi-module-dpop-key",
        keyPairAlgorithmConfig = KeyPairAlgorithmConfig.ES256,
        storage = SharedPreferencesStorage("token-bound-storage", reactContext),
        currentTimeMillisProvider = { System.currentTimeMillis() }
    )

    private fun asStringMap(conf: HashMap<String, Any>, parameter: String): Map<String, String> {
        return conf[parameter] as? Map<String, String> ?: mapOf()
    }

    private fun asStringOrDefault(conf: HashMap<String, Any>, parameter: String, defaultValue: String): String =
        asOptionalString(conf, parameter) ?: defaultValue

    private fun asStringOrThrow(conf: HashMap<String, Any>, parameter: String): String =
        asOptionalString(conf, parameter) ?: throw RuntimeException("Missing $parameter in configuration")

    private fun asOptionalString(conf: HashMap<String, Any>, parameter: String): String? = conf[parameter] as? String?

    private fun asOptionalUri(conf: HashMap<String, Any>, parameter: String): URI? {
        val optionalValue = asOptionalString(conf, parameter)
        return if (optionalValue != null) URI(optionalValue) else null
    }

    @SuppressLint("CustomX509TrustManager")
    private class TrustAllTrustManger : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private fun URLConnection.disableSslTrustVerification(): URLConnection {
        when (this) {
            is HttpsURLConnection -> {
                val sslContext = SSLContext.getInstance("SSL")
                sslContext.init(null, arrayOf(TrustAllTrustManger()), null)
                this.sslSocketFactory = sslContext.socketFactory
                this.setHostnameVerifier { _, _ -> true }
            }
        }
        return this
    }

}
