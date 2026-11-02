# RISC-V 语法高亮范围

阶段 2 面向 GNU 汇编风格，提供编辑器辅助，不代替汇编器或指令合法性检查。

## 已实现

- RV32I/RV64I 整数指令名称，常用整数伪指令，常见 GNU/RISC-V 汇编指示符。
- 整数寄存器 `x0..x31`、ABI 别名 `zero/ra/sp/gp/tp/t0..t6/s0..s11/a0..a7/fp`。
- 标签定义、数字局部标签及 `1f/1b` 引用、整数、GNU 前置单引号字符常量、字符串、表达式标点与宏参数。
- `#` 行注释、`/* ... */` 块注释；分号作为语句分隔符。
- 按语句位置识别指令与伪指令，按已支持指令的操作数位置识别寄存器。`add:` 是标签，`.globl a0`、`j a0`、`beq a0,a1,a2` 的目标是符号，不将其当作寄存器。
- 重定位表达式保留为符号表达式：`lw a0,%lo(a1)(sp)` 中仅 `a0` 和 `sp` 着寄存器色。
- 容错语句 PSI：未知指令、宏调用、未完成操作数均保留；未闭合字符串在物理换行恢复。`LightPsiParser` 与完整 AST 使用同一解析路径。
- Editor → Color Scheme → MASMLD RISC-V 中可设置颜色，默认继承 IDE 配色方案。

词法层由 SyntaxHighlighter 实现；上下文颜色由无索引依赖的 Annotator 实现。颜色设置页的预览使用与编辑器相同的颜色键。

## 明确边界

- `.s/.S/.asm` 使用相同的语法支持。`.S` 中 `#include/#if/#define` 等整行显示为注释，不执行预处理，不判断条件分支是否生效。
- 当前不解析 `.include`，不展开宏，仅解析汇编导出符号和宿主源码索引中的名字，不执行链接解析，不诊断寄存器/立即数合法性，不区分 RV32/RV64 指令可用性。
- 未覆盖的扩展指令、宏和指示符保留普通标识符颜色，不报告错误。M/A/F/D/C 等扩展的语义分类留待后续。
- `.insn` 的自定义操作数、GNU 浮点常量、跨行字符串/续行语义和完整汇编器表达式语法未完整支持。
- 当前按已支持指令的常见操作数形式分类，不承诺覆盖全部 GNU 别名和省略写法。
- Hover 已在阶段 3 接入，覆盖范围见 `hover.md`；标签跳转已接入，见 `navigation.md`；补全尚未实现。

## 文件/目录架构覆盖设计

本阶段沿用项目级选择，不增加尚无实际需求验证的设置 UI。后续覆盖规则为“精确文件 > 最长匹配目录 > 项目默认 > 通用汇编”；路径使用项目相对路径持久化，解析为 VirtualFile 后匹配，避免字符串前缀误匹配同名目录。架构模块缺失时保留原配置并回退通用语言；设置变更需同时触发 PSI 重解析与编辑器高亮更新。自动架构检测不覆盖用户的显式选择。

## 依据

实现为本项目编写；下列资料用于核对语法事实，未复制汇编器实现或手册正文：

- [RISC-V Assembly Programmer's Manual](https://github.com/riscv-non-isa/riscv-asm-manual/blob/main/src/asm-manual.adoc)：寄存器别名、指令和伪指令写法。
- [GNU as RISC-V directives](https://sourceware.org/binutils/docs/as/RISC_002dV_002dDirectives.html)。
- [GNU as character constants](https://sourceware.org/binutils/docs/as/Chars.html)：前置单引号字符常量。
- [GNU RISC-V assembler configuration](https://github.com/riscvarchive/riscv-binutils-gdb/blob/riscv-binutils-2.38/gas/config/tc-riscv.c)：核对 `#` 注释与 `;` 分隔符定义。
- [IntelliJ Platform syntax highlighting](https://plugins.jetbrains.com/docs/intellij/syntax-highlighting-and-error-highlighting.html)。
