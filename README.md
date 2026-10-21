# 留白 · Notes

一个安静的私人数字花园。记录知识，安放思考，收藏生活。

**Java 21 / Spring Boot + Vue 3 / TypeScript。真实服务端持久化，默认私密。**

## 当前交付状态

- 私人工作台、三个同等重要的内容空间、收藏、草稿、全文搜索、分类/标签筛选和排序。
- TipTap 可视化编辑器，以及带实时预览的 Markdown 编辑器；支持表格、代码高亮、引用、链接、图片与常见附件，快捷键 `Ctrl/⌘ S` 保存。
- 日记日期与时间线，日历筛选，阅读目录，浅色/深色/跟随系统，移动端布局与减少动态效果偏好。
- H2 **文件型数据库**、Flyway 迁移、服务端文件存储；内容不依赖 localStorage。localStorage 只保存主题选择。
- Cookie 会话、CSRF、服务端访问控制、登录限流、文件类型/大小与签名检查、HTML 清洗、CSP、并发编辑冲突检测。
- Markdown 导入/ZIP 导出、JSON 备份/恢复、服务端完整备份方案。
- 每 30 秒及窗口重新获得焦点时同步服务端记录；编辑期间不覆盖本地输入，保存版本冲突返回 409。

首版提供 **8 篇明确标记的非敏感示范记录**与一张本地 SVG 插画。没有导入历史聊天、工作代码或真实日记。默认所有示范内容也为私密。

**当前只是本环境私人预览，没有对外部署，也没有开启公开访问。**

## 快速运行

前置：Java 21（建议完整 JDK）、Node.js 22+、Python 3.12+。脚本默认将工具和依赖缓存存放在 `/workspace`，不需要系统管理员权限。

```bash
cd /workspace/note
bash scripts/setup.sh
bash scripts/start.sh
```

后端默认仅监听本机的 **8080** 端口，同时提供生产构建的 Vue 页面与 API，不需要额外前端进程。健康检查：

```bash
curl --fail http://127.0.0.1:8080/api/health
```

初次运行生成随机预览密码，保存在 `.data/preview-password`，权限 `0600`，不写入日志、不提交 Git。请通过当前环境终端自行查看该文件，并在登录页输入。也可由安全运行环境提供至少 12 个字符的 `NOTES_PREVIEW_PASSWORD`；不要写进源码或提交 `.env`。

开发时分别运行：

```bash
bash scripts/start.sh
# 另一终端；API 代理至 8080
npm --prefix frontend run dev
```

`setup.sh` 会校验 Maven 下载的 SHA-512，保留 TLS 校验并复用环境代理。部分云镜像有 Java 21 编译模块却缺少 `ct.sym`，`mvn.sh` 会使用 Java 21 source/target 编译；完整 JDK 使用标准 `--release 21`。

每个云任务已经隔离；直接使用现有 checkout，无须创建 Git worktree。

## 验证

```bash
bash scripts/mvn.sh verify
npm --prefix frontend run build
# 先启动后端；Chromium 默认路径 /usr/bin/chromium，可用 CHROMIUM_PATH 覆盖
npm --prefix frontend run test:e2e
```

Java 测试覆盖未登录读写/上传/备份拒绝、CSRF、强制私密草稿、显式公开/撤销、私人预览总开关、附件越权/格式检查、乐观锁、删除、JSON 恢复、登录/注销与示范初始化幂等性。浏览器测试覆盖登录、工作台、编辑器模式切换、表格/代码、保存重载、正文搜索、收藏、删除、手机布局及深色模式。

浏览器截图保存在 `artifacts/`；测试临时结果在忽略的 `frontend/test-results/`；不记录含登录凭证的 trace。

## 隐私和分享

采用单一主人模型，外部 OIDC 只允许配置的唯一 subject 登录，禁止任意自助注册。正文、搜索、编辑、删除、导入、备份与附件均经过服务器认证。会话为 HttpOnly、SameSite=Lax；写入需 CSRF token，登录后更换 session ID。

两层公开规则必须**同时满足**：

1. 登录主人逐篇确认正文/附件后手动标记公开，且文章不是草稿。
2. 部署管理员显式设置 `PUBLIC_ACCESS_ENABLED=true`。

默认 `PUBLIC_ACCESS_ENABLED=false`。此时所有公开接口都返回 404，即使一篇文章已标记公开。测试与内容审核完成前请保留该设置。

公开页仅按随机分享标识读取指定记录，没有公开全库搜索或分类索引。撤销分享、改成草稿或删除后，公开正文和附件立刻拒绝访问；响应禁止缓存。已由访客自行保存的副本无法撤回。

附件绑定具体文章；默认只能通过登录会话读取。公开后正文中的本文章附件地址会转换为受公开规则保护的地址。图片仅允许同源加载，避免打开私密记录时访问第三方追踪图片。HTML/SVG/脚本类附件不接受。普通附件强制下载并设置 `nosniff`，最大 10MB。文件校验不等于恶意软件扫描，公开部署前可按需要接入专门的扫描服务。

## 登录接入边界

**ChatGPT 账号登录尚未实现，也未伪装成已接通。** 当前环境没有可供本项目使用的 ChatGPT 官方身份接口与客户端凭证。ChatGPT 会话、ChatGPT 订阅以及 OpenAI API Key 都不能被当作本网站的登录凭证。

已实现标准 OIDC 授权码登录接入，适用于实际拥有的身份服务，**不等同于 ChatGPT 登录**。需要安全配置：

| 变量                  | 用途                                            |
| --------------------- | ----------------------------------------------- |
| `OIDC_ISSUER_URI`     | 可信身份服务的 issuer，必须使用 HTTPS           |
| `OIDC_CLIENT_ID`      | 注册的客户端 ID                                 |
| `OIDC_CLIENT_SECRET`  | 安全环境中的客户端密钥                          |
| `OIDC_OWNER_SUBJECT`  | 唯一允许登录的主人 subject                      |
| `LOCAL_LOGIN_ENABLED` | 本地密码登录开关，OIDC 验证通过后可设为 `false` |

在身份服务注册回调 `/login/oauth2/code/owner`。正确配置以上四项后登录页才显示身份登录入口。尚未用实际身份服务完成联调，因此不要把它当作已经验证的生产登录能力。

## 数据、备份与迁移

默认路径（均已被 Git 和 Docker 构建忽略）：

```text
.data/
  notes.mv.db           # H2 文件数据库
  uploads/              # 随机文件名，原文件名仅存于数据库
  preview-password      # 本机首次启动密码；按机密文件保护
```

- **Markdown ZIP**：所有记录的正文和有限 front matter，适合带到其他笔记工具。附件不嵌入 ZIP。
- **JSON 备份**：正文、分类、标签、日期等完整文章元数据；不含附件二进制。恢复新增记录，不覆盖已有内容，恢复后强制为私密草稿。每次最多 500 篇。
- **完整灾备/换机**：先正常停止 Java 进程，再将整个 `.data` 目录备份到受控、加密的存储；恢复到同一应用版本的数据目录，然后启动。不要在 H2 正在写入时直接复制数据库文件。完整备份含密码文件，必须像凭证一样保护。
- Markdown 导入可接收普通 `.md` 和本项目导出的 front matter；每次最多 50 个、单个最大 1MB，导入也始终私密。
- Flyway 已提供 `V1__initial.sql`。未来结构升级新增迁移文件，勿修改已部署的 V1。更新前保存完整备份。
- 换设备登录同一服务器即可访问同一份记录；本版没有离线协作或多人实时编辑。多设备冲突明确返回 409，不静默覆盖。

## 部署配置

已提供多阶段 `Dockerfile` 和 `compose.yaml`，容器以非 root 用户运行，持久卷保存数据库和附件：

```bash
docker compose up --build -d
```

Compose 也仅映射本机 8080，公开分享关闭。当前环境未执行 Docker 构建，因此容器部署尚未验证。

生产部署必须使用可信 HTTPS 反向代理，设置 `COOKIE_SECURE=true`、固定持久化磁盘与备份，并安全提供密码或 OIDC 凭证。反向代理限制请求体为 11MB，只信任你控制的转发头。不要直接向互联网暴露未配置 TLS 的 8080。

| 变量                    | 默认值                                           |
| ----------------------- | ------------------------------------------------ |
| `BIND_ADDRESS`          | `127.0.0.1`（容器为 `0.0.0.0`）                  |
| `PORT`                  | `8080`                                           |
| `NOTES_DATA_DIR`        | 从 backend 工作目录看的 `../.data`               |
| `PUBLIC_ACCESS_ENABLED` | `false`                                          |
| `COOKIE_SECURE`         | `false`（本地 HTTP 预览）；HTTPS 部署设置 `true` |
| `SEED_DEMO`             | `true`，只在空库初始化示范内容                   |
| `DB_PASSWORD`           | 空（本地嵌入式数据库，无远程监听）               |

当前验证的是本机 Java 进程和生产前端。对外部署、真实 OIDC 联调、HTTPS、跨设备公网连通与完整灾备恢复需要在实际目标环境再次验收。

## 代码结构

```text
backend/src/main/java/studio/liubai/  # 安全、REST、持久化、附件、示范数据
backend/src/main/resources/db/       # Flyway 数据迁移
backend/src/test/                    # 服务端与权限测试
frontend/src/App.vue                # 工作台、阅读、搜索、设置与空间导航
frontend/src/NoteEditor.vue          # 双模式编辑器
frontend/src/api.ts                  # 同源会话与 CSRF 客户端
frontend/src/markdown.ts             # Markdown/HTML 转换、清洗与高亮
frontend/src/style.css               # 响应式设计与主题
frontend/tests/                     # Playwright 浏览器验收
scripts/                            # 可重复安装与启动
```
