# UniversalArmory

TaCZ 军械库模组，按本地 SighsTemple 框架组织为共享源码树与独立版本工程。

```text
common/
  src/main/java/                  共享业务、Minecraft 基线实现与 SPI
  src/main/resources/             共享 assets/data
  build.gradle                    Forge 1.20.1 类路径下的独立编译
 targets/forge-1.20.1/
  src/main/java/                  Forge 入口、注册、事件、配置和网络传输
  src/main/resources/             Forge metadata、Mixin 与 pack metadata
  libs/                           TaCZ 本地生产 jar
  dependencies.gradle             本版本的 ApricityUI/KubeJS/Rhino 等依赖
  gradle.properties               本版本的 Minecraft/Forge 参数
  gradlew.bat / gradle/            独立 wrapper
 gradle/target-conventions/        本地 jar 重映射与发布约定
 scripts/                         SighsTemple target 发现与构建脚本
 build.gradle                     target 聚合入口
 gradle.properties                共享 mod_* 信息
 SighsTemple/                     用户提供的框架原件，不参与模组构建
```

## 平台边界

`Universalarmory` 只保留公共模组标识。`ArmoryPlatform` 提供注册结果与配置持久化能力，`ArmoryNetworkBridge` 提供网络传输能力，均由 target 入口安装。Forge DeferredRegister、FML 事件和 SimpleChannel 不再藏在共享入口里。

网络操作与分页业务仍在 common：`ArmoryRequestHandler` 负责权限校验、库存操作和快照组装；Forge target 负责数据包注册、编解码和主线程投递。

common 不是 target 的项目依赖。IDE 通过 composite build 导入，target 通过 `prepareCommonSources` 共享编译源码。需要 target 替换的 common 文件在 Sync 任务中登记 exclude，当前没有同名覆盖实现。

## 构建与开发

当前已实现的 target 只有 **Forge 1.20.1 / Forge 47.3.0**。SighsTemple 中其他加载器与版本的模板不代表本模组已移植。

使用 JDK 21 运行 Gradle（源码编译为 Java 17）：

```powershell
.\gradlew.bat -p targets/forge-1.20.1 clean build
.\gradlew.bat -p common clean build
.\gradlew.bat -Ptarget=forge-1.20.1 build
```

IDE 直接打开 `targets/forge-1.20.1/`。生产 jar 在 `targets/forge-1.20.1/build/libs/`，不要分发 `build/devlibs/` 或 common 的独立编译 jar。

AUI 使用 `com.sighs:ApricityUI-forge-1.20.1:1.3.0`。本地 TaCZ 生产 jar 通过 `modImplementation` 自动重映射，不嵌入发布 jar。所有传递模组依赖均需显式提供。

运行验证尚未全部完成；构建成功和客户端页面首帧不等于专用服务端、多人存取和全部界面流程验收。
