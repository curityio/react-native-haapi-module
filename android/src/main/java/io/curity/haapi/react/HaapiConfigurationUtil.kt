package io.curity.haapi.react

import android.content.Context
import se.curity.identityserver.haapi.android.driver.ClientAuthenticationMethodConfiguration
import se.curity.identityserver.haapi.android.sdk.DcrConfiguration
import se.curity.identityserver.haapi.android.sdk.HaapiAccessorFactory
import se.curity.identityserver.haapi.android.sdk.HaapiConfiguration
import se.curity.identityserver.haapi.android.sdk.OAuthAuthorizationParameters
import java.net.HttpURLConnection
import java.net.URI
import java.time.Duration

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
            url.openConnection().apply { connectTimeout = 8000 } as HttpURLConnection
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
}