package io.curity.haapi.react

import android.annotation.SuppressLint
import android.content.Context
import se.curity.identityserver.haapi.android.driver.ClientAuthenticationMethodConfiguration
import se.curity.identityserver.haapi.android.sdk.DcrConfiguration
import se.curity.identityserver.haapi.android.sdk.HaapiAccessorFactory
import se.curity.identityserver.haapi.android.sdk.HaapiConfiguration
import se.curity.identityserver.haapi.android.sdk.OAuthAuthorizationParameters
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLConnection
import java.time.Duration
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext

object HaapiConfigurationUtil {

    fun createConfiguration(conf: HashMap<String, Any>): HaapiConfiguration = HaapiConfiguration(
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
                scope = asStringOrDefault(conf, "scope", "").split(" ")
            )
        },
        httpHeadersProvider = {
            mapOf()
        },
        httpUrlConnectionProvider = { url ->
            val validateCertificate = conf["validateTlsCertificate"] as? Boolean? ?: true
            url.openConnection().apply {
                connectTimeout = 8000
                if(!validateCertificate) {
                    disableSslTrustVerification()
                }
            } as HttpURLConnection
        },
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