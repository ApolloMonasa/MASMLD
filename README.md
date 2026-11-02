# MASMLD

<p align="center"><img src="docs/branding/masmld-logo.svg" width="128" alt="MASMLD blue and purple modular cube logo" /></p>

[下载发布包](https://github.com/ApolloMonasa/MASMLD/releases/latest) · [问题反馈](https://github.com/ApolloMonasa/MASMLD/issues) · [MIT 许可证](LICENSE)

Marketplace 0.7.0 已于 2026-11-03 提交审核，尚未公开：[Core](https://plugins.jetbrains.com/plugin/35737-masmld-core) · [RISC-V](https://plugins.jetbrains.com/plugin/35738-masmld-risc-v) · [GNU ld](https://plugins.jetbrains.com/plugin/35739-masmld-gnu-linker-script)。审核期间可使用 GitHub Release 安装包。

面向 IntelliJ Platform 的模块化汇编语言插件，首批优先验证 RustRover。

当前公开版本为 0.7.0：Core、RISC-V 和 GNU ld 是三个独立插件。汇编与链接脚本均提供基础高亮、容错 PSI 和离线 Hover / Quick Documentation；GNU ld 模块只依赖 Core，不需要安装或选择 RISC-V。

## 构建与运行

基线：RustRover 2026.2.5（262.11879.102）、JDK 25、Gradle Wrapper 9.4.0、Kotlin 2.4.20、IntelliJ Platform Gradle Plugin 2.19.0。当前最低平台构建号为 262，未设置兼容上限；未来版本和其他 IDE 不因此视为已验证。实际验证范围见 [验证记录](docs/validation.md)。CLion 亦已做二进制兼容检查，具体范围见验证记录。

```bash
# 无本地 IDE 时，Gradle 会下载指定版本的 RustRover。
./gradlew :plugins:core:buildPlugin :plugins:arch-riscv:buildPlugin :plugins:linker-script:buildPlugin

# 可使用本地安装，避免下载 IDE。路径应指向包含 product-info.json 的目录。
./gradlew :plugins:arch-riscv:runIde -PlocalIdePath=/absolute/path/to/rustrover

# 仅运行核心插件，用于验证无架构模块的情况。
./gradlew :plugins:core:runIde -PlocalIdePath=/absolute/path/to/rustrover
```

网络受限时，可通过 `-PpluginRepository=<Maven URL>` 和 `-PmavenRepository=<Maven URL>` 指定可信镜像。代理使用 Gradle 标准 JVM 参数配置，不在仓库保存个人网络设置。

构建产物分别位于 `plugins/core/build/distributions/`、`plugins/arch-riscv/build/distributions/` 和 `plugins/linker-script/build/distributions/`。在 RustRover 的 Settings → Plugins → Install Plugin from Disk 中先安装 Core，再按需安装 RISC-V 或 GNU Linker Script；安装、启用或禁用模块后需要重启 IDE。开发沙箱与日常 IDE 配置隔离。

## 验收操作

1. 运行核心沙箱，打开项目。在 Tools → MASMLD: Show Architecture Status 查看状态：架构列表为空，生效语言为 Generic assembly。
2. 运行 RISC-V 沙箱，核心插件会作为依赖一并加入。状态窗口应列出 RISC-V、RV32I 和 RV64I。
3. 在 Settings → Languages & Frameworks → MASMLD Assembly 中选择 RISC-V 并应用，然后打开 `examples/riscv/hello.s`。
4. 打开 `examples/riscv/highlighting.S`，查看指令、伪指令、寄存器、标签、指示符、字符串与注释的颜色。在 Settings → Editor → Color Scheme → MASMLD RISC-V 调整配色。RV32I/RV64I 尚无 profile 选择和合法性校验，Hover 请按下方说明体验。
5. 重启沙箱，确认项目设置被保留。禁用 RISC-V 并重启后，应回退通用汇编，且保留原选择，以便重新安装模块后恢复。

`.s`、`.S`、`.asm` 统一由核心提供文件类型；若已被其他插件关联，需要通过 IDE 的文件类型设置手动选择 MASMLD Assembly。本插件不会覆盖用户已有的关联。

## 目录与计划

- `plugins/core`：扩展契约、注册表、通用文件入口、项目设置和状态操作。
- `plugins/arch-riscv`：RISC-V Lexer、容错 Parser、语句 PSI、上下文分类、高亮及颜色设置。
- `docs/riscv-syntax.md`：语法覆盖范围、边界和资料来源。
- `docs/hover.md`：Hover 使用方式、覆盖范围与资料维护约定。
- `plugins/linker-script`：独立 GNU ld 语言与离线资料，不依赖 RISC-V。
- `docs/linker-script.md`：链接脚本的覆盖范围与使用方式。
- `docs/architecture.md`：依赖边界及扩展约定。
- `docs/plan.md`：后续分阶段交付。
- `docs/validation.md`：实际执行的检查与未覆盖项。

## 隔离启动检查

```bash
python3 tools/smoke/run.py --ide /absolute/path/to/rustrover
```

脚本从构建 ZIP 安装插件，编译仅用于测试的 ApplicationStarter，分别启动 core-only、core+riscv、core+ld 和全部插件组合的沙箱，检查扩展发现、设置项、文件类型、PSI 语言分发、文本保留及缺失模块回退。测试插件不进入交付 ZIP。

该脚本需要沙箱具备有效的 RustRover 许可；空配置可能在执行断言前退出并显示 `No valid license found`。这是未完成验证，不算测试通过。必要时可用对应 `build/smoke/<mode>/smoke.vmoptions` 启动沙箱的正常 IDE 完成许可激活，再用 `--reuse-config` 保留该测试配置重跑。请勿把日常 IDE 配置目录指定为测试目录。


## 自动化语法测试

```bash
./gradlew :plugins:arch-riscv:test -PlocalIdePath=/absolute/path/to/rustrover
```

这些 JUnit 测试直接使用平台 Lexer 和轻量语法树，不启动需要许可证的完整 IDE。覆盖增量重启、文本保留、未完成语句恢复、上下文误着色和颜色键映射。它们不替代完整编辑器中的 Annotator、语言切换和主题视觉验收。


## Hover 与快速文档

在项目中选择 RISC-V 架构后，将鼠标移到指令或寄存器名称上，或使用 View → Quick Documentation（快捷键由当前 Keymap 决定）。普通鼠标悬停弹窗需在 Settings 中搜索并启用 **Show quick documentation on hover**。两种入口使用同一份离线说明。

可以直接打开 rCore 的 `os/src/entry.asm`：悬停 `la`、`sp`、`call`、`.section`、`.globl`、`.space` 查看中文说明。`boot_stack_top` 等标签可通过 Ctrl+点击或 Ctrl+B 跳转；`call rust_main` 等外部名字会交给宿主 IDE 的源码符号索引查询。用户符号不显示 ISA 说明。

本版本提供 52 条真实指令、29 条伪指令、35 条常用汇编指示符，以及 65 个寄存器名称（32 个数字名称与 33 个 ABI 别名，包含 fp）。每条含类别、语法、用途、注意事项、示例和来源。RV64 专属项会注明适用范围，但尚无 RV32/RV64 模式过滤。

升级后需退出当前开发沙箱，再重新执行 `runIde` 加载 0.7.0；若通过 ZIP 安装，则 Core 与已安装的可选插件一并升级并重启。不要同时运行针对同一沙箱目录的两个 IDE 进程。


## GNU ld 链接脚本

同时体验汇编与 `.ld`，请使用组合沙箱入口（旧的 `arch-riscv:runIde` 只带 Core 与 RISC-V）：

```bash
./gradlew :plugins:core:runIdeAll -PlocalIdePath=/absolute/path/to/rustrover
```

只体验独立链接脚本模块，可验证无需 RISC-V：

```bash
./gradlew :plugins:linker-script:runIde -PlocalIdePath=/absolute/path/to/rustrover
```

打开 `examples/linker/memory.ld` 或 rCore 的 `os/src/linker.ld`。`.ld` 不需要在 MASMLD Assembly 设置中选择架构；汇编文件仍按项目架构设置分发。悬停 `SECTIONS`、`MEMORY`、`ENTRY`、`ALIGN`、`ORIGIN` 等名称可查看说明。颜色设置入口为 Editor → Color Scheme → MASMLD GNU ld。

若 `.ld` 已关联到其他插件或文本类型，使用 Associate with File Type 选择 **MASMLD Linker Script**。当前只自动注册 `.ld`；`.lds` 或 `.ld.in` 可按需手动关联，不执行模板展开或预处理。

```bash
./gradlew :plugins:linker-script:test :plugins:arch-riscv:test -PlocalIdePath=/absolute/path/to/rustrover
# 仅验证 Core + GNU ld 的完整 IDE 行为（需该测试沙箱具备有效许可）
python3 tools/smoke/run.py --ide /absolute/path/to/rustrover --mode core-ld --reuse-config
```

组合沙箱由 Gradle 测试配置组装，不改变各插件发布包之间的依赖关系。完整行为检查尚需有效测试环境，不把沙箱目录准备成功当作 UI 验收通过。


## 验证与本地安装包

```bash
# 三个本地插件一起验证，Core 依赖从本地解析，不访问 Marketplace
./gradlew verifyAllPlugins -PlocalIdePath=/absolute/path/to/rustrover
# 自动测试、结构检查、二进制验证、元数据审计和预发布打包
./gradlew releaseBundle -PlocalIdePath=/absolute/path/to/rustrover
```

组合包位于 `build/releases/masmld-0.7.0.zip`，内含三个独立插件 ZIP、安装说明、SHA256SUMS、manifest.json 及测试/兼容性证据。外层 ZIP 是交付容器；解压后将内部插件 ZIP 逐个通过 Install Plugin from Disk 安装，先装 Core，再按需装 RISC-V 和 GNU ld。Core 和可选模块使用同一版本。

0.6 开发预览的 75 项自动化测试通过；三个插件分别对 RustRover 262.11879.102 和 CLion 262.11879.106 做离线 Plugin Verifier 检查，六组均为 Compatible。用户已确认 0.6 开发预览的真实 IDE 功能及安装、升级、模块组合验收完成。0.7 新增宏导航单独执行自动化测试；历史隔离 smoke 的许可阻塞保留在验证记录中。

Core 对无效元数据、重复架构 ID、重复语言和重复 profile ID 做隔离处理，避免悄悄选中冲突模块。`tools/smoke` 中的示例架构只属于测试辅助插件，不进入发布包。

可使用 `python3 tools/smoke/run.py --ide /absolute/path/to/rustrover --mode extension --reuse-config` 验证新增扩展路径；测试沙箱需具备有效许可。更多约定见 `docs/extension-contract.md`，验证边界见 `docs/validation.md`。该构建命令只生成本地包；公开下载与商店状态见 GitHub Release 和发布记录。


## 文件图标与符号跳转

`.asm/.s/.S` 使用橙色 A 文档图标，`.ld` 使用紫色链接文档图标，均提供深浅主题版本。升级 Core 和已安装的可选模块后重启生效；文件需关联到相应 MASMLD 文件类型。

在 RISC-V 汇编中使用 Ctrl+点击或 Navigate → Declaration or Usages（Ctrl+B；以当前 Keymap 为准）。支持同文件标签、数字局部标签 `1f/1b`、常量声明，以及项目中其他汇编文件导出的名字。未找到汇编定义时，会查询宿主 IDE 的源码符号：RustRover 的 Rust 索引、CLion 的 C/C++ 索引等。不强依赖这些语言插件；没有提供者或索引不可用时保持未解析，不尝试加载缺失语言类。

跨语言查询按源码名字匹配，不执行最终链接解析，不反解 `_Z...` 等 C++ 修饰名，也不解析 `export_name`/链接器别名。多个候选由 IDE 提供选择。查看 `docs/navigation.md` 了解边界。

```bash
# 用 RustRover SDK 构建，并额外校验本机 CLion；路径均为安装根目录
./gradlew releaseBundle -PlocalIdePath=/absolute/path/to/rustrover \
  -PadditionalVerificationIdePath=/absolute/path/to/clion
```

## 宏定义跳转

支持在同一 RISC-V 汇编文件中，从宏调用名称或 `.purgem` 名称跳转到当前有效的 `.macro` 定义；支持已知宏体内的调用和递归调用。

```asm
.macro save reg:req
    addi sp, sp, -16
    sd \reg, 0(sp)
.endm
save ra                       # Ctrl+点击 save 跳到 .macro save
```

宏采用定义先于调用的文本顺序，`.purgem` 后需重新定义才恢复解析。宏与标签使用不同名字空间；宏参数和宏体标签不会作为全局标签。当前不展开 `.include`、条件汇编或预处理，不解析参数拼接/动态生成的宏名或展开时才定义的嵌套宏。见 [导航说明](docs/navigation.md)。

## 许可证与发布

插件实现与原创资料采用 MIT，Copyright 2026 Yuliang Xiao (ApolloMonasa)。独立测试样本保留其上游许可，见 [第三方声明](THIRD_PARTY_NOTICES.md)。版本变更见 [CHANGELOG.md](CHANGELOG.md)，商店资料见 [docs/marketplace.md](docs/marketplace.md)。GitHub 下载发布与 Marketplace 审核是独立流程。
