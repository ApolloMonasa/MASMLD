# Hover / Quick Documentation：0.8 当前说明

0.8.0-dev 的新增能力和限制见 [开发者编辑能力](developer-workflows.md) 与 [需求覆盖表](development-matrix.md)。RISC-V 目录现有 465 个名称；GNU ld 增加了符号、包含图与常量/布局分析。Core 和已安装的可选模块始终使用同一版本。

以下保留首版实现与资料来源记录，其中旧版本号及“尚未支持”的表述仅适用于历史阶段；当前能力以上述文档为准。

---

# Hover / Quick Documentation

## 使用

1. Core 与 RISC-V 插件升级为相同的 0.6.0-dev，并重启沙箱。
2. 在当前项目的 MASMLD Assembly 设置中选择 RISC-V。
3. 将光标放在名称内部，使用 View → Quick Documentation；鼠标悬停由 IDE 的 Show quick documentation on hover 设置控制。
4. 在 `entry.asm` 中检查 `la`、`sp`、`call`、`.section`、`.globl`、`.space`。同一符号的 Hover 和 Quick Documentation 使用同一个渲染函数。

## 已覆盖

- 52 条 RV32I/RV64I 真实指令。
- 29 条常用整数伪指令。
- 35 条常用 GNU/RISC-V 汇编指示符。
- 32 个整数寄存器数字名称，33 个 ABI 别名（含 s0/fp），共 65 个可查询名称。

每个条目提供类别、操作数写法、中文用途、适用范围、示例和来源链接。真实指令不与伪指令混为一类。`li`、`la`、`call`、`tail` 的展开不被描述成固定机器编码，说明中列出常量、PIC、XLEN、重定位或链接松弛等影响因素。

资料随插件打包，查询不访问网络。点击来源链接才会由 IDE 打开网页。未覆盖条目不显示文档，不编造缺失说明。

## 实现与上下文

- Core 中的 DocumentationEntry/DocumentationSource 是架构无关的数据契约；DocumentationRenderer 转义所有纯文本，并只允许 HTTPS 来源链接。
- RISC-V 的 `catalog/riscv/entries.tsv`、`registers.tsv`、`sources.tsv` 分别维护条目、寄存器与来源元数据。资料按需从资源加载一次，不扫描项目文件。
- 查询使用与高亮相同的 RiscVClassifier。只在指令、伪指令、指示符或寄存器的准确 token 范围内返回说明；标签定义、跳转目标、指示符操作数、重定位中的符号、注释、字符串和空白不提供 ISA 说明。
- 同时注册 offset 与 PSI Documentation Target Provider，两者共用解析入口。全局扩展会先检查文件语言，不接管 Rust、其他语言或通用汇编文件。
- DocumentationTarget 的恢复指针使用 SmartPsiElementPointer，并在恢复时重新检查上下文。被删除的元素、从指令改成标签的元素不应恢复成旧文档目标。

当前结构只提供静态 ISA/汇编器资料：不做用户标签文档、补全或指令合法性诊断；符号导航见 `navigation.md`；不依据 RV32/RV64 profile 隐藏条目。已有高亮但未纳入这 35 个常用条目的指示符可能无 Hover。

## 来源、版本与内容声明

来源链接和查阅版本随每条资料打包，详见 `sources.tsv`。

- RV32I/RV64I：RISC-V ISA v20260120，基础整数章节版本 2.1。
- 调用约定：RISC-V ABIs Specification 2.0-rc0 / 2026-10-27（只引用稳定的整数寄存器约定；上游声明 CC BY 4.0）。
- 伪指令：RISC-V Assembly Programmer's Manual，2026-11-02 查阅在线版本。
- 汇编指示符：GNU as 手册的对应页面，2026-11-02 查阅在线版本。

中文摘要、示例和渲染代码由本项目编写；未将上游手册正文或汇编器代码复制进插件。上游许可声明仅描述参考资料，不据此给本项目代码指定许可证。在线来源可能更新，离线条目不会随网页变化自动改变。

## 测试边界

自动化测试覆盖 rCore `entry.asm` 固定样本、所有 181 个目录项的上下文可达性、别名对应、伪指令语义提示、符号误触发排除、HTML 转义及不安全链接排除。

实际 IDE 内的弹窗触发、扩展点调度和编辑后的指针恢复另由 smoke 脚本断言和人工验收覆盖；离线查表测试不等同于已经完成这部分验证。
