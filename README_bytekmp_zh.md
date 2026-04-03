<p align="center">
    <img alt="ByteKMP" src="bytekmp/image/bytekmp.png" />
</p>

[English](README_bytekmp_en.md) | 简体中文

ByteKMP 是基于 Kotlin Multiplatform(KMP) 与 Compose Multiplatform(CMP) 构建的跨平台开发框架，旨在保持各平台原生能力与用户体验的前提下，实现 Android、iOS、鸿蒙 等多端核心代码的高效复用，提升开发效率并降低维护成本。

# 仓库信息
由于 iOS 与 鸿蒙采用了不同 Kotlin 版本（iOS 2.1, 鸿蒙 2.0），当前还未完成版本统一，因此 iOS 与鸿蒙采用不同分支，分支信息如下
- **鸿蒙**: platform/harmonyos
- **iOS**: platform/ios

相关仓库信息
- [ByteKMP-compose-multiplatform-core](https://github.com/ByteKMP/compose-multiplatform-core)
- [ByteKMP-compose-multiplatform](https://github.com/ByteKMP/compose-multiplatform)
- [ByteKMP-sample](https://github.com/ByteKMP/ByteKMPSample)

# 编译发布
### 1. 环境配置
- Xcode 15.4
- JDK 17
### 2. 配置 maven 信息
在根目录新建文件`local.build.properties`并填写 maven 信息
```properties
custom_maven_publish_url=xxx
custom_maven_publish_username=xxx
custom_maven_publish_password=xxx
```
### 3. 修改版本 & 发布产物
在`gradle.properties`下修改版本号
```properties
jetbrains.publication.version.CORE_BUNDLE=1.0.0
jetbrains.publication.version.COMPOSE=1.6.10
jetbrains.publication.version.LIFECYCLE=2.8.0
jetbrains.publication.version.NAVIGATION=2.7.7
jetbrains.publication.version.SAVEDSTATE=1.2.1
```
执行`./gradlew :mpp:publishComposeJbToMavenRepo`发布产物
# License
本项目采用 [Apache-2.0 License](LICENSE.txt) 许可证开源