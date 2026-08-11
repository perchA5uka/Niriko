package com.otakup.niriko.plugin.bilibili

/**
 * 哔哩哔哩同步 WebView 注入脚本。
 *
 * 移植自 Bangumi-master `src/screens/web-view/bilibili-sync/component/login/utils.ts`，
 * 并针对大追番量（数百部）做了三点增强：
 * 1. **受限并发**：`pgc/review/user` 从串行改为并发 [REVIEW_CONCURRENCY] 条，大幅缩短拉取时间；
 * 2. **进度上报**：每完成一批（[PROGRESS_BATCH] 条）postMessage `REVIEW_PROGRESS`，
 *    UI 可渲染进度条，避免"不确定是否还在拉取"；
 * 3. **超时容错**：XHR 设 [REQUEST_TIMEOUT_MS] 超时，非 2xx 一律 resolve(null)，
 *    单个请求悬挂/失败不会卡死整条拉取链路。
 *
 * **作用域注意**：所有辅助函数（xhr/postMessage/getList/getReviews 等）都声明为
 * **顶层函数**（而非嵌套在某个 init 函数体内），确保互相引用不因作用域问题
 * 抛出 ReferenceError（上一版把 getList/getReviews 定义在 init 函数体内，
 * 导致 checkLogin 调用 getList 时找不到符号，拉取链在登录后即断裂、页面永远
 * 停在"正在获取数据"，这是本次修复的重点）。
 *
 * 流程：WebView 加载 `https://m.bilibili.com/space?from=headline`（保持用户登录态），
 * 注入脚本在页面上下文中用同源 XHR（withCredentials）请求带 Cookie 的接口：
 *   1. `x/web-interface/nav` — 检查登录，取 `wallet.mid`；
 *   2. `x/space/bangumi/follow/list` — 分页拉取追番列表（ps=15 循环直到不足一页）；
 *   3. `pgc/review/user` — 并发拉取用户评分与短评。
 * 结果统一 `window.NirikoBridge.postMessage(JSON.stringify({type, data}))` 回传 App。
 * 顶层包裹 try/catch：任何异常都会以 `DEBUG_ERROR` 消息回传，便于定位。
 *
 * 安全：脚本为本地常量（非远程加载）；App 不读取/导出 Cookie，
 * 数据仅在 WebView 会话内使用，符合「不绕过授权流程」的要求。
 */
object BilibiliWebBridge {

    const val HOST_API = "https://api.bilibili.com"

    /** 移动端空间页（承载登录态；已登录用户直接通过）。 */
    const val HOST_M = "https://m.bilibili.com"
    const val URL_ZONE = "$HOST_M/space?from=headline"

    /** 配套脚本消息类型（与 App 侧常量保持一致，仅供注入 JS 内使用）。 */
    const val MSG_CHECK_LOGIN = "CHECK_LOGIN"
    const val MSG_GET_LIST = "GET_LIST"
    const val MSG_GET_REVIEW = "GET_REVIEW"
    const val MSG_REVIEW_PROGRESS = "REVIEW_PROGRESS"
    const val MSG_ERROR = "DEBUG_ERROR"

    /** follow/list 每页条数（与参考实现一致）。 */
    private const val PAGE_SIZE = 15

    /** 评分拉取并发数（B 站接口限频安全阀）。 */
    private const val REVIEW_CONCURRENCY = 5

    /** 每完成 N 条上报一次进度。 */
    private const val PROGRESS_BATCH = 5

    /** 单请求超时（毫秒）。 */
    private const val REQUEST_TIMEOUT_MS = 15_000

    /**
     * 重置注入幂等 guard。供 UI "重新获取"时调用：
     * 先 evaluateJavascript 清掉 __nirikoBiliInjected，再 reload 页面，脚本才会重新 bootstrap。
     */
    fun resetInjected(): String = "window.__nirikoBiliInjected = false;"

    /**
     * 注入脚本全文。通过占位符注入 URL/参数，避免 Kotlin 模板字符串冲突。
     */
    fun injectedJavaScript(): String {
        val script = """
            (function() {
              /* ========== 幂等 guard：防止 onPageFinished 多次触发导致脚本重跑 ==========
                 注入脚本用 evaluateJavascript 调用，SPA 页面会多次触发 onPageFinished，
                 若不加 guard，__list/__page/getList 的全局状态会被重置、多个 bootstrap 并发翻页，
                 GET_LIST 消息会丢/重 → 页面永远卡在"正在获取数据"。
                 首次注入后置位，后续注入直接 return（函数定义仍在，但不会重新 bootstrap）。 */
              if (window.__nirikoBiliInjected) { return; }
              window.__nirikoBiliInjected = true;

              var __timeoutId = null;
              var __isBridgeOk = false;
              var __list = [];
              var __page = 1;

              /* 兜底：Promise 回调里抛出的异常（wallet 缺失等）外层 try/catch 捕不到，
                 统一挂在 unhandledrejection 上报，避免拉取链静默断裂 */
              window.addEventListener('unhandledrejection', function(e) {
                var err = e && e.reason;
                window.NirikoBridge && window.NirikoBridge.postMessage(JSON.stringify({
                  type: '__MSG_ERROR__',
                  data: 'unhandledrejection: ' + String(err && err.stack ? err.stack : err)
                }));
              });

              /* 纵深防御：非 bilibili 域（外链跳转后残留注入）直接停手，不 bootstrap */
              var __host = (location && location.hostname || '').toLowerCase();
              if (__host !== 'bilibili.com' && __host.indexOf('.bilibili.com') < 0) {
                return;
              }

              function waitForBridge() {
                if (!__isBridgeOk && !window.NirikoBridge.postMessage) {
                  __timeoutId = setTimeout(waitForBridge, 400);
                } else {
                  clearTimeout(__timeoutId);
                  __timeoutId = null;
                  __isBridgeOk = true;

                  setTimeout(function() {
                    try {
                      bootstrap();
                    } catch (e) {
                      window.NirikoBridge.postMessage(JSON.stringify({
                        type: '__MSG_ERROR__',
                        data: String(e && e.stack ? e.stack : e)
                      }));
                    }
                  }, 4000);
                }
              }

              waitForBridge();
            }());

            function bootstrap() {
              checkLogin();
            }

            function xhr(opts) {
              var method = (opts.method || 'get').toLowerCase();
              return new Promise(function(resolve) {
                var request = new XMLHttpRequest();
                /* 超时:单请求 15s 未完成视为失败,避免整条拉取无限等待 */
                request.timeout = __TIMEOUT_MS__;
                request.onreadystatechange = function () {
                  if (this.readyState === 4) {
                    if (this.status >= 200 && this.status < 300) {
                      try {
                        resolve(JSON.parse(this.responseText));
                      } catch (e) {
                        /* 响应不是 JSON（风控页/验证码等）——把现场带回去,便于调用方定化上报 */
                        resolve({
                          __xhrError: true,
                          status: this.status,
                          text: String(this.responseText || '').substr(0, 300)
                        });
                      }
                    } else {
                      /* 非 2xx（412 风控/429 限频等）同样带现场 */
                      resolve({
                        __xhrError: true,
                        status: this.status,
                        text: String(this.responseText || '').substr(0, 300)
                      });
                    }
                  }
                }.bind(request);
                request.onerror = function () {
                  resolve({ __xhrError: true, status: -1, text: 'network error' });
                }.bind(request);
                request.ontimeout = function () {
                  resolve({ __xhrError: true, status: -2, text: 'timeout' });
                }.bind(request);
                request.onabort = function () {
                  resolve({ __xhrError: true, status: -3, text: 'aborted' });
                }.bind(request);
                request.open(method, opts.url, true);
                request.withCredentials = true;
                request.send(null);
              });
            }

            function postMessage(type, data) {
              window.NirikoBridge.postMessage(JSON.stringify({ type: type, data: data }));
            }

            /* 统一上报 XHR 失败现场（状态码/超时/非 JSON 响应前 300 字符） */
            function reportXhrError(tag, data) {
              if (data && data.__xhrError) {
                postMessage('__MSG_ERROR__', tag + ' 请求失败 status=' + data.status + ' body=' + data.text);
                return true;
              }
              return false;
            }

            function ts() {
              return String(new Date() / 1);
            }

            function checkLogin() {
              xhr({ url: '__HOST_API__/x/web-interface/nav' }).then(function(data) {
                if (reportXhrError('nav', data)) return;
                if (data) postMessage('__MSG_CHECK_LOGIN__', data);
                if (data && data.data && data.data.isLogin) {
                  /* mid 兼容：新版在 data.mid，旧版（参考实现）在 data.wallet.mid；
                     兜底从 data 平级字段找 mid，取不到时上报错误而不是静默卡死 */
                  var d = data.data;
                  var mid = d.mid || (d.wallet && d.wallet.mid) || data.mid || 0;
                  if (!mid) {
                    postMessage('__MSG_ERROR__', 'nav 登录成功但未返回 mid: ' + JSON.stringify(d).substr(0, 300));
                    return;
                  }
                  getList(mid);
                }
              });
            }

            function getList(mid) {
              xhr({
                url: '__HOST_API__/x/space/bangumi/follow/list?type=1&follow_status=0&pn=' + __page +
                     '&ps=__PAGE_SIZE__&vmid=' + String(mid) + '&ts=' + ts()
              }).then(function(data) {
                if (reportXhrError('follow/list', data)) return;
                if (!data || !data.data) {
                  postMessage('__MSG_ERROR__', 'follow/list 返回结构异常: ' + JSON.stringify(data).substr(0, 300));
                  return;
                }
                if (data.code !== 0) {
                  postMessage('__MSG_ERROR__', 'follow/list 返回 code=' + data.code + ' message=' + String(data.message || ''));
                  return;
                }
                if (!data.data.list) {
                  postMessage('__MSG_ERROR__', 'follow/list data 中无 list 字段: ' + JSON.stringify(data.data).substr(0, 300));
                  return;
                }
                data.data.list.forEach(function(item) {
                  var match = String(item.progress || '').match(/(\d+)话/);
                  __list.push({
                    id: item.media_id,
                    seasonId: item.season_id,
                    title: item.title,
                    cover: item.cover,
                    status: item.follow_status,
                    progress: match ? Number(match[1]) : 0,
                    total: item.total_count
                  });
                });

                if (data.data.list.length >= __PAGE_SIZE__) {
                  __page += 1;
                  getList(mid);
                } else {
                  postMessage('__MSG_GET_LIST__', __list);
                  var mediaIds = __list.map(function(item) { return item.id; });
                  getReviews(mediaIds);
                }
              });
            }

            function getReviews(mediaIds) {
              var reviews = {};
              var total = mediaIds.length;
              var done = 0;
              var index = 0;

              function reportProgress() {
                if (done % __BATCH__ === 0 || done >= total) {
                  postMessage('__MSG_REVIEW_PROGRESS__', { done: done, total: total });
                }
              }

              function worker() {
                if (index >= mediaIds.length) return;
                var mediaId = mediaIds[index++];
                xhr({
                  url: '__HOST_API__/pgc/review/user?media_id=' + String(mediaId) + '&ts=' + ts()
                }).then(function(data) {
                  /* 单个评分请求失败不阻塞整体：跳过该条继续 */
                  if (data && !data.__xhrError && data.result && data.result.review && data.result.review.short_review) {
                    var short_review = data.result.review.short_review;
                    reviews[mediaId] = {
                      score: short_review.score || 0,
                      content: short_review.content || ''
                    };
                  }
                  done += 1;
                  reportProgress();
                  if (done >= total) {
                    postMessage('__MSG_GET_REVIEW__', reviews);
                    return;
                  }
                  worker();
                });
              }

              for (var i = 0; i < __CONCURRENCY__ && i < mediaIds.length; i++) {
                worker();
              }
            }
        """.trimIndent()
        return script
            .replace("__HOST_API__", HOST_API)
            .replace("__PAGE_SIZE__", PAGE_SIZE.toString())
            .replace("__CONCURRENCY__", REVIEW_CONCURRENCY.toString())
            .replace("__BATCH__", PROGRESS_BATCH.toString())
            .replace("__TIMEOUT_MS__", REQUEST_TIMEOUT_MS.toString())
            .replace("__MSG_CHECK_LOGIN__", BILI_MSG_CHECK_LOGIN)
            .replace("__MSG_GET_LIST__", BILI_MSG_GET_LIST)
            .replace("__MSG_GET_REVIEW__", BILI_MSG_GET_REVIEW)
            .replace("__MSG_REVIEW_PROGRESS__", BILI_MSG_REVIEW_PROGRESS)
            .replace("__MSG_ERROR__", BILI_MSG_DEBUG_ERROR)
    }
}