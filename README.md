# 网页隧道 WebTunnel

把**手机本地存储里的 HTML 页面**（或文件夹、HTML 源码、已运行的本地服务）通过 **Pinggy SSH 隧道**映射到公网，
外部设备用浏览器直接打开公网 URL 即可访问。

> **作者：Gnuning** ｜ **QQ 交流群：1101774655**
> 应用内「设置 → 关于」里点击群号即可一键复制，再去 QQ 搜索加入。

---

## 一、功能一览

| 功能 | 说明 |
| --- | --- |
| 四种内容来源 | ① 本地 HTML 文件 ② 本地文件夹（CSS/JS/图片整站发布） ③ 直接粘贴 HTML 代码 ④ 转发 URL（反向代理） |
| 本地 HTTP 服务 | 基于 **NanoHTTPD**，端口可自定义，被占用时自动顺延；支持 CORS、目录索引、MIME 推断 |
| Pinggy 隧道 | 基于 **JSch** 实现 `ssh -p 443 -R0:localhost:<port> a.pinggy.io`，自动解析 Pinggy 下发的公网地址 |
| 实时状态 | 连接中/已连接/已断开 状态灯 + 运行时长 + **实时流量统计**（Pinggy 会周期性推送 `RB/SB/TC/AC`） |
| 地址操作 | 一键复制、分享、浏览器打开、**二维码**（另一台设备扫码即开） |
| 运行日志 | 独立日志页，按级别过滤、自动滚动、一键复制/清空（HTTP 请求 + SSH 隧道输出 + 服务事件） |
| 后台保活 | 前台服务（Android 14 `specialUse` 类型）+ 通知栏停止按钮 + 可选唤醒锁；进程被回收后可自动恢复隧道 |
| 参数可调 | 本地端口、SSH 服务器/端口（443 或 22）、访问令牌、自定义子域名、主题（浅色/深色/跟随系统）等 |

界面风格：**白色为主 + 大圆角卡片 + 柔和阴影 + 微交互动画**，主页 / 日志 / 设置三页，操作路径最短为「选内容 → 点启动 → 复制」。

---

## 二、快速开始

### 1. 直接用 Android Studio
1. `File → Open` 打开本工程根目录（`web-tunnel`）；
2. 首次打开等待 Gradle 同步（工程使用 AGP 9.4.1 / Gradle 9.8 / Kotlin 2.4.20 / Compose BOM 2026.08.00）；
3. 连接手机（Android 8.0+）→ 点击运行。

> **版本说明**：Compose BOM 2026.08 / lifecycle 2.11 要求 `compileSdk >= 37`，所以工程里 `compileSdk = 37`。
> 若你的本机只装了较旧的 SDK，请把依赖一起降级，例如：
> `compose-bom:2024.09.02` + `activity-compose:1.9.2` + `lifecycle-*:2.8.6` + `core-ktx:1.13.1`，并把 `compileSdk` 改为 34。
> `targetSdk = 34`（Android 14）、`minSdk = 26`（Android 8.0）在任何组合下都保持不变即可。

### 2. 命令行构建
```bash
# 需要 JDK 17+ 与 Android SDK
echo "sdk.dir=/你的/Android/sdk" > local.properties
./gradlew :app:assembleDebug            # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease          # 已配置 R8 混淆规则
```

### 3. 依赖仓库加速（可选）
`settings.gradle.kts` 里已经内置了阿里云镜像，并把 `google()` / `mavenCentral()` 放在后面兜底：

```kotlin
maven { url = uri("https://maven.aliyun.com/repository/public") }
maven { url = uri("https://maven.aliyun.com/repository/google") }
```

如果你的网络可以直连 Google Maven，删掉这两行即可；反之保留能显著加快依赖下载。
`gradle/wrapper/gradle-wrapper.properties` 也把 Gradle 发行包指向了腾讯云镜像，直连顺畅时可换回 `services.gradle.org`。

---

## 三、使用说明

### 主页三步走
1. **选择内容来源**
   * `本地文件`：选择一个 `.html`，适合单文件页面；
   * `本地文件夹`：选择网页所在目录（**推荐**），目录内 CSS/JS/图片都会被发布；目录里有 `index.html` 时自动作为首页，否则给出目录索引页；
   * `HTML 代码`：直接写/粘贴源码，可点「载入示例页面」体验；
   * `转发 URL`：把已在运行的服务（如 `http://127.0.0.1:9000`、局域网地址、公网接口）反向代理到公网。
2. 确认「快速参数」（本地端口、SSH 端口，默认 8080 / 443），需要更多选项点右上角进「设置」；
3. 点 **启动隧道** → 状态卡出现公网地址（`https://xxxx.free.pinggy.net` 之类）→ 复制 / 分享 / 二维码，任意外部设备打开即可。

### 说明
* 生成地址后应用会（可在设置里关闭）**自动复制**到剪贴板；
* Pinggy 免费版会同时给出两个域名，第一个作为主地址，另一个列在下方作备用；
* 停止方式：主页按钮 / 通知栏「停止隧道」；
* 退出应用或锁屏后隧道继续运行（前台服务），通知栏常驻显示状态。

---

## 四、Pinggy 配置说明

### 1. 命令行对照表（应用里的设置项 = 命令行参数）

| 应用设置 | 对应命令 | 说明 |
| --- | --- | --- |
| SSH 服务器（默认 `a.pinggy.io`） | `ssh -p 443 ... a.pinggy.io` | 也可用 `free.pinggy.io` 等官方服务器 |
| SSH 端口（443 / 22） | `-p 443` | 443 更像 HTTPS 流量，不易被网络限制；443 不通时换 22 |
| 本地端口（默认 8080） | `-R0:localhost:8080` | 应用先在本机监听该端口，再把它映射出去 |
| 访问令牌（可选） | `TOKEN@a.pinggy.io` | **令牌就是 SSH 用户名**；免费使用留空（此时用户名用占位值，Pinggy 会忽略） |
| 自定义子域名（可选） | `-R <子域名>:0:localhost:8080` | 属于 **Pro 付费能力**；套餐不支持时应用会自动回退为随机域名 |

即应用执行的核心动作等价于：

```bash
ssh -p 443 -R0:localhost:8080 a.pinggy.io                 # 免费版
ssh -p 443 -R0:localhost:8080 <你的TOKEN>@a.pinggy.io      # 登录后（更长会话 / 固定域名）
```

### 2. 免费版限制（实测）
* 隧道**单次最长 60 分钟**，到期后 Pinggy 会主动断开，应用会提示「隧道已断开」，重新点启动即可；
* 公网地址**随机生成**，形如：
  * `https://xxxx-xxxx-....free.pinggy.net`
  * `https://xxxx-....run.pinggy-free.link`
* 无需注册即可使用；登录 [dashboard.pinggy.io](https://dashboard.pinggy.io) 可拿到访问令牌，享受 Pro 套餐权益。

### 3. 应用是怎么拿到公网地址的（便于排错/二次开发）
应用连接后会打开一条空命令的 SSH `exec` 通道，Pinggy 会把信息打印到它的标准输出，应用逐行解析：

```
Allocated port 3 for remote forward to localhost:8080
You are not authenticated.
Your tunnel will expire in 60 minutes. Upgrade to Pinggy Pro ...
https://xxxx-....run.pinggy-free.link
https://xxxx-....free.pinggy.net
RB: 0, SB: 0, TC: 0, AC: 0          ← 周期性推送：下行/上行字节、累计连接数、当前连接数
```

* 公网地址：正则匹配 `*.pinggy.net / *-free.link / *.pinggy.link / *.pinggy.online / *.pinggy.io`；
* `RB/SB/TC/AC` 会实时显示在主页状态卡上（下行 / 上行 / 请求数 / 在线连接）；
* 隧道断开（含 60 分钟到期）会触发通道 EOF，应用据此更新状态并提示。

### 4. 算法兼容性
为了不引入 BouncyCastle，应用只启用 Android/Conscrypt 原生支持的算法：
`curve25519-sha256`（不支持时 JSch 会自动剔除）→ `ecdh-sha2-nistp256/384/521` → `diffie-hellman-group14-sha256`；
主机密钥优先 `ecdsa-sha2-nistp256`，加密使用 `aes-ctr / aes-gcm`，MAC 使用 `hmac-sha2`。
如后续需要 ed25519 主机密钥或更强算法，可按 README 末尾说明补充 BouncyCastle 依赖。

---

## 五、权限说明（均为最小必要）

| 权限 | 类型 | 用途 |
| --- | --- | --- |
| `INTERNET` / `ACCESS_NETWORK_STATE` | 安装即授予 | 连接 Pinggy、反向代理目标 |
| `POST_NOTIFICATIONS` | **动态申请（Android 13+）** | 前台服务状态通知；拒绝后隧道仍可运行，只是没有通知 |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | 安装即授予 | Android 14 要求声明前台服务类型（本地 Web 服务 + SSH 隧道属于 `specialUse`） |
| `WAKE_LOCK` | 安装即授予 | 「锁屏保持运行」开启时申请局部唤醒锁，保证后台不掉线 |
| 存储 | **无需危险权限** | 通过系统文件选择器（SAF）拿到可持久化 Uri，符合分区存储规范，不申请 `READ_EXTERNAL_STORAGE` |

---

## 六、项目结构（二次开发指引）

```
app/src/main/java/com/gnuning/webtunnel/
├── WebTunnelApp.kt              # Application：创建 AppContainer（手写依赖容器，未用 Hilt/KSP）
├── MainActivity.kt              # 唯一 Activity：边到边 + 注入 ViewModel + 系统栏明暗
├── core/
│   ├── AppInfo.kt               # 版本 / 作者 / QQ 群常量
│   ├── AppContainer.kt          # 依赖容器
│   ├── Logging.kt               # 日志仓库（内存环形缓冲 + StateFlow + logcat）
│   ├── Models.kt                # ServerMode / TunnelStatus / TunnelState / TunnelConfig
│   ├── Settings.kt              # 设置模型 + SharedPreferences 持久化（同步读写 + StateFlow）
│   ├── SafUtil.kt               # SAF 目录遍历（直接用 DocumentsContract，无额外依赖）
│   └── ContentSource.kt         # 「发布什么」的抽象 + 启动前校验
├── server/
│   ├── LocalWebServer.kt        # NanoHTTPD：四种来源的响应逻辑、目录索引、CORS、MIME
│   └── ReverseProxy.kt          # OkHttp 反向代理（含 Location 重写、流式响应）
├── tunnel/
│   ├── PinggyTunnel.kt          # JSch 连接 Pinggy、请求远程转发、解析公网地址与流量统计
│   └── TunnelController.kt      # 总控：状态机（STARTING→CONNECTING→CONNECTED→IDLE/ERROR）
├── service/
│   ├── TunnelService.kt         # 前台服务：常驻通知、停止按钮、进程回收后自动恢复
│   └── NotificationHelper.kt    # 通知渠道与常驻通知
├── ui/
│   ├── AppViewModel.kt          # 三个页面共用：状态暴露 + 启动/停止/复制/分享动作
│   ├── RootScreen.kt            # Scaffold + 悬浮底部导航 + 页面切换动画
│   ├── theme/                   # Material 3 主题（浅色为主，含深色）、字体、圆角
│   ├── components/Widgets.kt    # SoftCard / 状态点 / 胶囊选项 / 主按钮 / 二维码 / 底部导航等
│   └── screens/                 # HomeScreen（主页）· LogsScreen（日志）· SettingsScreen（设置）
└── util/                        # 格式化、MIME 表、剪贴板/分享/打开链接
```

常见二次开发点：
* **换隧道服务商**：只需改 `tunnel/PinggyTunnel.kt`（连接与地址解析逻辑集中在这里）；
* **换本地服务器**：改 `server/LocalWebServer.kt`，或换成 Ktor/NanoWSD；
* **加新的内容来源**：在 `core/ContentSource.kt` 增加一种 `ContentSource`，并在 `LocalWebServer` 的 `serve()` 里加分支；
* **加设置项**：`core/Settings.kt` 加字段（读、写、key 三处）+ `SettingsScreen.kt` 加控件。

### 需要更强 SSH 算法时（可选）
```kotlin
implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
```
并在 `PinggyTunnel` 的 `init` 里恢复默认算法列表（或显式加入 `ssh-ed25519`），同时在 `proguard-rules.pro` 保留 `org.bouncycastle.**`。

---

## 七、构建验证记录

本工程在真实环境中完成了**编译验证**（不是「看起来能跑」）：

| 项目 | 结果 |
| --- | --- |
| `:app:assembleDebug` | ✅ 构建成功，产物 `app/build/outputs/apk/debug/app-debug.apk`（约 19 MB） |
| `:app:assembleRelease` | ✅ 构建成功（R8 压缩 + 资源裁剪），产物 `app/build/outputs/apk/release/app-release-unsigned.apk`（约 1.9 MB） |
| APK 校验 | ✅ `compileSdk 37` / `targetSdk 34` / `minSdk 26`、权限与「网页隧道」名称均正确写入 AndroidManifest |
| 隧道逻辑 | ✅ 已用真实账号无关的免费隧道实测：`ssh -p 443 -R0:localhost:<port> a.pinggy.io` 能拿到公网地址，且地址解析正则、`RB/SB/TC/AC` 统计解析均与 Pinggy 实际输出一致 |
| 端到端 | ✅ 实测公网地址可从外部网络直接访问到本地 HTTP 服务（返回 200） |

编译工具链（与本机已验证版本一致）：**AGP 9.4.1 · Gradle 9.8 · Kotlin 2.4.20 · JDK 17+ · Compose BOM 2026.08.00**。

### （可选）在 Android 设备上的 proot 工作区里编译
如果是在 Android 手机上的 Linux 工作区里构建，需要把 aapt2 指到本机可执行的版本（否则 AGP 会去下载 x86_64 的 aapt2）：

```bash
./gradlew :app:assembleDebug \
  -Pandroid.aapt2FromMavenOverride=/opt/android-sdk/build-tools/37.0.0/aapt2
```

---

## 八、常见问题

| 现象 | 排查 |
| --- | --- |
| 长时间卡在「建立隧道」 | 手机网络是否可用；把 SSH 端口从 443 换成 22；或填写访问令牌后重试（详见日志页） |
| 提示端口被占用 | 应用会自动顺延端口（日志里有提示），也可以手动改一个高位端口 |
| 外部打不开地址 | 确认状态灯为「已连接」、地址复制的完整（含 `https://`）；免费隧道 60 分钟后地址会失效 |
| 页面能开但样式/图片 404 | 「本地文件」模式只发布单个文件，请改用「本地文件夹」模式选择整个目录 |
| 反向代理接口 404/跨域 | 检查目标地址为 `http://IP:端口` 形式；表单类 POST 会由 NanoHTTPD 解析后重建（见 `ReverseProxy` 注释）；WebSocket 不支持 |
| 锁屏后掉线 | 打开设置里的「锁屏保持运行」，并允许本应用后台运行 / 关闭电池优化 |

---

## 九、技术栈

Kotlin 2.4.20 · Jetpack Compose（Material 3，Compose BOM 2026.08.00）· AGP 9.4.1 / Gradle 9.8 ·
NanoHTTPD 2.3.1 · JSch(mwiede) 2.28.8 · OkHttp 4.12.0 · ZXing 3.5.3 ·
minSdk 26（Android 8.0）/ targetSdk 34（Android 14）。

## 十、免责声明

本工具仅用于本地开发调试、内网服务临时共享、教学演示等合法场景。
请勿通过公网隧道发布违法违规内容或他人隐私数据；因使用不当造成的一切后果由使用者自行承担。

---

**作者：Gnuning ｜ QQ 群：1101774655（点击复制）**
