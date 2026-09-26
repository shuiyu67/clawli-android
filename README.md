# Clawli 安卓端（clawli-android）

小狸（xiaoli-cli）的手机客户端——原生 Kotlin APK。

- **文本** 直接进 AI 对话
- **图片/文件** 上传落到电脑磁盘（`xiaoli-cli/clawli_files/`，不喂 AI）
- **工具调用** 状态实时展示（⚙ ✓/…）
- **双模式**：局域网直连（`IP:9079`）／云模式（经 `clawli-relay` 自建中继穿透 NAT）

## 安装

用 `app/build/outputs/apk/debug/app-debug.apk`（或 release 包）传到手机安装。
首次需允许"安装未知来源应用"。

## 使用

1. 电脑端启动小狸并打开 Clawli 服务（9079，设置密码）
2. 手机打开 Clawli App：
   - **局域网模式**：填 PC 的局域网 IP（同 WiFi）+ 密码 → 连接
   - **云模式**：填中继地址（`ws://服务器:8900`）+ 房间码 + 令牌 + 密码 → 连接
     （PC 端同时跑 `relay_client.py`，详见 [clawli-relay](../clawli-relay/README.md)）
3. 聊天框发消息=直接问 AI；点「文件」选图片/文件=上传到电脑

## 构建（开发）

```bash
# 需要 JDK 17+ 与 Android SDK (compileSdk 34)
./gradlew assembleDebug        # Windows: gradlew.bat assembleDebug
```

本机无 wrapper 二进制时可用全局 gradle：
`C:\tools\gradle-8.7\bin\gradle.bat assembleDebug`

## 协议

与 `xiaoli-cli/clawli_server.py` 对齐（详见 ClawliSocket.kt 头注释）：
`auth` → `auth_success`；`message` 进 AI；`file`（base64）落盘；
`text`/`tool_status`/`image` 为下行展示帧。
