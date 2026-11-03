# MASMLD

<p align="center"><img src="docs/branding/masmld-logo.svg" width="128" alt="MASMLD 蓝紫色立方体标志" /></p>

MASMLD 为 RustRover、CLion 等 IntelliJ Platform IDE 提供 RISC-V 汇编和 GNU ld 链接脚本编辑支持。插件支持语法高亮、中文悬停说明、代码补全和定义跳转。

[下载安装包](https://github.com/ApolloMonasa/MASMLD/releases) · [版本记录](CHANGELOG.md) · [问题反馈](https://github.com/ApolloMonasa/MASMLD/issues)

## 安装

MASMLD 要求 IDE 平台 build 262（2026.2 系列）或更高。RustRover 是主要实机验收环境，CLion 已通过插件兼容性检查。

1. 在 [GitHub Releases](https://github.com/ApolloMonasa/MASMLD/releases) 下载所需的 ZIP。下载完整的 `masmld-<版本>.zip` 后，先解压取出各模块安装包。
2. 打开 **Settings → Plugins → 齿轮菜单 → Install Plugin from Disk**，先安装 Core，再按需安装可选模块。
3. 确认 Core 和可选模块的版本号一致，然后重启 IDE。

| 安装包 | 用途 |
| --- | --- |
| `core-<版本>.zip` | 必装，提供汇编文件关联和项目设置 |
| `arch-riscv-<版本>.zip` | 编辑 RISC-V 汇编文件（`.s`、`.S`、`.asm`） |
| `linker-script-<版本>.zip` | 编辑 GNU ld 链接脚本（`.ld`），可与 Core 单独搭配 |

## 使用

### RISC-V 汇编

在 **Settings → Languages & Frameworks → MASMLD Assembly** 中将当前项目的架构设为 **RISC-V**。打开汇编文件后，可以用 IDE 的标准操作触发补全、查看参数提示、跳到标签或宏定义。项目设置也可以配置目标 ISA 和 `.include` 搜索路径。

开启 IDE 的 **Show quick documentation on hover** 后，悬停在指令、寄存器或汇编指示符上即可查看中文说明，也可以直接调用 **Quick Documentation**。Rust、C 和 C++ 的源码符号跳转依赖宿主 IDE 对相应语言的索引。

### GNU ld 链接脚本

打开 `.ld` 文件即可补全或跳转到符号、`MEMORY` 区域、输出段和 `INCLUDE` 文件。可以用 IDE 的 **Reformat Code** 整理脚本。试用时可打开 [示例链接脚本](examples/linker/memory.ld)。

### 0.9 及以上版本

- **宏展开预览**。在 RISC-V 宏调用上右键选择 **MASMLD: Preview Macro Expansion**，查看参数绑定和静态展开结果。
- **查看链接结果**。用 GNU ld 的 `-Map=kernel.map` 生成报告，在 `.ld` 或 `.map` 文件上右键选择 **MASMLD: Inspect GNU ld Map**，查看实际链接地址、输入目标文件和脚本定义。安装前请在 Releases 确认该版本是否已提供下载。

## 常见问题

- **文件没有高亮**。检查已安装所需模块。编辑汇编时，将项目架构设为 RISC-V。确认文件类型关联为 **MASMLD Assembly** 或 **MASMLD Linker Script**。如果扩展名已被其他插件占用，可以手动调整关联。
- **插件提示缺少依赖**。先安装与可选模块同版本的 Core。升级时请一并更新已安装的 MASMLD 模块。
- **悬停没有说明**。使用 IDE 的 **Quick Documentation** 确认当前指令有文档，再检查是否启用了悬停说明。

本项目采用 [MIT 许可证](LICENSE)。第三方材料见 [第三方声明](THIRD_PARTY_NOTICES.md)。
