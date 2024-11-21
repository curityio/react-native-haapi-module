# react-native-haapi-module

[![Quality](https://img.shields.io/badge/quality-test-yellow)](https://curity.io/resources/code-examples/status/) [![Availability](https://img.shields.io/badge/availability-source-blue)](https://curity.io/resources/code-examples/status/)

This a react-native Native Module that use the Hypermedia Authentication API of the Curity Identity Server. The module utilizes the iOS and Android SDK to perform attestation and communication with the API.

<https://curity.io/product/authentication-service/authentication-api/>

## Getting started

`$ npm install @curity/react-native-haapi-module --save`

## Configuration

Parameter Name             | Platform | Required | Default                      | Description
-------------------------- | -------- | -------- | ---------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------
`appRedirect`              | both     | false    | `app:start`                  | Redirect URI to use in OAuth requests. Needs to be registered in server config
`keyStoreAlias`            | android  | false    | `haapi-react-native-android` | Keystore alias for keys used in an authentication flow. Only used on Android
`configurationName`        | ios      | false    | `HaapiModule`                | The name to use for the configuration on iOS. If you are in testing mode and switching environments, make sure that each environment sets a different name
`clientId`                 | both     | true     |                              | The registered `client_id`
`baseUri`                  | both     | true     |                              | Base URI of the server. Used for relative redirects.
`tokenEndpointUri`         | both     | true     |                              | URI of the token endpoint.
`authorizationEndpointUri` | both     | true     |                              | URI of the authorize endpoint.
`revocationEndpointUri`    | both     | true     |                              | URI of the revocation endpoint.
`registrationEndpointUri`  | android  | false    |                              | URI of the registration endpoint. Required if fallback registration should be used.
`fallback_template_id`     | android  | false    |                              | Name of the template client to be used in fallback. Required if fallback registration should be used.
`registration_secret`      | android  | false    |                              | Name of the template client to be used in fallback. Required if fallback registration should be used.
`validateTlsCertificate`   | both     | false    | true                         | If the server TLS certificate should be validated. Set to `false` to accept self signed certificates.
`acrValues`                | both     | false    | `""`                         | Space separated string to send in authorize request.
`scope`                    | both     | false    | `""`                         | Space separated string of scopes to request.
`extraRequestParameters`   | both     | false    | `{}`                         | Map of extra parameters to send in the request to the authorize endpoint.
`extraHttpHeaders`         | both     | false    | `{}`                         | Map of extra http headers to send in all requests to the authentication API.

## Usage

All functions of the module are async operations. The application may use events produced by the module to drive the authentication flow, or rely on results return by promises.

### Load

To use the module, first load the module with the desired configuration.

```javascript
import {NativeModules} from "react-native";

const {HaapiModule} = NativeModules;

// Example configuration
const haapiConfiguration = {
    "appRedirect": "app:start",
    "keyStoreAlias": "haapi-react-native",
    "clientId": "react-dev-client",
    "baseUri": "https://login.example.com",
    "tokenEndpointUri": "https://login.example.com/oauth/token",
    "authorizationEndpointUri": "https://login.example.com/oauth/authorize",
    "revocationEndpointUri": "https://login.example.com/oauth/revoke",
    "scope": "openid profile",
    "registrationEndpointUri": "https://login.example.com/oauth/registration",
    "fallback_template_id": "react-native-fallback",
    "registration_secret": "my-good-secret"
    "validateTlsCertificate": true,
    "extraRequestParameters": {"prompt": "login"},
    "extraHttpHeaders": {"x-my-good-header": "foobar"}
    "acrValues": ""
}

HaapiModule.load(HaapiConfiguration).catch(e => {
    console.error('Error in loading configuration', e);
});

export default HaapiModule;
```

`load()` may be called multiple times with different configuration, to be able to start authentication flows requesting different `acr` or `scope`.

## Start

After the module has been loaded, the `start()` function may be called. `start()` will setup the communication with HAAPI, perform attestation, and then start emitting events for the application to react on. Receiving events will allow the application to know more about the contents of the current state than if it were to receive the raw HaapiResponse. The module will follow redirect responses automatically.

```javascript
try {
    await HaapiModule.start();
} catch (e) {
    console.error(e);
}
```

To listen for the events produced:

```javascript
const eventEmitter = new NativeEventEmitter(HaapiModule);
eventEmitter.addListener("EventName", () => {
    // Handle event
});
```

Since `start()` will start an authentication flow, it's recommended to only call it when a user performs an action to start the login.

## Navigate

To follow a link in a HAAPI response, the `navigate(model)` function can be used. `model` is an object conforming to [Link](https://curity.io/docs/haapi-data-model/latest/links.html)

```javascript
try {
    await HaapiModule.navigate(model);
} catch (e) {
    console.error(e);
}
```

## Submit form

To submit a form in an action, use the submitForm(action, parameters), where `action` is the form to submit, and `parameters` is an object containing the field names and the values to fill the form.

```javascript
try {
    await HaapiModule.submitForm(action, parameters);
} catch (e) {
    console.error(e);
}
```

## Refresh Access Token

Refresh the access token using the refresh token. The application may listen to the events `TokenResponse`/`TokenResponseError` for the result of the refresh.

```javascript
HaapiModule.refreshAccessToken(refreshToken);
```

## Log out

Calling log out will revoke the tokens, and close the underlying managers to clear the state.

```javascript
HaapiModule.logout().then(/* Remove tokens from state */);
```

## Events

Event Name                              | Emitted when
--------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
AuthenticationStep                      | An action is required by the user as part of authentication. See [Authentication Step](https://curity.io/docs/haapi-data-model/latest/authentication-step.html)
AuthenticationSelectorStep              | An `AuthenticationStep` with the kind `authenticator-selector` is received. An authenticator selector screen should be be shown to the user.
ContinueSameStep                        | A screen should be shown to the user, containing some information. The only required action by the user is to accept or in some cases cancel. [Continue Same Step](https://curity.io/docs/haapi-data-model/latest/continue-same-step.html)
PollingStep                             | An authentication step that requires polling was received. May contain information for the user for how to proceed authentication out of band. [Polling Step](https://curity.io/docs/haapi-data-model/latest/polling-step.html)
PollingStepResult                       | A poll result was received with the `status` `PENDING`. The application may show new information to the user and continue polling.
StopPolling                             | A successful poll result was received. Application should stop polling, and the module will continue execution and may issue new events.
TokenResponse                           | Authentication was successful, and the resulting token(s) was received. The payload of the event will contain `accessToken`, `expiresIn` and `scope`. May contain `refreshToken` and `idToken`
TokenResponseError                      | Authentication was successful, but the token request returned an error.
SessionTimedOut                         | The authentication process took too long, and timed out. The user will have to start over using `start()` method again.
IncorrectCredentials                    | The user enter wrong credentials in an `AuthenticationStep`. Show an error to the user and allow them to try again. [Invalid Input Problem](https://curity.io/docs/haapi-data-model/latest/invalid-input-problem.html)
ProblemRepresentation                   | The server returned an unexpected problem. [Problem](https://curity.io/docs/haapi-data-model/latest/problem.html)
HaapiError                              | An unexpected problem happened. Event will have members `error` and `error_description`
RegistrationStep                        | Registration is expected of the user. See [Registration Step](https://curity.io/docs/haapi-data-model/latest/registration-step.html)
UnkownResponse                          | Server returned a response that is not supported by the module
HaapiLoading                            | The module has started a request and is waiting on a response
HaapiFinishedLoading                    | The module received response and finished processing
LoggedOut                               | The module finished the logout
WebAuthnAuthenticationStep              | Current authentication step is a webauthn/passkeys step. The module will perform a client operation to ask the user to authenticate on their device. The full step is provided to the client to be able to show an appropriate screen. [Login with WebAuthn](https://curity.io/docs/haapi-data-model/latest/webauthn-authentication-step.html)
WebAuthnUserCancelled                   | User cancelled the authentication request. App should show appropriate screens for how to proceed
WebAuthnRegistrationFailed              | Registration of a webauthn device failed
WebAuthnRegistrationFailedKeyRegistered | Registration of a webauthn device failed. Reason is likely because the key is already registered. User should proceed to authenticate using the key.

## Example implementation

See <https://github.com/curityio/react-native-haapi-example> for example implementation in javascript which is mostly driven by events.

## Development

This module cannot be compiled as it is, instead add a file system dependency to the example application and open that workspace. See the [example repository](https://github.com/curityio/react-native-haapi-example) for instructions.

## Known limitations

- Registration steps are not yet fully supported
- External Browser flow not yet supported
