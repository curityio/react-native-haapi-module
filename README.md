# react-native-haapi-module

## Getting started

`$ npm install @curity/react-native-haapi-module --save`

## Configuration

Parameter Name             | Platform | Required | Default     | Description
-------------------------- | -------- | -------- | ----------- | -----------------------------------------------------------------------------------------------------
`appRedirect`              | both     | false    | `app:start` | Redirect URI to use in OAuth requests. Needs to be registered in server config
`clientId`                 | both     | true     |             | The registered `client_id`
`baseUri`                  | both     | true     |             | Base URI of the server. Used for relative redirects.
`tokenEndpointUri`         | both     | true     |             | URI of the token endpoint.
`authorizationEndpointUri` | both     | true     |             | URI of the authorize endpoint.
`revocationEndpointUri`    | both     | true     |             | URI of the revocation endpoint.
`registrationEndpointUri`  | android  | false    |             | URI of the registration endpoint. Required if fallback registration should be used.
`fallback_template_id`     | android  | false    |             | Name of the template client to be used in fallback. Required if fallback registration should be used.
`registration_secret`      | android  | false    |             | Name of the template client to be used in fallback. Required if fallback registration should be used.
`validateTlsCertificate`   | both     | false    | true        | If the server TLS certificate should be validated. Set to `false` to accept self signed certificates.
`acrValues`                | both     | false    | `""`        | Space separated string to send in authorize request.
`scope`                    | both     | false    | `""`        | Space separated string of scopes to request.

## Usage

All functions of the module are async operations. The application may use events produced by the module to drive the authentication flow, or rely on reults return by promises.

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
    "acrValues": ""
}
try {
    HaapiModule.load(haapiConfiguration)
} catch (e) {
    console.error('Error in loading configuration', e);
}

export default HaapiModule;
```

## Start

After the module has been loaded, the `start()` function may be called. `start()` will setup the communication with HAAPI, perform attestation, and then start emitting events for the application to react on. Receiving events will allow the application to know more about the contents of the current state than if it were to receive the raw HaapiResponse.
The module will follow redirect responses automatically.

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

Event Name                 | Emitted when
-------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
AuthenticationStep         | An action is required by the user as part of authentication. See [Authentication Step](https://curity.io/docs/haapi-data-model/latest/authentication-step.html)
AuthenticationSelectorStep | An `AuthenticationStep` with the kind `authenticator-selector` is received. An authenticator selector screen should be be shown to the user.
ContinueSameStep           | A screen should be shown to the user, containing some information. The only required action by the user is to accept or in some cases cancel. [Continue Same Step](https://curity.io/docs/haapi-data-model/latest/continue-same-step.html)
PollingStep                | An authentication step that requires polling was received. May contain information for the user for how to proceed authentication out of band. [Polling Step](https://curity.io/docs/haapi-data-model/latest/polling-step.html)
PollingStepResult          | A poll result was received with the `status` `PENDING`. The application may show new information to the user and continue polling.
StopPolling                | A successful poll result was received. Application should stop polling, and the module will continue execution and may issue new events.
TokenResponse              | Authentication was successful, and the resulting token(s) was received. The payload of the event will contain `accessToken` and `scope`. May contain `refreshToken` and `idToken`
TokenResponseError         | Authentication was successful, but the token request returned an error.
SessionTimedOut            | The authentication process took too long, and timed out. The user will have to start over using `start()` method again.
IncorrectCredentials       | The user enter wrong credentials in an `AuthenticationStep`. Show an error to the user and allow them to try again. [Invalid Input Problem](https://curity.io/docs/haapi-data-model/latest/invalid-input-problem.html)
ProblemRepresentation      | The server returned an unexpected problem. [Problem](https://curity.io/docs/haapi-data-model/latest/problem.html)
HaapiError                 | An unexpected problem happened. Event will have members `error` and `error_description`

## Example implementation

See <https://github.com/curityio/react-native-haapi-example> for example implementation in javascript which is mostly driven by events.

## Known limitations

- Registration steps no yet supported
- External Browser flow not yet supported
- Webauthn/Passkeys not yet supported
