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

import se.curity.identityserver.haapi.android.sdk.models.actions.ClientOperationActionModel
import se.curity.identityserver.haapi.android.sdk.models.actions.ClientOperationActionModel.WebAuthnRegistration.PublicKey
import se.curity.identityserver.haapi.android.sdk.models.actions.Credential
import java.util.stream.Collectors
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class RelyingParty(val name: String, val id: String)

data class User(val name: String, val displayName: String, val id: String)

data class AuthenticatorSelection(
    val authenticatorAttachment: String,
    val userVerification: String = "required",
    val requiresResidentKey: Boolean = true, val residentKey: String = "required"
)

@OptIn(ExperimentalEncodingApi::class)
private fun encodeToUrlSafeString(bytes: ByteArray): String = Base64.UrlSafe.encode(bytes)

private fun mapCredentials(credentials: List<Credential>): List<UrlSafeCredential> =
    credentials.stream().map { UrlSafeCredential.from(it) }.collect(Collectors.toList())

data class UrlSafeCredential(val id: String, val type: String = "public-key") {
    companion object {
        fun from(credential: Credential): UrlSafeCredential =
            UrlSafeCredential(encodeToUrlSafeString(credential.idByteArray), credential.type)
    }
}

data class WebAuthnAuthenticationRequest(
    val rpId: String,
    val userVerification: String = "required",
    val challenge: String,
    val extensions: Map<String, Any> = emptyMap(),
    val allowCredentials: List<UrlSafeCredential>,
    val platformCredentials: List<UrlSafeCredential>,
    val crossPlatformCredentials: List<UrlSafeCredential>,
) {

    companion object {
        fun from(publicKey: ClientOperationActionModel.WebAuthnAuthentication.PublicKey): WebAuthnAuthenticationRequest {
            val allowCredentials = mapCredentials(publicKey.allowCredentials)
            val platformCredentials = mapCredentials(publicKey.platformCredentials)
            val crossPlatformCredentials = mapCredentials(publicKey.crossPlatformCredentials)
            return WebAuthnAuthenticationRequest(
                publicKey.relyingPartyId,
                publicKey.userVerification,
                encodeToUrlSafeString(publicKey.challenge),
                emptyMap(),
                allowCredentials,
                platformCredentials,
                crossPlatformCredentials
            )
        }

    }
}

data class WebAuthnRegistrationRequest(
    val rp: RelyingParty,
    val user: User,
    val challenge: String,
    val authenticatorSelection: AuthenticatorSelection,
    val excludeCredentials: List<UrlSafeCredential>,
    val pubKeyCredParams: List<PublicKey.CredentialParameter>,
    val extensions: Map<String, String> = emptyMap()
) {

    companion object {
        fun from(publicKey: PublicKey): WebAuthnRegistrationRequest {
            val rp = RelyingParty(publicKey.relyingPartyName, publicKey.relyingPartyId)
            val user = User(publicKey.userName, publicKey.userDisplayName, encodeToUrlSafeString(publicKey.userId))
            val pubkeyParams = publicKey.credentialParameters
            val selection = AuthenticatorSelection(
                publicKey.authenticatorSelection.authenticatorAttachment,
                publicKey.authenticatorSelection.userVerification
            )
            val excludedCredentials = mapCredentials(publicKey.excludedCredentials)
            return WebAuthnRegistrationRequest(
                rp, user, encodeToUrlSafeString(publicKey.challenge), selection,
                excludedCredentials, pubkeyParams
            )
        }
    }

}
