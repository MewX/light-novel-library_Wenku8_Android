# Wenku8 Android `v2.0` TTS 生态与竞品技术调研报告

**Companion Spec:** [`README.md`](./README.md)  
**Tracking Issue:** [#201 — v2.0 加入更多TTS自定义的功能](https://github.com/MewX/light-novel-library_Wenku8_Android/issues/201)

本报告记录 `docs/designs/v2/tts/README.md` 背后的实证调研数据，涵盖国产 ROM 系统 TTS 现状、开源阅读器生态、商用阅读器最佳实践、端侧开源神经网络推理（Sherpa-ONNX）以及 Google Play 合规边界。

---

## 一、国产手机系统 TTS 现状与适配「坑点」

### 1. 主流手机厂商系统 TTS 矩阵

| 厂商 / 系统 | 常见预装引擎包名 | 中文默认可用性 | 典型适配「坑点」 |
| :--- | :--- | :--- | :--- |
| **原生 Android / Pixel / 海外 GMS** | `com.google.android.tts` | 良（需联网下载/更新中文包） | `targetSdk >= 30` 无 `<queries>` 仍存在包可见性阻断；多音色需按 `zh-*` 过滤 |
| **小米 / Redmi (HyperOS / MIUI)** | `com.xiaomi.mibrain.speech` / `com.miui.voiceassist` 或讯飞定制 | 中（部分精简版/国际版缺失） | 语速参数对极值不敏感；「小爱同学语音唤醒」偶发抢占音频焦点；默认省电策略极易杀后台 |
| **华为 (HarmonyOS / EMUI)** | `com.huawei.hiai` / `com.huawei.vassistant` / 预装讯飞 | 中（新旧机型差异大） | `com.android.settings.TTS_SETTINGS` 常抛 `ActivityNotFoundException`；应用启动管理需允许后台活动 |
| **荣耀 (MagicOS)** | `com.honor.tts.engine` / 讯飞定制 | 中 | 同华为系，部分机型系统设置隐藏了 TTS 入口 |
| **OPPO / 一加 / 真我 (ColorOS)** | `com.heytap.speechassist` / `com.oppo.voicedecoder` 或讯飞/精简缺失 | 弱~中（大量机型无默认中文引擎） | 后台查杀激进；`isLanguageAvailable()` 有时误报可用但实际缺离线数据导致 `speak()` 静默失败 |
| **vivo / iQOO (OriginOS)** | `com.iflytek.speechsuite` / `com.vivo.multimedia.tts` / 百度 | 弱~中 | TTS 设置深埋于「无障碍」；息屏后易剥夺无前台服务的音频焦点 |
| **三星 (One UI)** | `com.samsung.SMT` + Google TTS | 良 | 三星国行常默认三星引擎（机械感强），用户普遍希望切至 Google 或第三方插件 |
| **AOSP 原生兜底** | `com.svox.pico` (Pico TTS) | **不可用（不支持中文）** | 仅支持部分西语，必须从自动回退链中降权/排除 |

### 2. 国产机安装 Google Speech Recognition & Synthesis 仍失败的四大原因
1. **Android 11+ `<queries>` 缺失**：未声明 `android.intent.action.TTS_SERVICE` 时，`targetSdk >= 30` 应用无法发现并绑定任何第三方/外部 TTS 服务。
2. **默认引擎未自动切换**：安装 `com.google.android.tts` 后系统不会自动更改 `Settings.Secure.TTS_DEFAULT_SYNTH`，App 仅用双参构造 `TextToSpeech(ctx, listener)` 仍会绑定到损坏或缺失的默认引擎。
3. **国内网络无法拉取 Google 中文离线包**：即使绑定成功，Google TTS 首次需从 Google 服务器下载中文语音模型；国内直连环境下下载停滞导致无声。
4. **国产系统设置跳转崩溃**：`com.android.settings.TTS_SETTINGS` 在华为、荣耀、OPPO、vivo 等定制 ROM 上常抛出 `ActivityNotFoundException`，需采用 `TTS_SETTINGS -> ACCESSIBILITY_SETTINGS -> LOCALE_SETTINGS -> SETTINGS` 四级安全降级。

---

## 二、开源阅读器生态调研（用户「想接入很多语音」的真实来源）

### 1. Android 标准系统 TTS 插件生态（`TextToSpeechService`）
大量 Android 听书用户并不要求阅读器内置某一家私有 SDK，而是习惯安装独立 TTS 引擎 APK，再由阅读器通过标准系统协议调用：

* **SherpaTTS (`org.woheller69.ttsengine`, `woheller69/ttsEngine`)**：F-Droid / GitHub 开源纯离线神经网络系统 TTS 引擎，基于 `sherpa-onnx`，去谷歌/高隐私设备首选。
* **VoxSherpa (`CodeBySonu95/VoxSherpa-TTS`)**：开源离线神经网络 TTS（支持 **Kokoro-82M** 与 **Piper/VITS** 双引擎），已上架 Google Play 与 GitHub Releases，支持多语种中文离线朗读。
* **tts-server-android (`com.github.jing332.tts_server_android`)**：国内开源阅读圈最活跃的聚合桥接引擎，将各类开源/云端/自定义规则包装为标准 Android 系统 TTS，支持旁白与对话角色分流、正则多音字修正。
* **MultiTTS (`org.nobody.multitts`)**：国内网文圈普及度极高的离线多发音人系统 TTS 引擎，支持导入离线语音包。

**结论**：阅读器只要做好 `<queries>` + App 内三参指定引擎绑定 + 音色选择与回退，即可零维护成本直接打通整个 Android 免费/开源 TTS 生态。

### 2. Legado（开源阅读）`HttpTTS` 与 OpenAI 兼容接口范式
* **Legado `HttpTTS` 机制**：通过声明式 JSON 配置（`url`、`header`、`contentType`、`concurrentRate` 及 `{{speakText}}`、`{{speakSpeed}}` 占位符）向本地/局域网/云端 HTTP 服务请求 MP3/WAV/OGG 音频流播放。
* **2025–2026 新收敛标准——OpenAI 兼容接口 (`POST /v1/audio/speech`)**：
  * 几乎所有新一代开源自建 TTS 服务都提供 OpenAI 兼容包装（如 **Kokoro-FastAPI**、**GPT-SoVITS**、**CosyVoice**、**IndexTTS**、**F5-TTS**、**Piper HTTP**），统一接受 `{ model, input, voice, speed, response_format }` 返回音频流。
  * **免费官方云层参考**：**Microsoft Azure Speech F0 Tier** 提供**每月 500,000 字符永久免费**的 Neural 语音合成额度（晓晓、云希、晓伊等高质量中文声线），适合个人 BYOK 零成本使用。
* **安全边界**：兼容 Legado 常见 HTTP 规则时，应只支持纯声明式变量替换，**不引入任意 JS `eval` 执行**，以避免 RCE 风险与商店安全审核问题。

### 3. 端侧开源神经网络 TTS（Sherpa-ONNX）可行性与包体评估
* **16 KB Page Size 合规性**：`k2-fsa/sherpa-onnx` 自 `v1.12.10+` 起已完整支持 Google Play 面向 Android 15+ 强制要求的 16 KB 内存页对齐。
* **主流开源中文模型实测指标**：
  * **`matcha-icefall-zh-en`**：约 50–150 MB，非自回归流匹配架构，手机 CPU 推理极快（旗舰/中端 RTF < 0.2），中英混读自然，低端机也可用。
  * **`kokoro-int8-multi-lang-v1_1`**：82M 参数 INT8 量化后约 80–150 MB，含 103 个说话人，自然度逼近云端大模型（旗舰 CPU RTF 约 0.2–0.4）。
  * **`vits-melo-tts-zh_en` / 社区 VITS**：体积极轻，老旧机型流畅。
* **为何不直接打包进主 APK**：
  * 多 ABI `sherpa-onnx.aar` 约 35–50 MB（单 `arm64-v8a` 亦约 12–18 MB），会令所有不听书的用户承担包体膨胀；
  * Google Play 严禁从外部 URL 下载可执行代码（`.so`/`.dex`），从自定义 URL 下载二进制模型权重亦存在自动化扫描误判风险；
  * **最佳实践**：通过**独立伴侣插件 APK**（`default` 走 GitHub Releases，`playstore` 独立上架 + Play Asset Delivery 或 Play Feature Delivery）解耦分发。

### 4. 商业 SDK 与非官方 Edge TTS 逆向接口的风险评估
* **开发者买单的商业云/离线 SDK**：不可行。按 1 万听书活跃用户日听 1 万字测算，月调用达 30 亿字，商业云成本高达数万至数十万元/月；商业离线 SDK 则按设备激活授权收费并含重型闭源 `.so`。
* **非官方微软 Edge TTS（WebSocket 逆向接口）**：**明确不内置**。微软在 2024–2026 年多次升级 `Sec-MS-GEC` 动态防刷签名，导致第三方客户端频繁出现 `403 Forbidden`；且逆向微软浏览器非公开接口在 Google Play Store 存在明确违规下架风险。

---

## 三、商用与标杆阅读器产品体验对标

| 产品 | 核心架构特点 | 值得 Wenku8 借鉴的最佳实践 |
| :--- | :--- | :--- |
| **微信读书** | 云端自研 AI + 双人「声伴」剧场 | 优雅的睡前定时（分钟 + 播完本章）、稳健的后台与锁屏卡片、沉浸式阅读同步 |
| **番茄小说** | 字节豆包大模型 + 服务端热点章 CDN 共享缓存 | 面向网文/轻小说的停顿与情感节奏、多角色人设音色（启发我们的轻小说旁白/对话分流） |
| **静读天下 (Moon+ Reader)** | 标准系统 TTS + 强大本地自定义 | 蓝牙/耳机线控切句、定时关闭、强大的多音字/角色称谓正则替换词典 |
| **Librera / ReadEra** | 深度适配 Android `TextToSpeech` 插件生态 | 应用内直选引擎与音色（无需改系统默认）、`TTS Replacements` 特殊符号过滤 |

### 对 Wenku8 轻小说场景的 6 项关键提炼
1. **实时试听预览**：配置引擎/音色/语速时必须能当场用轻小说范文试听，大幅降低调参试错成本。
2. **多级失效回退**：引擎挂了、缺包了、断网了不能直接报错摆烂，必须自动切备用引擎并从当前句续播。
3. **不打扰的阅读同步**：句级高亮 + 用户手动翻页时不强行拉回，提供「回到朗读位置」胶囊与长按段落起读。
4. **双缓冲预加载缓存（Phase 2）**：远程/自建语音必须提前预合成后 2 句并落盘 LRU 缓存，消除句间停顿与重复请求。
5. **轻小说文本清洗与词典（Phase 2）**：静默装饰分隔线（`───`、`* * *`、`◇`）与插图占位，支持多音字与日轻外来语人名矫正。
6. **旁白 / 对话双音色分流（Phase 3）**：日系轻小说对白引号（`「」`/`『』`/`“”`）高度规范，双音色分流是极具辨识度的听书杀手锏。
