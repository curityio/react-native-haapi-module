## [0.5.0](https://github.com/curityio/react-native-haapi-module/compare/v0.4.13...v0.5.0) (2024-11-21)
Support for passkeys for both Android and iOS have been added. To make this possible, numerous action has been taken on the codebase.

### SDK version has been pinned to 4.1.x
Previous releases did not pin the version on iOS, and android was using 4.0. If you experiencing differences with this update, please refer to the SDK changelogs:
https://curity.io/docs/haapi-android-sdk/latest/CHANGELOG.html
https://curity.io/docs/haapi-ios-sdk/latest/CHANGELOG.html

### Multiple new events
* RegistrationStep
* WebAuthnAuthenticationStep
* WebAuthnUserCancelled
* WebAuthnAuthenticationFailed
* WebAuthnRegistrationFailed
* WebAuthnRegistrationFailedKeyRegistered
* HaapiLoading
* HaapiFinishedLoading
* LoggedOut
* UnknownRespons
## [0.4.13](https://github.com/curityio/react-native-haapi-module/compare/v0.4.12...v0.4.13) (2024-11-19)
Fix: The iOS module was dependant on the `start` method being called every time the module was loaded. This made it hard to keep an application state between app starts. This dependency is now removed, so that methods like `refreshToken` works without the `start` method` being called first.
