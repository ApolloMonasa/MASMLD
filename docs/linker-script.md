# GNU ld 独立模块

模块：`plugins/linker-script`，插件 ID：`dev.masmld.linker`，版本：0.6.0-dev。依赖 Core 与 IntelliJ 公共平台，不依赖 RISC-V、Rust、Java 或 C/C++ 插件。自动关联 `.ld`，使用独立语言 ID `MASMLD GNU ld`。

## 首版能力

- 独立 Lexer：C 风格块注释、字符串/引号名称、整数常量及 K/M 后缀、符号名、位置计数器、标点与运算符。
- 保留 GNU ld 的连字符命名：`A-B` 是一个符号，`A - B` 才以减号分隔。`.text.*` 等匹配模式保留原文本，通配符按运算符/匹配符着色。
- 命令与属性、内建函数、节模式/点号名称、位置计数器、数字、字符串和注释可分别配置颜色。
- 容错块结构 PSI：保留大括号层次与行/分号分隔的子句；接受未知或未完成内容，不把它当作已通过 GNU ld 语法校验。
- 48 个离线文档条目（31 个命令/属性条目、17 个内建函数/值条目）。ORIGIN/LENGTH 的区域属性与函数形式分别说明。
- Hover 与 Quick Documentation 共用 Core 的安全 HTML 渲染器；来源链接和查阅版本在条目中显示。

词法高亮对支持的保留名称着色，无需向后查看可变的括号或空白，以保证增量重启不依赖已缓存 token 后方的内容。文档查询再检查使用形式：函数需跟随括号，块命令需跟随大括号；引号内名称、注释、字符串及普通符号不会因此成为内建文档目标。它仍是有限的语法辅助，不执行完整名称解析。

## 使用与验收

- 仅安装 Core + GNU Linker Script 即可使用，不需要选择汇编架构。
- 仅 `.ld` 的开发入口：`:plugins:linker-script:runIde`。
- 同时安装三个插件的组合开发入口：`:plugins:core:runIdeAll`。这是单独的开发沙箱配置，不向任何发布插件增加反向依赖。
- 打开 `examples/linker/memory.ld`，查看 MEMORY/SECTIONS、区域属性、地址与长度、KEEP 和 /DISCARD/。测试还固定使用 rCore `os/src/linker.ld` 的样本。
- 高亮不出现时，先确认文件类型为 MASMLD Linker Script；已有文件类型关联不被插件强制覆盖。
- 普通 Hover 遵循 IDE 的 Show quick documentation on hover 设置，也可使用 View → Quick Documentation。

## 明确边界

不调用链接器、不执行 INCLUDE、不读取外部输入文件、不求值表达式、不计算地址或区域剩余空间、不修改构建系统，不配置 CPU 内存权限。示例只展示编辑器能力，不代表可直接用于任意目标的最终链接配置。

当前不实现完整 GNU ld 语法、VERSION 脚本、跨文件符号导航、补全、内存布局诊断、模板展开或特定厂商脚本方言。小写区域属性缩写 org/o/len/l、完整文件通配符规则及所有保留名称尚未完整覆盖。带 K/M 或基数后缀的输入只做词法着色，不验证数值范围或后缀组合是否合法。

`#` 行作为整体显示，不展开条件编译；这不表示所有预处理输入都可以直接传给 GNU ld。字符串在未闭合换行处恢复；不实现 GNU ld 的所有引号与文件路径规则。点号开头的符号与节模式使用同一颜色，不宣称两者均已完成语义解析。

## 资料维护与验证

资料位于 `src/main/resources/catalog/ld/entries.tsv`，中文摘要与示例由项目编写；以 GNU ld 官方手册为事实参考，未复制上游手册正文。每条带直接来源链接、2026-11-02 查阅标记和内容声明。未覆盖条目不生成推测性文档。

[GNU ld Scripts](https://sourceware.org/binutils/docs/ld/Scripts.html)、[MEMORY](https://sourceware.org/binutils/docs/ld/MEMORY.html)、[SECTIONS](https://sourceware.org/binutils/docs/ld/SECTIONS.html)、[Builtin Functions](https://sourceware.org/binutils/docs/ld/Builtin-Functions.html) 是主要参考入口。

本模块 19 项自动化测试覆盖原始/高亮 Lexer 的逐 token 重启、截断块注释、300 组固定种子的随机不完整输入、rCore 脚本轻量树、块恢复、所有资料条目查表和类路径独立性。完整 IDE 中的文件关联冲突、弹窗、编辑后指针恢复和三插件共存仍需运行验收，不能由这些测试替代。
