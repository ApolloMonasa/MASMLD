# 0.8.0 发布验证

- 用户在 RustRover 完成 0.8.0-rc.3 编辑场景验收，覆盖标签和包含指令后的缩进、.endm 即时退回行首、Tab 和 Enter 补全、跨文件宏导航以及 GNU ld 补全与导航。
- 正式包通过 130 项自动化测试。三个模块对本机 RustRover 和 CLion 的六项 Plugin Verifier 检查通过，结果随安装包保存。

---

# 0.8.0-dev 开发者能力验证

日期：2026-11-03。工作分支：feature/developer-workflows。该开发包未覆盖公开的 0.7.0 安装包。

## 执行范围

- Core 20 项、RISC-V 68 项、GNU ld 30 项，共 118 项自动测试，无失败或跳过；最终精确结果随交付包 manifest.json 和 JUnit XML 保存。
- 其中 15 项使用真实 IntelliJ Platform unit-test 模式的 PSI/索引/编辑器 API：本地与跨文件定义、普通/数字标签引用搜索、包含文件及其编辑失效、符号与宏补全、宏/标签名字空间、重赋值分组重命名、数字标签修改拒绝、包含文件重命名和保守格式化。该模式不需要 IDE 许可证，不等同于图形主题及完整产品交互验收。
- 三个插件分别对 RustRover RR-262.11879.102 和 CLion CL-262.11879.106 做离线 Plugin Verifier，六组 Compatible，无阻断兼容性/内部 API/override-only 报告。
- 三模块结构、项目配置检查、打包资源与命名空间审计通过；组合沙箱已准备。独立 arch-example 编译、打包及结构检查通过，不进入三插件交付容器。
- tools/smoke/binary.py 在本机实际编译 C、C++ 和 Rust，执行 LLVM nm/symbolizer/cxxfilt：C/Rust 导出别名、C++ 链接名到调试源码行，以及 Rust v0 名字反解均通过。
- tools/smoke/riscv_catalog.py 直接读取新 ISA 目录的实际示例，用 LLVM MC 分别汇编 RV32 146 条、RV64 207 条；两个集合覆盖全部 212 个新增指令拼写。不会把此检查推广为所有操作数组合合法。
- GNU ld ALIGN 的零和非二次幂参数按本机实际 ld 输出核对；地址未知、循环、溢出和布局依赖最终输入时保持未知，不猜测结果。

工具链证据记录当前版本与脚本 SHA-256，匹配时才进入交付证据；CLI 验证不是 IDE 菜单/弹窗的 GUI 验收。复现方式见 README.md 和 developer-workflows.md。

## 验证边界

此前发布者提供的完整 IDE 功能与安装/升级/模块组合验收只适用于旧版，不延伸为 0.8 新功能的用户验收。该开发包构建时尚无新一轮 GUI/主题验收，未执行 Windows/macOS、全部 IntelliJ 产品及其他平台版本矩阵。二进制检查与 SDK 编辑器集成覆盖范围明确区分。

GNU 方言之外的语法、完整预处理/宏实例化、ISA 所有编码与特权约束、输入节最终布局及优化后二进制的完整调试语义不在本轮基础编辑模型中；对应工具和输入缺失时不伪造结果。

```bash
./gradlew releaseBundle :plugins:core:prepareSandbox_runIdeAll \
  :plugins:arch-example:buildPlugin :plugins:arch-example:verifyPluginStructure \
  -PwithExampleArchitecture=true \
  -PlocalIdePath=/absolute/path/to/rustrover \
  -PadditionalVerificationIdePath=/absolute/path/to/clion
python3 tools/smoke/binary.py
python3 tools/smoke/riscv_catalog.py
```

实际构建仍使用历史记录中的两个 Maven 镜像参数。本地容器：build/releases/masmld-0.8.0-dev.zip；新 GUI 验收与公开发布是后续独立状态。

---

# 0.7.0 发布验证

日期：2026-11-03。

## 用户确认的真实验收

发布者 ApolloMonasa 明确确认：0.6.0-dev 现有全部功能在真实 IDE 可见，暂未发现 bug；安装、升级和模块组合验证已完成。这是用户提供的验收结论，未额外推断具体 IDE 构建、主题或测试项目矩阵。下方历史记录保留当时的验证状态，不代表这些用户验收仍待进行。

0.7.0 新增宏导航、MIT 与品牌资源。宏导航尚无单独的用户 GUI 验收；其文本作用域、引用绑定与 PSI 定义范围执行自动化测试。本轮 Core 12 项、RISC-V 48 项、GNU ld 19 项，共 79 项测试通过，无失败或跳过。三个插件对下述两个 IDE 的六组 Plugin Verifier 检查均为 Compatible。构建、结构、项目配置和资源审计通过；详见发布包 manifest.json 和随附证据。

新增覆盖：普通宏调用、带参数定义、递归调用、宏体标签隔离、宏与标签同名、.purgem 后失效/重定义、嵌套宏不泄露及精确名称范围。二进制验证目标为本机 RustRover RR-262.11879.102 和 CLion CL-262.11879.106。

---

# 文件图标与导航验证记录

日期：2026-11-03，版本：0.6.0-dev。

## 已通过

- Core 12 项、RISC-V 44 项、GNU ld 19 项自动化测试，共 75 项，无失败或跳过。
- 新增覆盖：entry.asm 的地址标签、1f/1b 最近目标、与寄存器/操作码同名标签、重定位参数、常量声明、注释/字符串排除、导出规则、多定义与定义范围；定义 PSI 的轻量树节点精确覆盖名称。
- 宿主桥接协议测试覆盖非 PSI 原生导航项、Ex2 两阶段名称/定义查询、C++ 限定名称/签名，以及 Rust 风格 Ex 直接索引查询。没有把这些模拟协议测试称为真实 Rust/C++ 项目导航成功。
- Core 和 GNU ld 的浅/深主题 SVG 已渲染检查，16×16 资源已打包；RISC-V 共用 Core 文件图标。
- 三个插件对 RustRover 2026.2.5 / RR-262.11879.102 和 CLion 2026.2.4.1 / CL-262.11879.106 的 Plugin Verifier 检查均为 Compatible，共六组。没有添加强制的 Rust、C/C++ 或 Java 插件依赖。
- 测试辅助类新增标准 PsiReference 的本地/数字标签解析与文件图标断言，Java 辅助类编译通过。

## 未完成验证

新增的实际编辑器图标、Ctrl+点击/Ctrl+B、Rust 项目索引、CLion C/C++ 后端结果和跨文件候选尚未完成 GUI 运行验收。历史完整 IDE smoke 曾因独立测试配置缺少许可证停止，本轮没有复用用户激活信息或把未执行断言记作通过。

跨语言查询是按源码名字通过宿主索引导航，不反解 `_Z...` 等 C++ 修饰名或 export_name/链接器别名。`.ld` 本轮仅增加图标，没有加入链接脚本符号跳转。详细行为见 `navigation.md`。

## 构建与交付

```bash
./gradlew releaseBundle :plugins:core:prepareSandbox_runIdeAll \
  -PlocalIdePath=/absolute/path/to/rustrover \
  -PadditionalVerificationIdePath=/absolute/path/to/clion
```

本机仍使用下文记录的两个仓库镜像参数。交付容器为 `build/releases/masmld-0.6.0-dev.zip`，解压后按 INSTALL.md 安装内部 ZIP，并一并更新 Core 与已安装模块。

---

# 阶段 5 验证记录

日期：2026-11-02 至 2026-11-03，版本：0.5.0-dev。目标：本机 RustRover 2026.2.5 / RR-262.11879.102，JDK 25，Plugin Verifier 1.411。

## 已通过

- Core 12 项、RISC-V 28 项、GNU ld 19 项单元测试通过，共 59 项，无失败或跳过。
- 扩展校验覆盖重复架构 ID/语言、无效元数据和 profile、异常与链接错误隔离、取消传播，以及元数据快照。
- 三个独立插件编译、打包、结构和项目配置检查通过。
- `verifyAllPlugins` 将三个 0.5.0-dev ZIP 作为本地集合，对 RR-262.11879.102 离线执行二进制验证，三个 verdict 均为 Compatible；没有屏蔽兼容性问题或内部 API。
- 报告位于 `build/reports/pluginVerifier/RR-262.11879.102/plugins/<plugin-id>/0.5.0-dev/`。独立的首次在线 Core 检查曾在依赖下载阶段失败，不记作通过。
- 测试辅助插件包含第三方风格的示例架构注册，Java 辅助类编译、Python 脚本语法检查通过；它不进入正式插件 ZIP。
- 本地交付流程审计插件 ID、版本、依赖关系与代码命名空间，并附校验和和验证证据。

## 未完成

五种完整 IDE smoke 组合（core-only、core-riscv、core-ld、all、extension）均实际尝试。进程退出码均为 7，输出 No valid license found；行为断言没有执行，不能当作通过。日志在 `build/smoke/<mode>/stdout.log` 与各自 log 目录。

真实编辑器内的 Hover/Quick Documentation、文档目标编辑后恢复、模块禁用后的重启回退及文件关联交互仍未完成验收。用户此前确认 RISC-V 高亮可见，只覆盖该路径。没有复制许可证、修改当前 IDE 配置或绕过许可检查。

本阶段不将 Stage 5 整体验收或 Marketplace 发布标为完成。其他产品/平台版本尚未验证，RustRover 单一构建的 Compatible 结果不能推广为所有 IntelliJ 产品已通过。

## 复现

```bash
./gradlew releaseBundle -PlocalIdePath=/absolute/path/to/rustrover
python3 tools/smoke/run.py --ide /absolute/path/to/rustrover --mode extension --reuse-config
```

本机 Gradle 命令还带下文记录的两个仓库镜像参数。交付容器为 `build/releases/masmld-0.5.0-dev.zip`；内部每个载荷的 SHA-256 见 SHA256SUMS。它是未签名本地开发候选包，不是 Marketplace 发布。

---

# 阶段 4 验证记录

日期：2026-11-02，版本：0.4.0-dev。基线：本机 RustRover 2026.2.5 / 262.11879.102 / JDK 25。

## 已通过

- RISC-V 原有 28 项与 GNU ld 新增 19 项 JUnit 测试通过，共 47 项，0 失败、0 跳过。
- GNU ld 测试包含 rCore `os/src/linker.ld` 固定样本的词法/轻量树解析、MEMORY/SECTIONS 嵌套、不完整输入恢复、原始与高亮 Lexer 的逐 token 重启、300 组随机文本保留、48 个目录项查询和颜色映射。
- GNU ld 测试类路径不包含 `dev.masmld.riscv.RiscVLanguage`，对应断言通过。该结果验证测试类路径独立性，不替代实际插件加载验证。
- 三个插件的编译、独立 ZIP 打包、`verifyPluginStructure` 与 `verifyPluginProjectConfiguration` 均通过。
- 检查三个 plugin.xml：Core 只依赖平台，RISC-V 与 GNU ld 分别依赖 Core，二者没有相互依赖。GNU ld ZIP 未重复打包 Core 或 RISC-V 代码。
- `:plugins:core:prepareSandbox_runIdeAll` 成功；组合沙箱包含 core/arch-riscv/linker-script 三个 0.4.0-dev JAR。
- GNU ld 单模块的默认开发沙箱仅包含 linker-script 与 Core。
- 完整 IDE smoke 辅助类加入 core+ld、三插件组合、`.ld` 文件类型/AST/高亮器/文档目标断言，Java 辅助类编译与 Python 脚本语法检查通过。

## 未执行与限制

本轮未启动新的图形 IDE，也未执行新增 smoke 行为断言。文件类型被其他插件占用时的交互、GNU ld Hover/Quick Documentation 实际弹窗、编辑后文档指针恢复、三插件完整共存尚未完成验收。准备好沙箱目录不等于已运行 IDE。

未修改用户 rCore 源文件或当前运行的 IDE 配置；只将 linker.ld 复制为测试样本。未调用链接器，未验证示例的最终链接结果。其他 IDE 与跨版本 Plugin Verifier 检查仍未执行。

## 命令

```bash
# 两组自动化测试
./gradlew :plugins:linker-script:test :plugins:arch-riscv:test -PlocalIdePath=/absolute/path/to/rustrover
# 全模块开发窗口
./gradlew :plugins:core:runIdeAll -PlocalIdePath=/absolute/path/to/rustrover
# 独立 GNU ld 开发窗口
./gradlew :plugins:linker-script:runIde -PlocalIdePath=/absolute/path/to/rustrover
```

本机构建、测试与结构检查实际运行还带下文记录的 pluginRepository / mavenRepository 镜像参数。测试报告分别位于 `plugins/linker-script/build/reports/tests/test/index.html` 与 `plugins/arch-riscv/build/reports/tests/test/index.html`。使用与覆盖范围见 `linker-script.md`。

---

# 阶段 3 验证记录

日期：2026-11-02，版本：0.3.0-dev。基线：本机 RustRover 2026.2.5 / 262.11879.102 / JDK 25。

## 已通过

- `:plugins:arch-riscv:test` 共 28 项 JUnit 测试通过，0 失败、0 跳过。保留阶段 2 的 20 项测试，新增 8 项文档测试。
- 新增覆盖：rCore `entry.asm` 样本中的指令/寄存器/指示符；全部 181 个资料名称的上下文可达性；标签、字符串、注释、重定位符号的误触发排除；ABI 别名；RV64 范围；可变伪指令展开说明；HTML 转义与链接协议过滤。
- Core 与 RISC-V 0.3.0-dev 编译、独立 ZIP 打包、`verifyPluginStructure`、`verifyPluginProjectConfiguration` 均通过。
- 检查交付 JAR：三份 TSV 资料只进入 RISC-V 插件，未进入 Core；两个 plugin.xml 的版本均为 0.3.0-dev。
- 新增完整 IDE 文档目标、共享渲染和恢复指针 smoke 断言，测试辅助 Java 类已编译；断言尚未执行。
- 用户已在上一阶段确认 `entry.asm` 经过架构选择后出现高亮。这是用户确认的使用路径，不等同于全部编辑器流程验收。

## 未完成验证

尚未实际验证 0.3.0-dev 在完整 RustRover 编辑器中的 Hover/Quick Documentation 弹窗、全局扩展点调度，以及编辑后文档目标指针失效/恢复。自动化查表测试不启动完整 IDE，不能代替这些验证。

未修改当前正在运行的用户沙箱配置，未复制许可证。第一阶段隔离测试沙箱的许可阻塞是历史记录，本阶段没有把历史失败或未运行的检查记作通过。其他 IDE、跨版本 Plugin Verifier 兼容性验证尚未执行。

启动新版本：退出旧沙箱，再按 README 的正确安装根目录运行 `:plugins:arch-riscv:runIde`。普通悬停需启用 IDE 的 Show quick documentation on hover 设置；也可在名称内部使用 View → Quick Documentation。

本机完整构建命令沿用下文两个镜像参数，测试报告位于 `plugins/arch-riscv/build/reports/tests/test/index.html`。详细资料范围与来源见 `hover.md`。

---

# 阶段 2 验证记录

日期：2026-11-02，版本：0.2.0-dev。环境沿用本机 RustRover 2026.2.5 / 262.11879.102 / JDK 25。

## 自动化与构建

- `:plugins:arch-riscv:test`：20 项 JUnit 测试通过，无跳过。覆盖 Lexer（8）、上下文分类（7）、平台轻量语法树解析（3）、词法颜色映射与颜色预览（2）。
- Lexer 覆盖每个 token 边界的增量重启、截断块注释续扫，以及固定随机种子的 300 组不完整输入文本保留检查。
- Parser 测试使用平台 `PsiBuilderFactoryImpl` 的轻量树，确认换行/分号恢复、未知宏和未完成输入的语句范围，不启动完整 IDE。
- 两个插件独立 ZIP 编译打包、`verifyPluginStructure` 和 `verifyPluginProjectConfiguration` 检查通过。
- 测试 HTML 报告：`plugins/arch-riscv/build/reports/tests/test/index.html`。

执行测试：

```bash
./gradlew :plugins:arch-riscv:test -PlocalIdePath=/absolute/path/to/rustrover
```

本机执行仍使用下文记录的仓库镜像参数。测试使用平台类库，但不依赖完整 RustRover 的许可证。

## 本阶段未覆盖

完整编辑器的 Annotator 调用、项目架构切换后的高亮刷新、主题视觉效果、设置跨重启持久化，以及其他 IDE 的行为未完成运行验收。第一阶段的隔离完整 IDE 检查曾因无许可证停止，本阶段未将该检查标为通过。

`tools/smoke/SmokeStarter.java` 已增加公共文件类型的高亮器分发和 RISC-V AST 实例检查，供具备有效许可的沙箱运行；这些新增断言尚未执行。

高亮只覆盖 `riscv-syntax.md` 明确列出的范围；Hover、完整指令校验和其他架构尚未实现。

---

# 阶段 1 验证记录

日期：2026-11-02。环境：Linux x86_64，RustRover 2026.2.5，构建号 262.11879.102，JDK 25。

## 已通过

- Gradle Wrapper 9.4.0 已生成，并固定官方发行 ZIP 的 SHA-256。
- Core 与 RISC-V Kotlin 编译、独立 ZIP 打包成功。
- 两个插件的 `verifyPluginStructure` 与 `verifyPluginProjectConfiguration` 检查通过。
- Core 的 `buildSearchableOptions` 在真实 RustRover 进程中完成，设置索引生成成功。
- 检查 ZIP 内容：RISC-V ZIP 未重复打包 Core；开发沙箱中 Core 作为独立依赖安装。
- 隔离启动日志分别记录加载 `MASMLD Core`，以及 `MASMLD Core + MASMLD RISC-V`。这仅证明插件加载，并非全部功能通过。

执行构建及结构检查命令：

```bash
./gradlew \
  :plugins:core:buildPlugin :plugins:arch-riscv:buildPlugin \
  :plugins:core:verifyPluginStructure :plugins:arch-riscv:verifyPluginStructure \
  :plugins:core:verifyPluginProjectConfiguration :plugins:arch-riscv:verifyPluginProjectConfiguration \
  -PlocalIdePath=/absolute/path/to/rustrover
```

本机网络无法直连部分仓库，实际运行还传入了：

```text
-PpluginRepository=https://maven.aliyun.com/repository/gradle-plugin
-PmavenRepository=https://maven.aliyun.com/repository/public
```

工程未固定个人机器路径或代理。当前全部为 Kotlin、无 Swing 表单，因此关闭 Java 空值检查与表单字节码插桩；后续引入相关代码时重新评估。

## 未通过或未执行

`tools/smoke/run.py` 的测试辅助插件已编译，两个隔离沙箱均在许可证检查阶段以退出码 7 结束，输出 `No valid license found`。行为断言尚未执行，不能宣称文件关联、设置应用后的 PSI 重解析和缺失模块回退已经通过运行测试。日志位于 `build/smoke/{core-only,core-riscv}/`。

尚未执行完整图形界面人工验收、项目设置跨重启持久化验证、插件启用/禁用后重启验证、Plugin Verifier 二进制兼容性检查，以及 IDEA/CLion 等其他产品验证。由于这些验收缺口，阶段 1 当前属于已构建的基础设施预览，尚未完整验收。

语法高亮、Hover、RV32/RV64 profile 选择、`.ld` 模块、x86 和 ARM 尚未实现，不属于本轮已交付能力。
