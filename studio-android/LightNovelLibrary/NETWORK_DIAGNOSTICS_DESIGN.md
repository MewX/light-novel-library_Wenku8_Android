# Network diagnostics for mainland-China latency

Status: proposed design, 2026-11-07; revised after codebase/runtime review. This document does not implement the UI or change routing.

## Objective and evidence

Add **设置 → 网络检测** so someone in mainland China can run a useful benchmark on their phone, inspect the result, and share it without installing developer tools. The objective is to reduce the latency people experience when using Wenku8. Worker execution time alone is insufficient to choose a routing change.

The relay analysis for **2026-11-06 04:30 UTC through 2026-11-07 00:40 UTC**, end exclusive, provides the starting point:

| Observation | Result | Implication |
| --- | --- | --- |
| Requests classified as CN | 782,729 over 20 h 10 min | Includes mainland evening peak; this is a request count, not a user count. |
| CN requests with a usable transport RTT | 99.94% | Server-side network observations have broad coverage. |
| Successful, allowed CN cache hits | Relay p50 6 ms; p95 12 ms | Processing a hit is already inexpensive. |
| Successful, allowed CN cache misses | Relay p50 69 ms; p95 345 ms | Origin work still matters for misses. |
| CN ingress distribution | About 53.9% AMS/LHR; 44.0% SEA/SJC/LAX | Requests often enter Cloudflare far from China, while execution is predominantly in Taipei. |
| CN transport RTT by family | IPv4 p50 254 ms; IPv6 p50 228 ms | Aggregate differences do not establish that switching a particular phone to IPv6 helps. |

These are sample-weighted Analytics Engine observations with `blob16 = 'latency-v1'`. CN is Cloudflare's IP-country classification, not proof of physical location. The placement observation comes from a separate Workers dataset. Neither dataset measures the complete operation on the phone. Same-ingress placement cohorts currently favor keeping Smart Placement, but their action/cache mixes are not matched.

The new screen should answer:

1. Can this phone reliably reach the configured relay?
2. How long does receiving a small response take, including connection setup when it occurs?
3. Does another address family or transport improve results **on this same network**?
4. Once an experimental route exists, does it improve representative app requests as well as a tiny probe?

## Scope and delivery order

**MVP:** a foreground, user-started test against a small diagnostic endpoint on the app's configured relay hostname; use the existing platform HTTP stack, show individual outcomes and a concise summary, and export a local report. This establishes client-side reachability and latency without making Wenku8 origin requests.

**Next:** add an explicitly labeled comparison transport for controlled IPv4/IPv6 and HTTP/1.1/HTTP/2 tests, then an optional edge endpoint and a bounded real API test. These require additional implementation and must not appear as working tests in the MVP.

Tests do not change the app's selected relay, force a system-wide address family, or automatically upload results. A diagnostic success means the diagnostic endpoint worked; it does not establish login, search, or chapter availability.

## Screen and interaction

Add a 48 dp settings row near “检查更新” and “关于” in `fragment_config.xml`. Open an internal `NetworkDiagnosticsActivity` using the existing `BaseMaterialActivity`, Java/XML, Material components, and app theme. Support font scaling and screen readers. Add every string to `values/strings.xml`, `values-zh-rTW/strings.xml`, and `values-zh-rHK/strings.xml`.

The base activity/theme does not currently implement e-ink behavior. When `GlobalConfig.isEinkModeEnabled()` is true, this screen must explicitly disable indeterminate progress animation and expand/collapse transitions, use high-contrast static status text, and avoid continuous redraws. Status must be understandable without color or animation in every mode.

### Before starting

```text
← 网络检测

检查当前网络连接中转服务的速度和稳定性。

当前网络       Wi-Fi / 移动网络 / 未知
VPN            已检测到 / 未检测到 / 无法判断
测试服务       当前使用的中转服务

快速检测
通常约 15–30 秒，最久 60 秒。
仅传输少量诊断数据，不读取账号或书架。

[ 开始检测 ]
```

Show an offline explanation instead of starting when there is no available default network. Lack of Android's “validated” capability is a hint, not an absolute reason to refuse a reachable relay. Treat VPN/proxy information as best-effort metadata, not proof of a direct route. Do not request location or phone-state permission to identify a carrier.

If the relay configuration is unavailable, show “此版本未配置检测服务” and disable Start. This is a supported state for public `api-stub` builds, not a connection failure. No URL parsing exception or private/JNI API call may escape screen creation.

### Running

```text
← 网络检测

正在检测中转连接 · 已完成 4 / 10 次
已用时 8 秒

连接状态       检测中
成功 / 失败    4 / 0

[ 停止检测 ]
```

The counts above illustrate layout only. Show completed attempts rather than a fabricated percentage of time remaining. Keep the screen responsive. Stopping retains completed results and marks the report incomplete. If the active network changes, stop and ask the user to start a new run on the new network; do not combine Wi-Fi and mobile samples.

### Results

```text
← 网络检测

检测完成 / 部分完成 / 未能连接
当前网络 · 测试时间

中转连接
成功           — / — 次
完整响应中位数 — ms
最快 / 最慢    — / — ms
响应头到达     — ms（中位数）

此结果测量当前网络到中转服务的连接。
不包含书籍服务器处理和章节加载时间。

[ 查看详情 ]
[ 复制摘要 ]  [ 分享报告 ]
[ 重新检测 ]
```

Do not assign an unexplained “network score” or claim that a family/route is always faster. Show failures alongside latency so a fast but unreliable configuration cannot win by discarding failed attempts. With no successful samples, show “—”, never zero milliseconds.

Details expand to individual attempts, response-header/full-response timings, outcome, ingress location, observed HTTP protocol, and server-observed address family where available. Separate first and subsequent attempts, but do not label them “cold” and “warm” unless connection creation/reuse was actually observed. Advanced comparison controls appear only when implemented.

## MVP measurement protocol

| Item | Decision |
| --- | --- |
| Target | Exact configured relay hostname, with a dedicated diagnostic path; a fixed, trusted application configuration supplies it. |
| Transport | A session-free `HttpURLConnection` adapter following the app's normal POST transport, with the differences below recorded in the test profile. |
| Attempts | Up to 10 sequential logical requests; a 1-second gap between attempts for both success and failure. Skip the gap after the final attempt, on cancellation, or when out of time. No application retries; underlying connection attempts are not fully observable. |
| Time limits | Existing POST limits: 3-second connect and 5-second read timeout; additionally enforce a 10-second attempt deadline and a 60-second run deadline. |
| Payload | Small versioned JSON request with a fresh nonce per attempt; bounded JSON response, no book content. |
| Success | HTTP 200, expected schema and echoed nonce, bounded complete body, valid field types. A login page or redirect is not success. |
| Statistics | Successful count, failure count by category, and successful full-response minimum/median/maximum. Show first attempt separately. No p95 for a 10-sample run. |
| Repeatability | Report the profile version, exact target hostname, transport, limits, sample order, and network identity changes. |

Use `SystemClock.elapsedRealtimeNanos()` for elapsed measurements. Save UTC wall-clock time separately for correlation. Record call start, response headers available, and full body consumed. “Response headers available” is the observable platform boundary; do not market it as a precise first-byte timestamp. Include upload and any connection establishment in call-to-header time.

Store each elapsed duration as integer nanoseconds in the report. Sort the successful durations; an odd-count median is the middle value, and an even-count median is the arithmetic mean of the middle two. Export derived summaries in decimal milliseconds without rounding the inputs (an even-count median can have half-nanosecond precision). Round to integer milliseconds using half-up rounding only for display. Apply the same rule to successful call-to-header times. Zero successes yields null summaries; cancelled or unstarted attempts do not become zero-duration samples.

The platform adapter cannot reliably expose separate DNS, TCP, and TLS durations or socket reuse through its public interface. Store these as unavailable. A separate DNS lookup would warm the resolver and would not measure DNS within the HTTP call, so do not run one before the baseline or fabricate a stacked timing chart from independent probes.

The platform's keep-alive pool can be shared with earlier in-process app requests. Attempt 1 may therefore reuse a connection opened while browsing. Report `connectionReuse = null` for this adapter rather than assuming a fresh socket or trying to clear a global pool.

Use the normal system resolver and HTTPS hostname verification. Disable redirect following for diagnostics so redirected HTML cannot silently become a successful measurement. Disable adapter use of local HTTP response caches, and require the endpoint's `no-store` policy. These are intentional differences from ordinary app requests and belong in the profile description.

Set `Content-Type: application/json` and `setFixedLengthStreamingMode(requestBytes.length)` before connecting, write the exact UTF-8 bytes, and close the output stream. This avoids the buffered request-body replay path; it is not a portable guarantee of one socket attempt, since connection establishment/recovery can occur before sending the body. Keep application retries disabled, report platform attempt counts as unknown, and include all elapsed work in the logical request. Preserve an HTTP status exposed by `HttpRetryException` without replaying authentication or redirects. See the [streaming API contract](https://developer.android.com/reference/java/net/HttpURLConnection#setFixedLengthStreamingMode(int)) and [AOSP recovery logic](https://android.googlesource.com/platform/external/okhttp/+/ba6e9e9/okhttp/src/main/java/com/squareup/okhttp/internal/http/HttpEngine.java).

Call `getResponseCode()` before selecting a response stream; timestamp headers immediately when it returns a valid status. For 200, read `getInputStream()`; for an error, read a bounded `getErrorStream()` if present. A 3xx remains a redirect failure even if it has a normal input stream. Classify 429 as `rate_limited` and other non-200 statuses as `http_failure`, preserving the status if body reading also fails. `getErrorStream()` may be null. Never turn a known HTTP error into `unknown_io` merely because `getInputStream()` would throw.

Request `Accept-Encoding: identity`, and require absent/identity response content encoding for a successful probe. Read at most 2 KiB plus one overflow-detection byte, including for error bodies. Export `responseBodyBytesRead` and `bodyComplete`; the count covers bytes exposed by the body stream, excluding headers, HTTP framing, TLS, and transport overhead. It is **not total wire traffic**. Reject unexpected compression as an invalid successful response; a known HTTP error keeps its HTTP classification. Record identity encoding as another intentional difference from the app's gzip requests.

Do not invoke `LightNetwork.LightHttpPostConnection()` directly: it sends `PHPSESSID` and updates `LightUserSession` from responses. The probe must neither attach account cookies nor read/write that session. Verify that process-wide cookie/authenticator hooks cannot add credentials to diagnostic requests; do not change global handlers to achieve isolation. If that cannot be guaranteed with the platform adapter, resolve it before shipping and document any necessary transport change.

### Scheduling, cancellation, and stalled DNS

Use one serialized coordinator for run state and deadlines, separate from a process-owned, bounded I/O pool with at most **two occupied call slots** and no unbounded work queue. Normally only one logical attempt is active. The second slot allows a later user-started run to proceed if an earlier cancelled call is still blocked in a native resolver. Do not create replacement pools on Activity recreation or each restart.

On cancellation/deadline, the coordinator marks the attempt terminal immediately and initiates best-effort `disconnect()` plus stream closure without blocking the UI/coordinator. Cleanup and lingering I/O must remain resource-bounded; do not spawn unlimited cleanup threads. A slot remains occupied until its underlying work actually exits, not merely until its Future reports cancellation. Ignore late results using run/attempt identifiers, and check cancellation before later writes whenever control returns from blocking platform work.

If a timed-out attempt has not exited, stop its run with partial results instead of scheduling more samples behind it. A new run may start only when a call slot is free. If both slots are occupied, show “上次检测仍在结束，请稍后重试” and refuse admission without queueing or extending the pool. This bounds resource use even when DNS is not interruptible; the UI deadline cannot guarantee that the OS has stopped all network work.

The total deadline includes gaps and all setup work. Distinguish timeout, DNS failure, TLS failure, HTTP failure, rate limiting, invalid response, network change, and user cancellation using stable report codes; retain an `unknown_io` category when the platform cannot identify the stage.

## Relay endpoint prerequisite

Propose **`POST /__diagnostics/latency`** on the same hostname and Worker deployment used by the app. The current relay redirects non-POST requests and rejects non-root POST paths, so probing `GET /` is not a valid health test. Add an explicit handler before those generic guards and before API decoding, session handling, sinkhole processing, and normal analytics. Preserve existing behavior on every other path.

Request and response shape (illustrative, not a captured result):

```json
{ "schema": "wenku8-network-probe-v1", "nonce": "<32 lowercase hex characters>" }
```

```json
{
  "schema": "wenku8-network-probe-v1",
  "nonce": "<same nonce>",
  "endpointRole": "placed-relay",
  "ingressColo": null,
  "country": null,
  "asn": null,
  "clientIpFamily": "ipv6",
  "httpProtocol": "HTTP/1.1",
  "transportRttMs": null,
  "transportRttSource": null,
  "handlerElapsedMs": 0
}
```

- Accept only this fixed schema and nonce format. Bound the streamed request to 256 bytes, including when `Content-Length` is absent. Return 400 for malformed input, 413 for an oversized body, and 405 for unsupported methods on this path.
- Return `Content-Type: application/json` and `Cache-Control: no-store`; cap the response at 2 KiB. The nonce is for response matching, not authentication or cache-busting Wenku8 content.
- Perform **zero outbound fetches**, cache writes, archive reads, session updates, or background tasks. Diagnostic traffic must not enter the normal validated-request Analytics Engine dataset or its sinkhole/cache ratios.
- Use Cloudflare-supplied metadata and the existing client-IP extraction helper; emit only address family, never the IP itself. Represent missing fields as JSON `null`, not invented values. RTT is optional, positive, and labeled TCP or QUIC according to the available applicable source.
- `ingressColo` is the request's Cloudflare entry location, not a claim about the Worker's execution location. `endpointRole` describes deployment configuration. Do not hardcode “Taipei” as an observed execution location.
- `handlerElapsedMs` covers only this handler. Client elapsed time minus handler elapsed time still includes multiple network/platform stages; it is not a measurement of a single network hop. Keep the diagnostic handler's measurement scope separate from existing API relay timing.
- No account authentication is needed for this bounded, fixed-response endpoint. It must not accept destination URLs or arbitrary actions. Retain applicable perimeter protections and return a visible rate-limited outcome for HTTP 429; the client must not retry around it.

An endpoint on the placed Worker can still incur placement forwarding. It is deliberately a probe of the deployed relay path. A later **separate Worker with placement disabled** can provide an edge-only comparison. Give that endpoint an explicit role and version. A different hostname can change DNS and anycast routing, so paired results compare complete endpoint configurations, not an isolated placement effect.

Do not use arbitrary malformed API requests, changing page numbers, or random book identifiers for measurements. Those can exercise sinkhole behavior or create extra origin traffic and undermine the result.

## Advanced comparisons after the MVP

Use an isolated client with event instrumentation, such as OkHttp, if detailed connection timing and address-family control are needed. Select an Android-21-compatible release during implementation and verify its official API documentation. The app currently uses `HttpURLConnection`; changing the production transport is a separate decision.

| Profile | Purpose | Required labeling |
| --- | --- | --- |
| Platform / automatic family | Baseline closest to the current app's transport | Platform timings only; reuse and detailed phases may be unknown. |
| Comparison client / automatic family | Detect effects of changing the HTTP client | Experimental transport, with its exact version. |
| Same comparison client / IPv4 only | Compare address families within one client | IPv4-filtered system resolution; actual outcome recorded. |
| Same comparison client / IPv6 only | Compare address families within one client | IPv6-filtered system resolution; absence of an eligible address is unavailable, not 0 ms. |
| Same comparison client / HTTP/1.1 versus HTTP/2 offered | Evaluate protocol negotiation and reuse | Report negotiated protocol; an HTTP/1.1 fallback is not an HTTP/2 success. |

Run one comparison axis at a time. For fresh-connection comparisons, interleave individual sequential samples in counterbalanced order. For connection-reuse comparisons, use separate pools per arm and short counterbalanced bursts, for example A×3, B×3, B×3, A×3, with the same specified intra-burst and inter-burst gaps. Label each burst's first request separately and record actual reuse/idle duration where observable; do not silently discard setup requests. Single-request alternation can give one pool extra idle time while the other arm runs, so it is not the default reuse experiment.

Keep timeouts, body size, endpoint, and other client configuration identical within a comparison. Cap a detailed run at 60 attempts and 120 seconds; report truncation. Do not concurrently flood the connection or multiply every option into an unbounded matrix.

For a fresh-connection profile, use a fresh client-owned connection pool; explain that OS DNS and TLS session caches can remain warm. For reuse tests, keep the pool and record actual reuse when observable. Include connection attempts and fallback/retries in the sample; never count only the final successful socket's duration. Event phases may overlap or repeat, so show the event sequence instead of assuming every duration adds into a simple stack.

Force family only in this comparison client's resolver/socket selection, preserving the hostname for TLS/SNI. Never use an IP-literal HTTPS URL, disable certificate validation, or change process-wide DNS/network binding. NAT64/CLAT and proxies can make the local socket family differ from the family Cloudflare observes; record those separately where available. Do not call an IPv6 route usable merely because DNS returned an AAAA record.

A later real API profile should use a fixed, small, public read-only operation with an explicit request cap and normal cache keys. Select the operation after auditing its side effects and authentication requirements. No login, votes, reading-progress updates, or cache-busting requests. Cache-hit/miss labels require trustworthy response metadata added separately; infer neither from response speed. This profile is necessary before claiming an improvement in chapter/search performance.

Real API requests depend on private request encoding (`Wenku8API.getEncryptedCV()` and JNI `TokenManager.getMagicVersion()`). Put that profile behind an explicit capability/configuration interface: `api-stub` shows “此版本不支持实际接口检测” without calling stub methods or loading JNI. The basic diagnostic profile needs no private request encoding. Unit/UI tests inject a fake API-profile implementation; production integration tests are a separate opt-in suite.

## Report and sharing

Keep the completed report in memory for the screen session. Retain it across rotation using a lifecycle-owned model; a process restart can start fresh. No background upload or persistent device identifier is needed.

“复制摘要” produces readable text. “分享报告” first previews included fields, then uses Android's share sheet for a JSON attachment plus a short summary. Write attachments only to app-private cache and grant temporary read access through a narrowly configured `FileProvider`; expire old attachments on the next diagnostics launch, with a proposed 24-hour lifetime.

The preview explicitly shows Cloudflare-observed **country and ASN** and explains that ASN can identify an ISP or VPN/proxy provider; it need not identify the phone's physical mobile carrier. This network metadata is included even when the user adds no city/carrier note.

Use `ACTION_SEND` with MIME type `application/json`, `EXTRA_STREAM`, `setClipData(ClipData.newRawUri("", contentUri))`, and `FLAG_GRANT_READ_URI_PERMISSION` on the send intent before wrapping it with `Intent.createChooser`. Grant no write permission. Verify that a separate receiving app can open the attachment through the chooser on API 21–22 and a recent Android version. See [FileProvider URI grants](https://developer.android.com/reference/androidx/core/content/FileProvider).

The report schema, `wenku8-network-report-v1`, contains:

- Run-local random ID, UTC start time, local time-zone offset, completion/stop reason, profile version, app version/build flavor, Android API level, and transport version where applicable.
- Network transport type, metered status, VPN/proxy presence where observable, and whether a network transition interrupted the run. Do not export platform network handles.
- Target hostname and endpoint role, configured limits, ordered samples, attempt start offsets, elapsed timings, response status, `responseBodyBytesRead`, `bodyComplete`, and stable error categories.
- Optional server-provided country, ASN, ingress colo, protocol, family, and transport RTT. Missing values remain null with an availability explanation where useful.
- Derived summaries with both attempted and successful denominators. Failures and cancellations remain in the sample list; do not serialize stack traces or arbitrary error messages that might contain addresses.

Exclude raw/local/public IPs, SSID/BSSID, device serials, account information, cookies, tokens, book content, and browsing history. Normal server-side access logging is separate from this local-report promise. An optional user-entered note can identify a city/carrier for comparison; explain that it will be included when shared and never infer it from permissions.

### JSON compatibility contract

JVM tests use `org.json:json:20231013`, whereas devices use Android's `org.json`. For nullable fields, check `has(key)`/`isNull(key)` before reading and validate the raw value's type. Avoid coercing strings/numbers through `optString`, `optLong`, or `optDouble` defaults. Android can turn `JSONObject.NULL` into the literal string `"null"` through `optString(key, null)`, while the JVM library returns the fallback. Missing optional fields normalize to null; missing required schema/nonce fields are invalid.

When serializing, use `json.put(key, value == null ? JSONObject.NULL : value)` for explicitly nullable fields. Passing Java null removes the key. A literal `"null"` is not an acceptable location/protocol value, and null RTT must not become zero. Add the same nullable-field/type fixtures to JVM tests and device instrumentation, covering absent keys, explicit null, valid values, wrong types, and export round trips. Sources: [Android JSONObject](https://android.googlesource.com/platform/libcore/+/refs/heads/main/json/src/main/java/org/json/JSONObject.java) and [the JVM test version](https://github.com/stleary/JSON-java/blob/20231013/src/main/java/org/json/JSONObject.java).

## Android integration

Proposed locations relative to this directory:

| File or area | Responsibility |
| --- | --- |
| `app/.../fragment/ConfigFragment.java` | Open the diagnostics activity from settings. |
| `app/src/main/res/layout/fragment_config.xml` | Add the settings entry using existing row styling. |
| `app/.../activity/NetworkDiagnosticsActivity.java` | Render states, handle start/stop/details/share, and explicitly apply locale, e-ink behavior, and the active-run screen flag. |
| `app/src/main/res/layout/activity_network_diagnostics.xml` | Screen layout and accessible result rows. |
| `app/.../diagnostics/NetworkDiagnosticsViewModel.java` | Own run state across rotation; cancel when the owning screen is permanently closed. |
| `app/.../diagnostics/DiagnosticsRunner.java` | Scheduling, deadlines, network-change handling, and immutable results with injected clock/transport. |
| `app/.../diagnostics/PlatformProbeTransport.java` | Bounded, session-free HTTP implementation. |
| `app/.../diagnostics/DiagnosticsEndpointConfig.java` | Safely resolve a configured endpoint or return an unavailable state; inject configuration in tests. |
| `app/.../diagnostics/DiagnosticsNetworkMonitor.java` | API-gated default-network observations and stable transition detection. |
| `app/.../diagnostics/DiagnosticsReport.java` and formatter | Versioned data model, statistics, redacted JSON and text export. |
| Manifest, strings, and optional provider paths XML | Non-exported activity, translations, and restricted report sharing. |

Here `app/...` abbreviates `app/src/main/java/org/mewx/wenku8`; these are proposed new class names. Check existing lifecycle dependencies before choosing the ViewModel implementation. Keep the runner and report model independently testable; do not add a large UI framework or refactor unrelated networking to build this screen.

### Endpoint configuration and CI

The real `Wenku8API.BASE_URL` is `https://wenku8-relay.mewx.org/`; the stub sets it to `Wenku8API.UNKNOWN` (`"Unknown"`). CI deliberately uses `api-stub`. The production configuration adapter must return an unavailable result for null, empty, UNKNOWN, malformed, non-absolute, or non-HTTPS configuration, with no fallback to a hardcoded production hostname. Note that `URI.create("Unknown")` can parse a relative URI successfully; successful parsing alone is not validation.

Validate a host and reject user-info, query, and fragment. Resolve the absolute path with `baseUri.resolve("/__diagnostics/latency")` after validation, retaining the configured origin and avoiding double slashes from string concatenation. Test bases with and without a trailing slash. No configuration parsing or native API operation should throw during Activity/ViewModel construction.

Inject both endpoint configuration and transport before Activity launch in instrumentation tests, and reset the test injection afterward. Use fake transports for UI/runner tests and local controlled servers for adapter tests. All core diagnostics tests must run on CI without `RealApi.require()` and without reaching production, even on a developer checkout with the private module present. Keep the new app classes in normal JaCoCo coverage; do not exclude the feature to compensate for stub behavior.

### Connectivity and lifecycle

The project supports Android API 21 and above and already declares Internet/network-state permissions. Gate the [ConnectivityManager APIs](https://developer.android.com/reference/android/net/ConnectivityManager) as follows:

| Android version | Observation strategy |
| --- | --- |
| API 24+ | `registerDefaultNetworkCallback(callback)` plus active-network snapshots. The overload accepting a Handler requires API 26; serialize callback events onto the coordinator. |
| API 23 | `getActiveNetwork()` and `getDefaultProxy()` are available; use general callbacks as hints to re-evaluate the default network and check before/after each attempt. |
| API 21–22 | Use `getActiveNetworkInfo()` plus `getAllNetworks()`/capabilities for best-effort transport/VPN observation. General `registerNetworkCallback(NetworkRequest, callback)` events are not default-network events. Recheck active state before/after attempts; proxy/default-handle details may be unavailable. |

Do not claim exact default-network identity when API 21–22 cannot establish it; report reduced transition-detection capability. An unrelated available network or VPN must not be assumed to carry the probe. Register observations once per run and unregister on termination, without leaking callbacks across rotation.

Establish an initial network baseline before sending probes. Stop for loss of that default network, a different default `Network` identity, or a confirmed change in the default connection's underlying transport set or VPN attachment. The first callback establishes/confirms the baseline; it is not itself a transition. Ignore bandwidth/signal updates and routine capability changes on the same route. Callback absence or temporarily unavailable metadata is not proof of a VPN/transport change. Recheck around requests and mark the in-flight sample interrupted when a transition is detected.

Render visible underlying Wi-Fi/cellular/Ethernet transport separately from VPN state. If multiple underlying transports are reported, show a combined label rather than choosing one arbitrarily; if only VPN is visible, show the underlying type as unknown. Do not guess an underlying carrier from ASN.

`MainActivity.initialApp()` currently applies the user's SC/TC preference locally through `updateConfiguration`; diagnostics must not rely on that call surviving Activity recreation. Apply the selected locale to this Activity's configuration context before inflating resources, including after rotation, using an API-21-compatible approach. Preserve the existing preference semantics and avoid a project-wide locale migration in this feature.

While a run is active and this Activity is visible, set `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON`; otherwise a normal 15/30-second display timeout can prematurely stop it. Clear the flag on completion, cancellation, failure, and leaving the screen; restore it on recreation only if the run is still active. This is a window flag, with no CPU wake lock or foreground service. See [Android screen-on guidance](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on).

Backgrounding the app stops an active test; rotation does not. Use visibility/lifecycle handling that distinguishes configuration recreation from a genuine stop, rather than cancelling every `onPause`. Pressing Home, Back, or locking the phone still stops the run. Handle share-sheet launch after the run finishes. On return, show any partial report and make restarting explicit.

## Validation and acceptance criteria

1. Runner tests cover cancellation, deadlines, late callbacks, a stuck resolver across restart, pool saturation, and release only after actual I/O completion. Verify between-attempt gaps after success/failure, no final gap, all-failure/mixed outcomes, odd/even medians, rounding, and failure denominators. Use fake time/transport, not live servers in unit tests.
2. Adapter tests cover valid response, malformed/oversized body, wrong nonce, redirects, HTTP 400/405/413/429/500 with and without error streams, unexpected encoding, slow/truncated bodies, and cookie/session isolation. Verify fixed-length streaming, response-code-first timing, bounded reads/cleanup, known-status preservation, and no application retries. Do not assert platform-internal connection-attempt counts that the adapter cannot observe.
3. Relay tests prove the endpoint performs no origin/cache/session work, emits no normal analytics event, enforces limits, handles missing metadata, and leaves existing API routes unchanged.
4. UI/device checks cover API 21–22, API 23, and API 24+ boundaries, rotation, short display timeout, explicit lock/Home/Back cancellation, and clearing/restoring the screen flag. Check Wi-Fi/mobile/VPN transitions, harmless capability updates, offline/unconfigured states, and language preference after recreation. Verify large text, TalkBack, static e-ink presentation, required build variants, and production-free `api-stub` instrumentation without skipped core tests.
5. Inspect exported JSON for excluded data, explicit nulls, exact field types, and summaries matching raw durations. Run null fixtures on both JVM and Android. Verify the preview discloses country/ASN and a separate receiving app can read the shared attachment through the chooser with read-only permission. Check URI normalization and unavailable-config behavior with fake configuration/transport.
6. Run on at least one mainland device. Repeat on Wi-Fi and mobile data as separate reports, ideally at both evening peak and a quieter time. Document actual sample coverage rather than generalizing from one phone.

## How results guide the latency work

| Result | Next experiment or investigation |
| --- | --- |
| Small relay probe is slow while handler time is tiny | Investigate client/Cloudflare route and placement forwarding; use a paired edge endpoint comparison. |
| One family consistently improves latency and reliability on the same network | Evaluate an opt-in connection-selection experiment; confirm across carriers and regions before changing defaults. |
| Reused connections materially improve the comparison client | Investigate connection pooling and protocol support in actual app operations. |
| Probe is fast but representative API reads are slow | Correlate cache outcomes and origin timings; focus on the API path rather than claiming a network fix. |
| Proposed edge-cache route improves tiny probes | Test real cached reads and misses next; a tiny probe does not establish cache-hit rate or overall improvement. |

For each route/transport comparison, preserve ordered samples and compare matched runs on the same device/network. Report reliability and typical latency first. Aggregate enough sessions for tail-latency analysis; a ten-request report cannot establish a stable p95. Do not subtract unrelated population medians or attribute an aggregate IPv6 advantage to the family alone.

Suggested PR sequence: **relay diagnostic endpoint → Android MVP → controlled comparisons → evidence-backed routing experiment**. Keep Smart Placement in place while gathering evidence. The likely routing experiment is serving cache hits at ingress while preserving placed execution for misses, but it should be evaluated against client-visible timings and cache behavior before rollout.

## References

- Existing app implementation: `api/src/main/java/org/mewx/wenku8/network/LightNetwork.java`, `app/src/main/java/org/mewx/wenku8/fragment/ConfigFragment.java`, and `app/src/main/res/layout/fragment_config.xml`.
- Relay latency instrumentation: [wenku8_relay PR #98](https://github.com/MewX/wenku8_relay/pull/98).
- [Cloudflare Worker placement](https://developers.cloudflare.com/workers/configuration/placement/) and [request metadata](https://developers.cloudflare.com/workers/runtime-apis/request/).
- [Cloudflare Cache API](https://developers.cloudflare.com/workers/runtime-apis/cache/) for location-local cache behavior when designing the later routing experiment.
- [Android network-state guidance](https://developer.android.com/develop/connectivity/network-ops/reading-network-state) for lifecycle-aware connectivity observation.
