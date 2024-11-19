## [0.4.13](https://github.com/curityio/react-native-haapi-module/compare/v0.4.12...v0.4.13) (2024-11-19)
Fix: The iOS module was dependant on the `start` method being called every time the module was loaded. This made it hard to keep an application state between app starts. This dependency is now removed, so that methods like `refreshToken` works without the `start` method` being called first.
    
Fix: The podspec is updated to pin a specific HAAPI SDK version.

