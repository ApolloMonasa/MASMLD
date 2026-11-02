# 发布资料

GitHub 发布账户：ApolloMonasa — https://github.com/ApolloMonasa

Marketplace vendor：MASMLD — https://plugins.jetbrains.com/vendor/masmld（由发布者自行建档，Non-trader）

源代码：https://github.com/ApolloMonasa/MASMLD

问题反馈：https://github.com/ApolloMonasa/MASMLD/issues

许可证：MIT — https://github.com/ApolloMonasa/MASMLD/blob/main/LICENSE

## 三个独立条目

| 名称 | Plugin ID | 依赖 | 已选标签 |
| --- | --- | --- | --- |
| MASMLD Core | dev.masmld.core | IntelliJ Platform | Programming Language |
| MASMLD RISC-V | dev.masmld.riscv | MASMLD Core | Programming Language |
| MASMLD GNU Linker Script | dev.masmld.linker | MASMLD Core | Programming Language |

英文介绍及版本说明随各模块 META-INF/plugin.xml 打包。插件标志为各模块的 META-INF/pluginIcon.svg 和深色版本；原画见 branding/。不使用生成图片伪装 IDE 实际截图。

用户安装顺序：Core → 按需 RISC-V / GNU Linker Script → 重启。全部模块保持相同版本。RISC-V 需在项目的 MASMLD Assembly 设置中选择；GNU ld 无需架构选择。

隐私说明：插件不包含账号系统、遥测或主动上传源码功能。离线文档中有外部资料链接，点击后由浏览器访问相应网站。IDE/Marketplace 自身的更新、下载和统计遵循其自身政策。

兼容范围：最低平台 build 262。已对 docs/validation.md 列出的 RustRover/CLion 构建执行二进制验证，优先 RustRover；未把所有产品与后续版本表述为已实测。C/C++ 和 Rust 导航依赖宿主 IDE 提供相应索引。

## 发布流程

GitHub Release 提供三个可直接安装的插件 ZIP、组合容器与校验和；外层 masmld ZIP 需先解压，不直接安装。

Marketplace 是独立服务，首次上传需登录、创建 vendor 并接受开发者协议；GitHub 的 gh 授权不能替代 Marketplace 授权。先上传 Core，再上传两个依赖插件。新插件需经过 Marketplace 审核，上传成功不等于立即公开。

官方说明：https://plugins.jetbrains.com/docs/marketplace/uploading-a-new-plugin.html

发布实际结果以 GitHub Release 和 Marketplace 条目状态为准；本文件本身不宣称已完成商店审核。

## 0.7.0 实际发布记录

2026-11-03，三个插件均已成功上传，管理页面显示 “Your plugin was sent for moderation today”。当前状态为**已送审，尚未公开**。上传成功回执提示审核可能需要最多 2 个工作日；这不是保证完成时间。

- Core：https://plugins.jetbrains.com/plugin/35737-masmld-core
- RISC-V：https://plugins.jetbrains.com/plugin/35738-masmld-risc-v
- GNU ld：https://plugins.jetbrains.com/plugin/35739-masmld-gnu-linker-script

三者均选择 MIT、源码地址 https://github.com/ApolloMonasa/MASMLD、Programming Language 标签与默认稳定渠道；没有启用广告或 Hidden。发布者已自行完成协议确认与 vendor 建档，本次未修改其身份或联系信息。商店编辑页在审核期间显示相关资料编辑按钮禁用。

GitHub 安装包已公开：https://github.com/ApolloMonasa/MASMLD/releases/tag/v0.7.0 。审核期间可先从该处下载安装。商店条目在审核通过前可能仅发布者可访问；不把已取得条目编号表述为公开上架成功。
