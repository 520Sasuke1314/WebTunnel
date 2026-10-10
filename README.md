## 网页隧道 WebTunnel

> 把手机本地存储里的 HTML 页面，一键映射到公网。

**作者：Gnuning ｜ QQ 交流群：1101774655（点击复制）**

---

## 一、这是什么

一个 Android 应用，把手机上的 HTML 页面（单文件 / 整个文件夹 / 直接粘贴的源码 / 已在运行的服务）通过 **Pinggy SSH 隧道**发布到公网，外部设备用浏览器打开生成的 URL 即可访问。

不需要服务器、不需要域名备案、不需要公网 IP。选内容 → 点启动 → 复制地址，三步完成。

---

## 二、功能一览

| 功能 | 说明 |
| --- | --- |
| **四种内容来源** | ① 本地 HTML 文件 ② 本地文件夹（CSS/JS/图片整站发布，自动找 index.html） ③ 直接粘贴 HTML 代码 ④ 转发 URL（反向代理） |
| **本地 HTTP 服务** | 基于 NanoHTTPD，端口可自定义、被占用自动顺延，支持 CORS、目录索引、MIME 推断 |
| **Pinggy 隧道** | 基于 JSch 实现 `ssh -p 443 -R0:localhost:<port> a.pinggy.io`，自动解析 Pinggy 下发的公网地址 |
| **实时状态** | 连接中 / 已连接 / 已断开状态灯 + 呼吸动画 + 运行时长 + 实时流量统计（RB/SB/TC/AC） |
| **地址操作** | 一键复制、分享、浏览器打开、二维码（另一台设备扫码即开） |
| **运行日志** | 独立日志页，按级别过滤、自动滚动、一键复制/清空，涵盖 HTTP 请求 + SSH 输出 + 服务事件 |
| **后台保活** | 前台服务（Android 14 specialUse）+ 通知栏停止按钮 + 可选唤醒锁，进程回收后可自动恢复 |
| **参数可调** | 本地端口、SSH 服务器/端口、访问令牌、自定义子域名、主题（浅色/深色/跟随系统） |

界面风格：白色为主 + 大圆角卡片 + 柔和阴影 + 微交互动画，主页 / 日志 / 设置三页，操作路径最短为「选内容 → 点启动 → 复制」。

---

## 三、快速开始

### 环境要求

- Android 8.0（API 26）及以上
- 编译需要 JDK 17+、Android SDK

### 直接装 APK

到 [Releases](https://github.com/520Sasuke1314/WebTunnel/releases) 下载 APK 安装即可。

### 从源码构建

```bash
git clone https://github.com/520Sasuke1314/WebTunnel.git
cd WebTunnel
echo "sdk.dir=/你的/Android/sdk" > local.properties
./gradlew :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

### 用 Android Studio

1. `File → Open` 打开工程根目录
2. 等待 Gradle 同步（AGP 9.4.1 / Gradle 9.8 / Kotlin 2.4.20 / Compose BOM 2026.08.00）
3. 连接手机 → 点运行

> **版本说明**：Compose BOM 2026.08 / lifecycle 2.11 要求 `compileSdk >= 37`。若你的本机 SDK 较旧，可降级依赖（如 `compose-bom:2024.09.02` + `activity-compose:1.9.2` + `core-ktx:1.13.1`），并把 `compileSdk` 改为 34。`targetSdk 34` / `minSdk 26` 保持不变即可。

---

## 四、使用说明

### 主页三步走

1. **选择内容来源**
   - `本地文件`：选一个 `.html`，适合单文件页面
   - `本地文件夹`：选网页所在目录（**推荐**），目录内 CSS/JS/图片都会发布；有 `index.html` 时自动作为首页，否则生成目录索引页
   - `HTML 代码`：直接写/粘贴源码，可点「载入示例页面」体验
   - `转发 URL`：把已在运行的服务（如 `http://127.0.0.1:9000`）反向代理到公网
2. 确认「快速参数」（本地端口、SSH 端口，默认 8080 / 443）
3. 点 **启动隧道** → 状态卡出现公网地址（`https://xxxx.free.pinggy.net` 之类）→ 复制 / 分享 / 二维码

### 说明

- 生成地址后应用会自动复制到剪贴板（可在设置关闭）
- Pinggy 免费版会同时给出两个域名，第一个作为主地址，另一个作备用
- 停止方式：主页按钮 / 通知栏「停止隧道」
- 退出应用或锁屏后隧道继续运行（前台服务），通知栏常驻显示状态

---

## 五、Pinggy 配置说明

### 命令行对照

| 应用设置 | 对应命令 | 说明 |
| --- | --- | --- |
| SSH 服务器（默认 `a.pinggy.io`） | `ssh -p 443 ... a.pinggy.io` | 也可用 `free.pinggy.io` 等官方服务器 |
| SSH 端口（443 / 22） | `-p 443` | 443 更像 HTTPS 流量，不易被网络限制；不通时换 22 |
| 本地端口（默认 8080） | `-R0:localhost:8080` | 应用先在本机监听该端口，再映射出去 |
| 访问令牌（可选） | `TOKEN@a.pinggy.io` | 令牌就是 SSH 用户名；免费使用留空 |
| 自定义子域名（可选） | `-R <子域名>:0:localhost:8080` | Pro 付费能力；套餐不支持时自动回退为随机域名 |

等价命令：

```bash
ssh -p 443 -R0:localhost:8080 a.pinggy.io              # 免费版
ssh -p 443 -R0:localhost:8080 <TOKEN>@a.pinggy.io      # 登录后
```

### 免费版限制（实测）

- 隧道**单次最长 60 分钟**，到期 Pinggy 会主动断开，重新点启动即可
- 公网地址**随机生成**，形如 `https://xxxx.free.pinggy.net`
- 无需注册即可使用；登录 [dashboard.pinggy.io](https://dashboard.pinggy.io) 可拿到访问令牌

### 应用怎么拿到公网地址

应用连接后打开一条空命令的 SSH `exec` 通道，Pinggy 把信息打印到标准输出，应用逐行解析：

```
Allocated port 3 for remote forward to localhost:8080
You are not authenticated.
Your tunnel will expire in 60 minutes. Upgrade to Pinggy Pro ...
https://xxxx-....run.pinggy-free.link
https://xxxx-....free.pinggy.net
RB: 0, SB: 0, TC: 0, AC: 0     ← 下行/上行字节、累计连接数、当前连接数
```

---

## 六、权限说明（均为最小必要）

| 权限 | 类型 | 用途 |
| --- | --- | --- |
| `INTERNET` / `ACCESS_NETWORK_STATE` | 安装即授予 | 连接 Pinggy、反向代理目标 |
| `POST_NOTIFICATIONS` | 动态申请（Android 13+） | 前台服务状态通知；拒绝后隧道仍可运行 |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | 安装即授予 | Android 14 要求声明前台服务类型 |
| `WAKE_LOCK` | 安装即授予 | 「锁屏保持运行」开启时申请局部唤醒锁 |
| 存储 | **无需危险权限** | 通过 SAF 拿到可持久化 Uri，符合分区存储规范 |

---

## 七、项目结构

```
app/src/main/java/com/gnuning/webtunnel/
├── WebTunnelApp.kt              # Application：创建 AppContainer（手写依赖容器，无 Hilt/KSP）
├── MainActivity.kt              # 唯一 Activity
├── core/                        # AppInfo / AppContainer / Logging / Models / Settings / SafUtil / ContentSource
├── server/                      # LocalWebServer（NanoHTTPD）· ReverseProxy（OkHttp）
├── tunnel/                      # PinggyTunnel（JSch）· TunnelController（状态机）
├── service/                     # TunnelService（前台服务）· NotificationHelper
├── ui/                          # AppViewModel · RootScreen · theme · components · screens
└── util/                        # Formatters · MimeTypes · ShareUtil
```

### 常见二次开发点

- **换隧道服务商**：只改 `tunnel/PinggyTunnel.kt`（连接与地址解析集中在这里）
- **换本地服务器**：改 `server/LocalWebServer.kt`，或换成 Ktor
- **加内容来源**：在 `core/ContentSource.kt` 增加一种，并在 `LocalWebServer.serve()` 加分支
- **加设置项**：`core/Settings.kt` 加字段（读、写、key 三处）+ `SettingsScreen.kt` 加控件

---

## 八、技术栈

Kotlin 2.4.20 · Jetpack Compose（Material 3，BOM 2026.08.00）· AGP 9.4.1 / Gradle 9.8 ·
NanoHTTPD 2.3.1 · JSch(mwiede) 2.28.8 · OkHttp 4.12.0 · ZXing 3.5.3 ·
minSdk 26（Android 8.0）/ targetSdk 34（Android 14）

---

## 九、构建验证

| 项目 | 结果 |
| --- | --- |
| `:app:assembleDebug` | ✅ 构建成功，约 20 MB |
| `:app:assembleRelease` | ✅ 构建成功（R8 压缩 + 资源裁剪），约 1.9 MB |
| APK 校验 | ✅ compileSdk 37 / targetSdk 34 / minSdk 26，权限与应用名正确 |
| 隧道逻辑 | ✅ 免费隧道实测，地址解析正则与 RB/SB/TC/AC 统计解析与实际输出一致 |
| 端到端 | ✅ 公网地址可从外部网络直接访问本地 HTTP 服务（返回 200） |

---

## 十、常见问题

| 现象 | 排查 |
| --- | --- |
| 长时间卡在「建立隧道」 | 检查网络；SSH 端口从 443 换 22；或填访问令牌后重试（详见日志页） |
| 提示端口被占用 | 应用会自动顺延（日志有提示），也可手动改高位端口 |
| 外部打不开地址 | 确认状态灯为「已连接」、地址复制完整（含 `https://`）；免费隧道 60 分钟后失效 |
| 页面能开但样式/图片 404 | 「本地文件」只发布单个文件，改用「本地文件夹」选整个目录 |
| 反向代理接口 404/跨域 | 检查目标地址为 `http://IP:端口` 形式；WebSocket 不支持 |
| 锁屏后掉线 | 打开「锁屏保持运行」，允许后台运行 / 关闭电池优化 |

---

## 十一、免责声明

本工具仅用于本地开发调试、内网服务临时共享、教学演示等合法场景。
请勿通过公网隧道发布违法违规内容或他人隐私数据；因使用不当造成的一切后果由使用者自行承担。

---

## 十二、联系

- **作者**：Gnuning
- **QQ 群**：`1101774655`（点击复制后到 QQ 搜索加入）
- **问题反馈**：[Issues](https://github.com/520Sasuke1314/WebTunnel/issues)

如果这个项目对你有帮助，欢迎 Star ⭐

---

## 许可证

[MIT License](LICENSE) © 2026 Gnuning
