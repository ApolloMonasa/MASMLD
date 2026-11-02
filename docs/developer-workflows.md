# 开发者编辑能力（0.8）

本轮覆盖此前列出的 Core、RISC-V、GNU ld 与跨语言导航缺口。语法模型与实际工具链事实分开；不把基础诊断等同于完整汇编器或链接器。

## 文件、目录与目标配置

Settings → Languages & Frameworks → MASMLD Assembly：设置项目架构，按需填写 profile（rv32i / rv64i）和目标（如 rv64imafdc_zicsr、rv32gc）。留空目标及 profile 时不做 ISA 限制。当前方言为 GNU，其他方言不可直接冒用 GNU 解析。

覆盖规则每行：`路径 | 架构 ID | profile | 方言 | 目标`，至少前两列。例如：

```text
boot/ | riscv | rv32i | gnu | rv32imc_zicsr
src/entry.asm | riscv | rv64i | gnu | rv64gc
vendor/**/raw.asm | | | gnu |
```

最后匹配的规则生效；空架构回退 Generic。配置中保留缺失模块的 ID，安装对应模块后恢复。include 搜索先当前文件目录，再项目根目录，再配置的搜索目录；相对搜索目录以项目为基准。

## RISC-V

- 指令位置补全指令、伪指令、指示符和当前有效宏；操作数位置补全整数/浮点寄存器、本文件/include 符号以及项目导出符号。可用 Ctrl+Space，前缀由 IDE 匹配。
- 参数信息通过 IDE Parameter Info 操作（通常 Ctrl+P）查看指令语法或宏声明。宏体中可补全参数名称。
- M/A/F/D/C、Zicsr、Zifencei（含常用 CSR 伪指令）增加高亮、离线说明与目标过滤；A 包括 aq/rl/aqrl 拼写。按当前位置的 `.option push/pop/arch` 作用域检查 XLEN、扩展、基本参数数量、整数/浮点寄存器、部分立即数和舍入模式。扩展不匹配可用快速修复生成当前文件的目标覆盖。
- `.include "path"` 文件导航及包含文件中的标签、常量、宏导航。展开静态文本顺序，处理 `.purgem` 生命周期。递归包含有循环、深度、文件数与文本大小限制；不会执行宏体中的 include。
- 标准 Find Usages 和 Rename 接入 PSI 引用；数字局部标签重命名被拒绝。宏与标签名字空间分开，普通常量重复赋值的同名声明作为重命名组；冲突检查和预览保留。非汇编文件中的合成导航目标不提供源码重命名。
- Structure 视图展示标签、常量和宏；支持宏、重复块、条件块、CFI 过程及多行注释折叠。Reformat Code 仅调整已知 token 间距与缩进，保留宏参数、重定位、字符串及其他敏感 token 拼写。

C 预处理、条件分支求值、参数拼接及宏实例化仍不执行；私有 ISA、完整压缩编码和特权诊断由实际汇编器负责。无法找到源码定义时以弱提示说明可能来自外部目标文件或链接器。

## GNU ld

- 命令、函数、符号、MEMORY 区域、输出节补全；在 `>` / `ORIGIN` / `LENGTH` 和 `ADDR` / `LOADADDR` / `SIZEOF` 等位置按名字空间筛选。
- 定义 PSI、引用跳转、Find Usages、Rename，涵盖符号赋值、MEMORY 声明、输出节与 REGION_ALIAS。INCLUDE 支持引用文件及包含图中的符号，未关联为 .ld 的包含文件仍可按位置导航。
- 结构视图、花括号/多行注释折叠和保守格式化。
- 常量分析支持算术、位运算、比较、K/M、ORIGIN、LENGTH、双参数 ALIGN、MIN/MAX、ABSOLUTE。循环、溢出、未知函数、重复赋值及未知链接期量保持未知，不猜测 SIZEOF 或单参数 ALIGN 的值。
- 提示未定义区域、重复区域/输出节，以及可确定的非正长度、地址溢出和区域重叠，以及可确定的固定输出节起点/显式对齐不一致。地址别名可能有意重叠，因此使用弱提示。未根据未知输入节尺寸声称镜像越界。

## 跨 Rust/C/C++ 及实际链接符号

默认使用宿主源码索引；另识别常见 Rust export_name / unsafe(export_name)、C/C++ asm 名称及 GNU ld 简单符号别名。常见 Itanium 与 Rust legacy 路径提供名字级解码，不把名字匹配当作完整 ABI 解析。

需要完整修饰名时，在 MASMLD Assembly → Binary Symbols 配置 llvm-cxxfilt 或 c++filt，使用 Tools → MASMLD: Demangle and Navigate Symbol。外部程序在可取消后台任务中执行，参数按列表传递，不使用 shell。

需要按**实际链接结果**导航时：配置带调试信息的 ELF/目标文件、nm（LLVM 或 GNU）及 llvm-symbolizer / addr2line；构建完成后执行 Tools → MASMLD: Refresh Binary Symbols。索引精确导出名后，跳转通过地址查询源码文件/行。构建产物修改后缓存失效，需重新刷新。没有调试信息或本机源码文件时给出说明。此功能不负责构建、链接或调试运行，也不自动安装工具。

CLI 协议集成验证：`python3 tools/smoke/binary.py`，本机需 cc 与相应 LLVM 工具。它验证实际二进制导出别名到源码行的查询，不替代 IDE 界面验收。
