# 架构与扩展约定

## 发布与依赖

每个 `plugins/*` 子项目分别应用平台构建插件、生成独立 ZIP，不把架构实现打进 Core。RISC-V 通过 `localPlugin(project(":plugins:core"))` 获取编译与沙箱依赖，同时在 plugin.xml 声明必需依赖 `dev.masmld.core`。Core 仅依赖 `com.intellij.modules.platform`，不依赖 Rust、Java、C/C++ 插件。

可选表示用户可不安装架构插件；架构插件对 Core 的依赖是必需的。Core 不需要反向声明对每种架构的 optional dependency。

## 扩展契约

Core 暴露 `dev.masmld.core.architecture` Extension Point，接口为 `ArchitectureProvider`：

- `id`：稳定、全局唯一的架构 ID，写入项目设置后不可随意更名。
- `displayName`：设置和状态窗口中的名称。
- `language`：该架构注册的 IntelliJ Language，及其 ParserDefinition。
- `profiles`：能力元数据，当前不参与解析分发。

扩展实现由架构插件注册。Core 每次查询读取扩展列表，不长期保存扩展实例。预览版要求重启后加载/卸载模块；动态卸载时的 PSI、缓存和文件重解析验证留待后续。

扩展契约现为 0.1 开发接口，各插件按相同版本配套安装；阶段 5 已补充重复 ID、语言和元数据校验；契约见 `extension-contract.md`。稳定版前仍需固定跨版本兼容政策。

## 文件与语言分发

Core 注册唯一 AssemblyFileType，处理 `.s/.S/.asm`。项目默认选项为空，即通用汇编；不会依据扩展名推断 CPU 架构。LanguageSubstitutor 根据项目选中的 ID 查询架构扩展，返回具体语言；缺少该扩展时返回空值，使用通用语言。

通用语言使用占位解析器；RISC-V 已提供独立 Lexer、语句级容错 Parser/LightPsiParser 和 PSI。核心的 AssemblySyntaxHighlighterFactory 将公共文件入口转发到所选架构的高亮器；RISC-V 的词法高亮与上下文 Annotator 分层处理。未知/不完整语句不报告语法错误。详见 `riscv-syntax.md`。

项目设置持久化在 `.idea/masmld.xml`；更改后重新解析打开的文件。每文件和目录覆盖、自动检测、预处理 `.S` 的语义均未实现。

## 后续模块边界

- x86：自身处理 Intel/AT&T 方言差异。
- ARM：优先用 profile 与特性描述 AArch32、AArch64、Thumb 等差异，根据实际语法与发布需求再拆分。
- linker-script（已实现首版）：独立 GNU ld 语言、文件类型、Lexer、块结构解析器和文档接口，只依赖 Core 的公共文档模型与渲染，不依赖 RISC-V。
- 工具链适配：CMake、Cargo 集成放在可选适配层，不进入核心必需依赖。

指令、伪指令、汇编器指示符和寄存器分别建模；资料已以架构模块内 TSV 离线打包，记录来源、版本与内容声明。核心提供架构无关的 DocumentationEntry 与安全 HTML 渲染器；架构模块负责查表与上下文选择。Hover 与 Quick Documentation 采用公共 Documentation Target API，遵循 IDE 的悬停设置。


## 开发组合与发布隔离

Core 的 `intellijPlatformTesting.runIde` 注册 `runIdeAll`，仅向该任务的运行时沙箱安装 RISC-V 和 GNU ld。Core 的默认编译/运行依赖没有因此增加架构插件；各自 ZIP 依旧独立。`linker-script:runIde` 的沙箱仅包含 GNU ld 与 Core，可用于验证独立使用路径。

DocumentationKind 在阶段 4 增加 LINKER_COMMAND 与 BUILTIN_FUNCTION，不使用“汇编指示符”类别描述链接器命令。可选插件使用与 Core 配套的开发版本。
