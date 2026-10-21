# 私人预览验收记录

日期：2026-10-21（Asia/Shanghai）。本记录对应当前工作区和本机运行实例。

- Java 21 / Spring Boot 3.5.16；Vue 3 / TypeScript；TipTap 3.32.0。
- `bash scripts/setup.sh` 已完整执行成功，包括冻结 npm 安装、前端类型检查/生产构建、Maven 打包与测试。
- Java：12 项测试通过，0 失败、0 错误、0 跳过。
- Playwright + 系统 Chromium：5 项测试通过，覆盖登录、工作台、编辑器双模式往返、表格/代码、保存重载、全文搜索、收藏、删除、手机布局、深色模式、未登录访问拒绝、弹窗键盘交互。
- axe 的 WCAG 2 A/AA、2.1 AA 自动规则：登录、浅色工作台与深色工作台检查通过。自动规则不代表完整的人工无障碍认证。
- 生产 npm 依赖审计：0 项已知漏洞；这不替代持续升级和后续安全审查。
- 正常停止并重启 Java 服务后，8 篇示范记录的 ID、正文与全部文章元数据指纹一致；预览密码文件继续可用。
- 健康接口成功；未登录读取笔记/备份/导出返回 401；默认公开总开关关闭。
- `.data`、预览密码、依赖目录、构建目录及浏览器临时结果均被 Git 忽略。没有记录含登录凭证的浏览器 trace。
- 安装脚本与启动说明已保存到环境配置草稿；未发布环境快照，未对外部署站点。

所有示范内容均为非敏感虚构记录。测试创建的记录已通过应用接口删除，没有引入真实聊天、代码或日记。

## 截图

- [私人登录页](artifacts/login-desktop.png)
- [桌面工作台](artifacts/dashboard-desktop.png)
- [手机工作台](artifacts/dashboard-mobile.png)
- [阅读页](artifacts/reader-desktop.png)
- [双模式编辑器](artifacts/editor-desktop.png)
- [手机深色空间](artifacts/knowledge-mobile-dark.png)

## 尚未验证或接通

ChatGPT 账号登录未接通。标准 OIDC 适配器未进行真实身份提供商联调。当前仅使用私人预览密码。Docker 配置已交付但未在本环境构建运行；没有对外 HTTPS、真实跨设备公网连接或灾备恢复演练的验证结果。

完整运行、登录接入、部署与数据备份说明见 [README.md](README.md)。保持 `PUBLIC_ACCESS_ENABLED=false`，直到用户审核并明确批准外部公开访问。
