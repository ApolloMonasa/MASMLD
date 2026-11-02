# 文件图标与符号导航（0.7.0）

## 使用

升级 Core 与已安装的可选模块后重启。汇编文件需关联 MASMLD Assembly，`.ld` 需关联 MASMLD Linker Script。汇编文件有橙色 A 图标，链接脚本有紫色链接图标；SVG 提供浅色与 `_dark` 版本。

在 RISC-V 汇编符号使用处按 Ctrl+点击或 Ctrl+B（Navigate → Declaration or Usages）。例如 rCore `entry.asm` 的 `la sp, boot_stack_top` 定位到本文件末尾的标签；`call rust_main` 在 RustRover 的项目符号索引可用时查询到 Rust 定义。CLion 中同样使用宿主 C/C++ 符号查询，不依赖 Rust 插件。

## 解析顺序与边界

1. 同文件普通标签，以及 `name = expression`、`.equ/.equiv/.set` 声明。
2. 数字局部标签 `1f/1b` 选择最近的后方/前方同号定义，不跨文件。
3. 当前项目其他 MASMLD RISC-V 文件内通过 `.global/.globl/.weak` 导出的定义；`.L` 局部名不跨文件，显式 `.local` 的定义不导出。
4. 汇编没有可用定义时，调用宿主 IDE 已安装的 Go To Symbol 提供者查询同名源码目标。支持 PSI 定义和非 PSI 的可导航后端目标。

多个普通定义保留为候选，不擅自选第一个。寄存器、操作码、注释、字符串、节名、重定位修饰符不作为普通标签引用；重定位表达式的实际符号参数可以导航。宏体不参与全局标签收集，不展开宏或 include，不模拟条件编译或 `.set` 求值顺序。

跨语言是源码名称导航，不是 ELF 链接模拟。C 函数、Rust 源码名和宿主可查询的 C++ 名称可被查找；尚不反解 C++ `_Z...` 修饰名、Rust 符号修饰、export_name 别名或链接器脚本别名。C++ 的 `extern "C"` 接口通常具有与汇编一致的源码名字，但插件不验证最终 ABI 或链接结果。

`.ld` 本轮增加图标，链接脚本符号导航尚未实现。通用汇编和未来架构需由对应架构模块提供解析；目前导航实现位于 RISC-V 模块。

## 实现

- 解析器为标签和常量名称生成独立 `RiscVSymbolDefinition`，实现 PsiNameIdentifierOwner；引用通过 `RiscVStatement.getReferences()` 暴露标准 PsiPolyVariantReference，支持平台默认的 Ctrl+点击与 Ctrl+B。
- 文本符号分析按 PsiFile 缓存并随文件修改失效；跨汇编文件候选由平台词索引缩小范围，不逐个扫描整个项目。
- SourceSymbolBridge 只引用公共 IntelliJ 接口，不导入 org.rust 或 C/C++ 插件类。缺少语言支持就没有对应提供者；索引期间只保留本地解析，不因缺少 Rust 插件而失败。
- 对 ChooseByNameContributorEx2 先执行带查询模式的名称阶段，再按提供者返回的索引名称查询定义，兼容 clangd 的缓存协议；直接索引的 Ex 提供者不枚举所有名称。
- CLion 后端可能只返回 NavigationItem；以可导航的合成 PSI 目标保留其导航和展示行为，不要求强制转换成 Rust/Java PSI。
- 名称相同的重载/限定名可显示多个候选。不承诺完整重命名或查找引用功能；数字局部标签重命名不支持。

## 验证

新增 10 项符号范围测试、1 项定义节点轻量树测试，以及 5 项宿主桥接协议测试，在 0.6 预览中连同既有测试共 75 项；0.7 新增 4 项宏测试，共 79 项。桥接测试覆盖原生非 PSI 目标、clangd 的两阶段查询、C++ 限定名称/签名和 Rust 风格直接索引查询；它们是协议测试，不是启动实际语言服务器的端到端测试。

四个 SVG 已通过 XML/尺寸检查并用 rsvg-convert 渲染查看。两个本机 IDE 的三插件二进制验证共六组为 Compatible。用户已确认 0.6 现有功能的真实 IDE 验收完成；未额外推断具体产品、主题与项目矩阵。0.7 新宏导航尚无单独用户 GUI 验收。完整 IDE smoke 中增加本地标签/数字标签和图标断言，断言尚未在具备许可证的独立测试沙箱执行。

## GNU 宏

同文件 `.macro name ...` 创建宏定义节点。调用名称与 `.purgem name` 提供标准 PSI 引用；按文本顺序绑定当前定义，清除后不继续绑定旧定义，再次定义后绑定新位置。宏与标签名字空间隔离，宏不通过 `.globl` 导出、不进入宿主源码查询。已知宏体中的宏调用（含递归）可导航；宏体标签、参数及展开时嵌套定义不泄露到文件作用域。

仅识别静态、同文件名称，不展开 include、条件、预处理或宏参数；对未生效的条件分支也只按源码文本顺序分析。首次定义之前的未知调用不提供引用。这是编辑器导航模型，不替代 GNU as 的展开与合法性判断。

GNU as 语义参考：https://sourceware.org/binutils/docs/as/Macro.html
