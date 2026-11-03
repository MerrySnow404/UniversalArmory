# UniversalArmory 架构约定

本项目按 SighsTemple 的 `common + targets/<loader>-<minecraft-version>` 结构维护。框架原件位于 `SighsTemple/`，不要把该目录当作本项目的发布 target。

- 根工程仅聚合独立 target；共享模组信息放根 `gradle.properties`。
- `common/` 是共享源码树，也以 Forge 1.20.1 基线类路径独立编译。target 通过 `includeBuild('../../common')` 导入 IDE，通过 `prepareCommonSources` 的 Sync 任务编译共享源码，不使用 `project(':common')`。
- 无确证版本差异的源码与资源默认放 common，可以引用 Minecraft 和 loader 类型。仅凭 import 平台类型不能拆分文件。
- 已确认加载器差异的入口、DeferredRegister 注册、FML 生命周期、配置持久化和 SimpleChannel 传输放 `targets/forge-1.20.1/`。common 的 `spi/` 定义平台能力，由 target 入口显式安装；不得按版本判断或反射选择平台实现。
- 网络业务处理与快照组装保留 `common/.../armory/ArmoryRequestHandler.java`，target 只处理加载器注册、传输、编解码和线程投递。
- 某个 target 替换 common 文件时，必须在该 target 的 `prepareCommonSources` 中登记 exclude；不得依赖同名 class 的覆盖顺序。
- 共享 assets/data 放 common；加载器 metadata、Mixin 配置、pack metadata 放 target。禁止共享和 target 同路径资源隐式覆盖。
- 版本、加载器范围放 target 的 `gradle.properties`；版本专属 Maven 依赖放 target 的 `dependencies.gradle`；本地 jar 放 target 的 `libs/`。
- `gradle/target-conventions/local-dependencies.gradle` 自动发现本地 jar，并通过 Legacy Forge 的 `modImplementation` 进行 SRG 到 official 重映射。不要改成裸 fileTree implementation，否则 TaCZ 的生产命名不能用于开发编译。
- 发布逻辑仅放 `gradle/target-conventions/publish.gradle`，不要执行发布任务，除非用户要求。
- 当前实现与支持矩阵只有 `forge-1.20.1`，不要把框架里的空 target 或未经构建的端口宣称为支持。

## 验证

使用 JDK 21 运行 Gradle，源码目标为 Java 17：

```powershell
.\gradlew.bat -p targets/forge-1.20.1 clean build
.\gradlew.bat -p common clean build
.\gradlew.bat -Ptarget=forge-1.20.1 build
```

涉及注册、网络、资源与客户端渲染时必须进行实际运行验证。检查最终 reobfuscated jar 的 metadata、共享类和 target 类。构建通过不等于运行测试通过。
