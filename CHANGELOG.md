# [0.6.0](https://github.com/curityio/react-native-haapi-module/compare/v0.5.0...v0.6.0) (2024-11-25)


### Bug Fixes

* Custom http headers and query parameters for ios ([#28](https://github.com/curityio/react-native-haapi-module/issues/28)) ([6a2344c](https://github.com/curityio/react-native-haapi-module/commit/6a2344cbf57e62984eddbe173020a8091e3409cc))


### Features

* Update to latest SDK versions ([4c525d4](https://github.com/curityio/react-native-haapi-module/commit/4c525d4707897535ce1cb47d7ad177a0f0a0e85e))


# [0.5.0](https://github.com/curityio/react-native-haapi-module/compare/v0.4.12...v0.5.0) (2024-11-25)


### Features


* Support for passkeys for both Android and iOS have been added. To make this possible, numerous action has been taken on the codebase.

* SDK version has been pinned to 4.1.x* 
Previous releases did not pin the version on iOS, and android was using 4.0. If you are experiencing differences with this update, please refer to the SDK changelogs:
https://curity.io/docs/haapi-android-sdk/latest/CHANGELOG.html
https://curity.io/docs/haapi-ios-sdk/latest/CHANGELOG.html

* Multiple new events
Events concerning Webauthn/Passkeys and other lifecycle events has been added. See README for documentation.


## [0.4.13](https://github.com/curityio/react-native-haapi-module/compare/v0.4.12...v0.4.13) (2024-11-19)


### Bug Fixes


* The iOS module was dependent on the `start` method being called every time the module was loaded. This made it hard to keep an application state between app starts. This dependency is now removed, so that methods like `refreshToken` works without the `start` method` being called first.


