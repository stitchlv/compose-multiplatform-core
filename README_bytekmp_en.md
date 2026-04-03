<p align="center">
    <img alt="ByteKMP" src="bytekmp/image/bytekmp.png" />
</p>

English | [简体中文](README_bytekmp_zh.md)

ByteKMP is a cross-platform development framework built on Kotlin Multiplatform (KMP) and Compose Multiplatform (CMP). It aims to enable efficient reuse of core code across Android, iOS, and HarmonyOS, while preserving native capabilities and native user experience on each platform, thereby improving development efficiency and reducing maintenance costs.

# Repository Information
Due to the different Kotlin versions used by iOS and HarmonyOS (iOS 2.1, HarmonyOS 2.0), the versions have not been unified yet. Therefore, iOS and HarmonyOS use different branches. The branch information is as follows:
- **HarmonyOS**: platform/harmonyos
- **iOS**: platform/ios

Related Repository Information
- [ByteKMP-compose-multiplatform-core](https://github.com/ByteKMP/compose-multiplatform-core)
- [ByteKMP-compose-multiplatform](https://github.com/ByteKMP/compose-multiplatform)
- [ByteKMP-sample](https://github.com/ByteKMP/ByteKMPSample)

# Build and Publish
### 1. Environment Configuration
- Xcode 15.4
- JDK 17
### 2. Configure Maven Information
Create a new file `local.build.properties` in the root directory and fill in Maven information:
```properties
custom_maven_publish_url=xxx
custom_maven_publish_username=xxx
custom_maven_publish_password=xxx
```
### 3. Modify Version & Publish Artifacts
Modify the version number in `gradle.properties`:
```properties
jetbrains.publication.version.CORE_BUNDLE=1.0.0
jetbrains.publication.version.COMPOSE=1.6.10
jetbrains.publication.version.LIFECYCLE=2.8.0
jetbrains.publication.version.NAVIGATION=2.7.7
jetbrains.publication.version.SAVEDSTATE=1.2.1
```
Execute `./gradlew :mpp:publishComposeJbToMavenRepo` to publish artifacts.

# License
This project is licensed under the [Apache-2.0 License](LICENSE.txt).