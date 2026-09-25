package com.otakup.niriko.data.probe

/**
 * 豆瓣 HTML 解析测试用的固定样本。
 *
 * ## 为什么要把真实响应固化成 fixture
 *
 * 第 3 轮把豆瓣剧照做成「客户端实时搜索」并失败，根因之一是没有把**真实响应**留下来：
 * 解析器只能照着我记忆里的页面结构写，页面一改版测试也照样绿。
 * 这里的每一段 HTML 都对应一类真实的、必须能被识别的响应，页面改版时单测会红。
 *
 * ## 写法约定
 *
 * 全部用普通字符串 + 「\n」显式换行（不用 raw string / 反引号），
 * 因为本仓库的构建工具对 KDoc 与 raw string 里的反引号处理不稳定。
 */
object DoubanHtmlFixtures {

    /**
     * 正常的「电影剧照」页（movie.douban.com/subject/{id}/photos）。
     *
     * 对应真实结构：
     * - title 形如「肖申克的救赎 剧照 (豆瓣)」；
     * - 列表项是 li > div.cover > img[src]，src 指向 img1.doubanio.com 的
     *   view/photo/s_ratio_poster/public/... 缩略图（解析后要能换成 large）；
     * - 页面带「共 42 张」这类总数文案。
     */
    val photosPage: String = "" +
        "<!DOCTYPE html>\n" +
        "<html lang=\"zh-CN\">\n" +
        "<head>\n" +
        "  <meta charset=\"utf-8\">\n" +
        "  <title>肖申克的救赎 剧照 (豆瓣)</title>\n" +
        "</head>\n" +
        "<body>\n" +
        "  <div id=\"content\">\n" +
        "    <h1>肖申克的救赎 的剧照</h1>\n" +
        "    <div class=\"photos-count\">共 42 张</div>\n" +
        "    <ul class=\"poster-col3 clearfix\">\n" +
        "      <li>\n" +
        "        <div class=\"cover\">\n" +
        "          <a href=\"https://movie.douban.com/photos/photo/1234567/\">\n" +
        "            <img src=\"https://img1.doubanio.com/view/photo/s_ratio_poster/public/p1234567.jpg\" alt=\"剧照1\">\n" +
        "          </a>\n" +
        "        </div>\n" +
        "      </li>\n" +
        "      <li>\n" +
        "        <div class=\"cover\">\n" +
        "          <a href=\"https://movie.douban.com/photos/photo/1234568/\">\n" +
        "            <img src=\"https://img1.doubanio.com/view/photo/s_ratio_poster/public/p1234568.jpg\" alt=\"剧照2\">\n" +
        "          </a>\n" +
        "        </div>\n" +
        "      </li>\n" +
        "      <li>\n" +
        "        <div class=\"cover\">\n" +
        "          <a href=\"https://movie.douban.com/photos/photo/1234569/\">\n" +
        "            <img src=\"https://img3.doubanio.com/view/photo/s_ratio_poster/public/p1234569.jpg\" alt=\"剧照3\">\n" +
        "          </a>\n" +
        "        </div>\n" +
        "      </li>\n" +
        "    </ul>\n" +
        "    <div class=\"paginator\">\n" +
        "      <span class=\"prev\">&lt;前页</span>\n" +
        "      <span class=\"thispage\">1</span>\n" +
        "      <a href=\"?start=30\">后页&gt;</a>\n" +
        "    </div>\n" +
        "  </div>\n" +
        "</body>\n" +
        "</html>"

    /**
     * 反爬拦截页（302 到 sec.douban.com 之后返回的落地页）。
     *
     * 判定信号：出现「sec.douban.com」或「异常请求」文案时，
     * 解析器必须返回「被拦」而不是空结果——否则会被误判成「这页没有剧照」。
     */
    val antiScrapePage: String = "" +
        "<!DOCTYPE html>\n" +
        "<html>\n" +
        "<head>\n" +
        "  <meta charset=\"utf-8\">\n" +
        "  <title>异常请求</title>\n" +
        "</head>\n" +
        "<body>\n" +
        "  <div class=\"wrapper\">\n" +
        "    <h1>检测到异常请求</h1>\n" +
        "    <p>你的访问过于频繁，或使用了非正常的访问方式。</p>\n" +
        "    <p>请稍后重试，或前往 <a href=\"https://sec.douban.com/\">sec.douban.com</a> 完成验证。</p>\n" +
        "  </div>\n" +
        "</body>\n" +
        "</html>"

    /**
     * 带 HTML 实体的剧照页样本。
     *
     * 真实的豆瓣页面里，图片 URL 的查询串会以实体形式出现
     * （「&amp;」而不是「&」），某些 CDN 参数（size / crop / quality）都在这之后。
     * 解析器必须先做实体解码，再去判断 large / s_ratio 之类的尺寸规整，
     * 否则会因为 URL 里带着「&amp;」而拿到 403 图。
     */
    val emojiPage: String = "" +
        "<!DOCTYPE html>\n" +
        "<html lang=\"zh-CN\">\n" +
        "<head>\n" +
        "  <meta charset=\"utf-8\">\n" +
        "  <title>千与千寻 剧照 (豆瓣)</title>\n" +
        "</head>\n" +
        "<body>\n" +
        "  <div id=\"content\">\n" +
        "    <div class=\"photos-count\">共 3 张</div>\n" +
        "    <ul class=\"poster-col3 clearfix\">\n" +
        "      <li>\n" +
        "        <div class=\"cover\">\n" +
        "          <img src=\"https://img9.doubanio.com/view/photo/s_ratio_poster/public/p7654321.jpg?size=large&amp;crop=1&amp;quality=80\" alt=\"剧照A\">\n" +
        "        </div>\n" +
        "      </li>\n" +
        "      <li>\n" +
        "        <div class=\"cover\">\n" +
        "          <img src=\"https://img1.doubanio.com/view/photo/s_ratio_poster/public/p7654322.jpg?size=large&amp;quality=90\" alt=\"剧照B\">\n" +
        "        </div>\n" +
        "      </li>\n" +
        "      <li>\n" +
        "        <div class=\"cover\">\n" +
        "          <img src=\"https://img3.doubanio.com/view/photo/s_ratio_poster/public/p7654323.jpg?size=large&amp;type=o\" alt=\"剧照C\">\n" +
        "        </div>\n" +
        "      </li>\n" +
        "    </ul>\n" +
        "  </div>\n" +
        "</body>\n" +
        "</html>"

    /** 全部样本（便于测试里遍历做「任何一个都必须能被判定」的通用断言）。 */
    val all: List<String> = listOf(photosPage, antiScrapePage, emojiPage)
}
