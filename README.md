# MASMLD

<p align="center"><img src="docs/branding/masmld-logo.svg" width="128" alt="MASMLD blue and purple modular cube logo" /></p>

面向 IntelliJ Platform 的模块化汇编与 GNU ld 插件，优先 RustRover，兼容性检查覆盖本机 RustRover 和 CLion。三个独立插件：Core、RISC-V、GNU Linker Script。

[公开下载](https://github.com/ApolloMonasa/MASMLD/releases) · [问题反馈](https://github.com/ApolloMonasa/MASMLD/issues) · [MIT](LICENSE)

当前候选版本：**0.8.0-rc.2**，采用 SemVer 2.0.0；此前公开版本为 0.7.0。本候选版包含新增开发者编辑能力和近期修复。0.7 商店条目：[Core](https://plugins.jetbrains.com/plugin/35737-masmld-core)、[RISC-V](https://plugins.jetbrains.com/plugin/35738-masmld-risc-v)、[GNU ld](https://plugins.jetbrains.com/plugin/35739-masmld-gnu-linker-script)，状态以商店页面为准。

## 功能

| 模块 | 能力 |
| --- | --- |
| Core | `.asm/.s/.S` 文件类型与图标；项目、文件、目录和 glob 架构设置；profile/方言/目标选项；include 搜索路径；可选架构扩展契约与独立示例模块 |
| RISC-V | GNU 汇编高亮、离线中文文档；指令/寄存器/符号/宏补全和参数提示；RV32/RV64 与 M/A/F/D/C、Zicsr、Zifencei 目标过滤及基础诊断/快速修复；包含图中的标签/常量/宏导航；项目导出符号索引；Find Usages、安全重命名、结构视图、折叠和保守格式化 |
| GNU ld | `.ld` 独立图标、高亮、48 项离线说明；符号、MEMORY、输出节与 REGION_ALIAS 的补全/导航/引用和重命名；INCLUDE；常量表达式、区域重叠及明确布局问题的弱提示；结构视图、折叠和格式化 |
| 跨语言 | 宿主 Rust/C/C++ 源码索引，常见导出名和链接器别名；后台工具反解修饰名；可选 ELF/目标文件符号索引和调试信息源码行导航 |

RISC-V 当前目录覆盖 465 个名称（含原有目录、新扩展拼写、浮点寄存器和 CSR 伪指令），不宣称覆盖整个 RISC-V ISA。未知动态链接期数值保持未知，不模拟完整链接过程。

## 安装与使用

先安装 Core ZIP，再按需安装相同版本的 RISC-V 和 GNU ld ZIP，重启 IDE。`.ld` 不需要 RISC-V；汇编文件在 Settings → Languages & Frameworks → MASMLD Assembly 选择架构。原有文件关联由用户决定，如冲突可手动选择 MASMLD Assembly / MASMLD Linker Script。

Hover 需启用 **Show quick documentation on hover**，也可使用 Quick Documentation。补全、参数提示、跳转、查找引用、重命名和格式化使用 IDE 标准操作，快捷键依 Keymap 而异。

包含文件不强制占用 `.inc` 扩展名；即使是纯文本也可按位置导航。需要在包含文件内部获得完整高亮与重构时，按实际语言关联为 MASMLD Assembly 或 MASMLD Linker Script，避免混合项目中误判包含文件的语言。

详细配置、示例和边界见 [开发者编辑能力](docs/developer-workflows.md)。构建产物符号导航是可选功能：配置 Binary Symbols 后运行 Tools → MASMLD: Refresh Binary Symbols；需要本机工具和调试信息，不自动安装工具或执行项目构建。

## 构建、测试与开发窗口

基线：JDK 25、Gradle Wrapper 9.4.0、Kotlin 2.4.20、IntelliJ Platform Gradle Plugin 2.19.0。最低平台 build 262（2026.2 系列）；没有设置兼容上限不代表未来版本均经过验证。设计无平台专属原生库，当前执行环境为 Linux；未完成 Windows/macOS 实测矩阵。

```bash
# 路径必须是含 product-info.json 的 IDE 安装根目录，不能使用 bin/rustrover 可执行文件
./gradlew releaseBundle -PlocalIdePath=/absolute/path/to/rustrover \
  -PadditionalVerificationIdePath=/absolute/path/to/clion

# 三插件组合开发窗口；默认单模块 runIde 仅安装该模块及其依赖
./gradlew :plugins:core:runIdeAll -PlocalIdePath=/absolute/path/to/rustrover

# 单独测试
./gradlew :plugins:core:test :plugins:arch-riscv:test :plugins:linker-script:test \
  -PlocalIdePath=/absolute/path/to/rustrover
```

本地交付容器为 `build/releases/masmld-0.8.0-rc.2.zip`，包含三个独立可安装 ZIP、说明、校验和和验证证据。外层 ZIP 须解压后安装内部插件 ZIP。网络受限时使用 `-PpluginRepository=<Maven URL>`、`-PmavenRepository=<Maven URL>` 配置可信镜像，不保存个人代理或 SDK 绝对路径。

测试包含纯语义检查及 IntelliJ Platform unit-test 模式下的真实 PSI、索引和编辑器操作，无需单独 IDE 许可；这不等同于新版本的图形界面/主题验收。完整历史记录与本轮结果见 [验证记录](docs/validation.md)。

可额外执行实际工具链检查：

```bash
python3 tools/smoke/binary.py         # C，及可用的 C++/Rust 编译器；nm/symbolizer/demangler
python3 tools/smoke/riscv_catalog.py  # 用 LLVM MC 汇编新 ISA 文档的实际 RV32/RV64 示例
```

## 扩展和覆盖边界

[扩展 API 与可编译示例](docs/developer-api.md) · [扩展契约](docs/extension-contract.md) · [架构设计](docs/architecture.md) · [本轮需求覆盖表](docs/development-matrix.md) · [变更记录](CHANGELOG.md)

当前 GNU 方言不执行 C 预处理、条件汇编求值、参数拼接或完整宏实例化；ISA 检查覆盖明确支持的基本形式，复杂编码、特权规则及最终链接结果由实际工具链决定。x86 和 ARM 模块尚未实现，示例模块不是一个真实 ISA。

插件实现与原创资料采用 MIT，Copyright 2026 Yuliang Xiao (ApolloMonasa)。独立测试样本保留上游许可，见 [第三方声明](THIRD_PARTY_NOTICES.md)。
