# 编写独立架构模块

MASMLD 使用 SemVer 2.0.0。0.x 的扩展 API 仍处开发阶段，改变契约时提升次版本并记录迁移；1.0 之后不兼容的公共契约修改提升主版本，兼容能力增加提升次版本，修复提升修订号。已发布二进制不覆盖；Core 与官方可选模块同步版本。

## 可以编译运行的示例

`plugins/arch-example` 是独立的教程插件，仅在传入 `-PwithExampleArchitecture=true` 时加入 Gradle 项目，不进入正式三插件交付包，也不宣称支持任何真实 ISA。

```bash
./gradlew :plugins:arch-example:buildPlugin :plugins:arch-example:verifyPluginStructure \
  -PwithExampleArchitecture=true -PlocalIdePath=/path/to/ide
./gradlew :plugins:arch-example:runIde \
  -PwithExampleArchitecture=true -PlocalIdePath=/path/to/ide
```

示例仅依赖 Core。运行后在 MASMLD Assembly 选择 Example，或增加覆盖规则 `demo/ | example | example-v1 | gnu |`。其 parser 保留文本，后续按自身语法替换 Lexer/Parser，再注册补全、文档、导航等语言扩展。

## 公共契约

- `ArchitectureProvider`：稳定且唯一的 `id`、展示名称、独立语言实例与可选 profile 列表。语言必须是 `AssemblyLanguage` 的不同方言；重复 ID、共享语言或重复 profile 会被隔离。
- `ArchitectureProfile`：架构模块内部定义的目标标识和展示名称，不代表核心已实现指令验证。
- 扩展点 `dev.masmld.core.architecture`：在可选插件的 plugin.xml 注册 provider。描述符依赖 `dev.masmld.core` 和 `com.intellij.modules.platform`，没有实际需求时不引入特定产品、Java、Rust 或 C/C++ 依赖。
- `AssemblySettings.selection(projectRelativePath)`：读取当前文件生效的架构、profile、方言和目标选项。文件规则最后匹配者优先；目录写作 `dir/`，glob 支持 `*`、`?`、`**`。其他架构不继承项目架构专用的目标选项。
- `AssemblySettings` 是持久化组件与 ModificationTracker；后台缓存须依赖它，配置应用后重解析文件。模块不可修改返回状态快照并假设自动生效；使用 loadState 保存。
- `DocumentationEntry` / `DocumentationSource` / `DocumentationRenderer`：离线纯文本资料与安全 HTML 渲染，来源链接不在 Hover 时自动联网。

Core 的 source/editing/binary 包是官方模块共用的开发辅助设施，0.x 期间可能调整。优先依赖小型 provider 契约，不把可选模块类引用带入 Core。

## 依赖与文件归属

`.s/.S/.asm` 的文件类型只由 Core 注册；不要在架构插件重新争抢扩展名。Core 的语言替换器按文件生效设置选择语言；缺少模块时回退 Generic，保留设置以便恢复。GNU ld 是独立语言，只依赖 Core，不通过 CPU 架构选择。

原型阶段可直接使用示例的 `localPlugin(project(":plugins:core"))`。独立仓库开发时，应使用与目标发布版本一致的 Core ZIP/API，不把 Core 类重复打进架构 JAR。安装或启停模块后重启。

## 验证清单

运行本模块测试、结构检查与目标产品的 Plugin Verifier；验证 Core-only、Core+本模块及缺失模块回退。`tools/smoke` 的 example 模式仍是测试辅助。不要把示例教程插件或测试框架打入发布 ZIP。

真实 ISA 模块应明确方言、支持的 ISA 扩展、宏/预处理边界，并在未知输入下保留文本与取消响应。不能让后台索引器读取项目设置或遍历其他文件；跨文件语义在可取消的项目查询服务中处理。

`ArchitectureOptionsValidator` 是 0.8 增加的可选能力接口。实现 `validateOptions(profileId, dialect, target)`，返回 null 表示接受，其他字符串作为设置保存前的提示。实现必须无副作用、不执行工具或读取外部文件；老 provider 无需实现，不因新增必需方法而失效。
