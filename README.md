# TV ADB 遥控

一个安卓 app：扫描局域网内开启了 ADB（5555 端口）的电视/盒子，连接后可以当遥控器用，还能管理电视上的应用。

## 功能

- **局域网扫描**：一键扫描当前 Wi-Fi 子网，列出所有开放 5555 端口的设备；也可以手动输入 IP 连接；常用设备可保存。
- **遥控器**：方向键/OK、返回、主页、菜单、电源、音量±、频道±、播放/暂停；还能向电视输入文字（搜索框打字不用再逐字选）。
- **应用管理**：列出电视上安装的应用（可切换显示系统应用），打开、强停、清数据、看详情、卸载；从手机选 APK 直接安装到电视。
- **设备信息与截图**：品牌/型号/Android 版本/分辨率/存储/内存/电量；一键截取电视当前画面；重启电视、打开电视设置。
- **ADB 授权**：内置纯 Kotlin 实现的 ADB 协议（含 RSA 授权）——电视若弹出「允许调试」对话框，在电视上确认即可。无第三方 ADB 库依赖。

## 使用前提

电视/盒子需要开启网络 ADB 调试（多数盒子在「开发者选项」里叫“网络调试”或“USB 调试”，端口 5555），手机与电视连同一个 Wi-Fi。

## 构建

用 Android Studio 打开本项目即可（Gradle 8.7 / AGP 8.5.2 / Kotlin 2.0.20，minSdk 26，targetSdk 34）。或命令行：

```bash
./gradlew assembleDebug
```

> 注：仓库里没有包含 gradle-wrapper.jar，在 Android Studio 打开时会自动生成；命令行构建前先运行一次 `gradle wrapper`。

## 自动编译与发布（GitHub Actions）

- 每次 push 到 `main`：自动编译 debug + release APK，在 Actions 运行页可下载产物（Artifacts）。
- 打版本标签时自动发布 Release：

  ```bash
  git tag v1.0
  git push origin v1.0
  ```

  完成后在仓库 Releases 页会出现签好名的 release APK，可直接下载安装；后续版本用同一密钥签名，可覆盖升级。

- 发布签名用的密钥库在 `ci/release-keystore.b64`（PKCS12，base64 文本），密码写在 `.github/workflows/build.yml` 里——这是个人自用 app 的专用密钥，只用于给这个 app 签名。若以后在意，可把密钥库和密码改放到 GitHub Secrets。

## 结构

- `adb/AdbClient.kt` — ADB 线协议客户端（CNXN/AUTH/OPEN/WRTE/CLSE）
- `adb/AdbKeyManager.kt` — RSA 密钥生成、令牌签名、ADB 公钥格式编码
- `net/DeviceScanner.kt` — /24 子网 5555 端口扫描
- `vm/MainViewModel.kt` — 连接管理、遥控、应用管理、设备信息
- `ui/` — Jetpack Compose 界面（设备 / 遥控 / 应用 / 信息 四页）
