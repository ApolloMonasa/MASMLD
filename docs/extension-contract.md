# 架构扩展契约与组合验证

0.8.0-rc.1。Core 与可选插件按相同版本安装，遵循 SemVer；0.x 公共 API 仍处开发期。新增的 ArchitectureOptionsValidator 为可选能力，原 provider 无需实现。完整开发说明与独立 arch-example 见 [developer-api.md](developer-api.md)。下方保留基础注册及组合验证契约。

## 接入方式

独立插件只声明对 `com.intellij.modules.platform` 与 `dev.masmld.core` 的依赖。实现 Core 的 ArchitectureProvider，并注册 `dev.masmld.core.architecture` Extension Point。为新的语言注册 ParserDefinition；若需词法高亮或文档，再注册对应的公共平台扩展。Core 无需加入架构 ID 分支。

- id 使用 `[a-z][a-z0-9._-]*`，一旦写入项目设置不得随意更名。
- displayName 必须非空。
- language 必须是 MASMLD Assembly 的独立 dialect；不能直接返回通用语言，也不能让多个架构共用同一个语言 ID。
- profiles 可以为空；有值时其 ID 符合相同格式、名称非空，且在该架构内唯一。
- 元数据 getter 应轻量、稳定，不执行 I/O。扩展实例不跨查询缓存，验证后使用独立元数据快照。

重复架构 ID 或语言 ID 的注册全部排除，不依据加载先后选取一个。无效元数据、普通读取异常和链接错误使相关提供者被忽略并记录日志，不删除其他有效提供者。平台和协程取消异常继续向上传播。架构未加载时已有项目选择保留，语言回退通用汇编。

当前插件要求重启后启用/禁用；没有因 Verifier 的动态加载建议就宣称已支持完整动态卸载。

## 示例扩展与组合

`tools/smoke/SampleArchitectureProvider.java` 和 SampleParserDefinition 展示只依赖 Core 的第三方风格扩展，ID 为 smoke.example。只有 smoke 的 extension 模式注册它。它仅用于验证扩展发现和语言分发，不实现真实 CPU 指令集，不进入任何交付插件。

完整 IDE smoke 组合：core-only、core-riscv、core-ld、all、extension。当前五种均已尝试，但独立配置中无有效许可证，退出码 7，断言没有执行。示例扩展的注册尚不能据此宣称完成运行验收。

## 分层验证

- Core 的 12 项单元测试：新增提供者、缺失模块、重复 ID/语言、无效字段/profile、异常隔离、快照独立性和取消传播。
- RISC-V 28 项、GNU ld 19 项测试继续运行，共 59 项。
- 三个插件结构与项目配置检查。
- `verifyAllPlugins` 将三个 ZIP 写入一个本地待验列表，使用固定 Plugin Verifier 1.411 与本机目标 IDE 离线校验。Core 可直接作为其他两个待验插件的依赖解析，无需 Marketplace 上已有版本。
- 校验门槛包括兼容性问题、内部 API、不正确的 override-only API 和无效插件报告；不通过添加忽略项或外部类前缀屏蔽结果。

初次在线尝试曾在依赖下载阶段失败；改用官方支持的离线模式后，0.5.0-dev 三个插件对 RustRover 262.11879.102 均为 Compatible。IDE 布局中一些未使用模块路径的警告保留在日志中，没有据此忽略插件兼容性问题。

## 交付包

`releaseBundle` 依赖三个模块的测试、结构/配置检查及 verifyAllPlugins。随后审计插件 ID、版本、依赖与包内命名空间，防止可选模块或测试代码被错误打进 Core。交付容器包含独立安装 ZIP、SHA-256、JUnit XML、Verifier 文本报告和验证说明。

该任务不签名、不发布，不执行完整 GUI 验收。外层组合 ZIP 不是单个 IntelliJ 插件，需要解压后按 INSTALL.md 安装内部 ZIP。后续版本仍需继续验证实际高亮刷新、Hover、指针恢复、模块缺失后重启，以及用户文件关联冲突。
