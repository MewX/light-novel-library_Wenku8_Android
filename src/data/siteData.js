export const SCREENSHOT_CATEGORIES = [
    { id: 'all', label: '全部视图' },
    { id: 'bookshelf', label: '书架与 3D 书脊' },
    { id: 'discover', label: '发现与详情' },
    { id: 'reader', label: '阅读与插图' },
    { id: 'eink', label: '墨水屏模式' },
];

export const SCREENSHOTS = [
    {
        id: 'bookshelf-grid',
        category: 'bookshelf',
        src: 'imgs/app/v2/2.0-bookshelf.jpg',
        title: '本地书架 · 网格视图',
        caption: '继续阅读轮播、折叠式云端同步菜单、三视图快速切换与四栏自适应导航',
    },
    {
        id: 'bookshelf-spine-scroll',
        category: 'bookshelf',
        src: 'imgs/app/v2/2.0-bookshelf-spine-3d-scroll.jpg',
        title: '3D 立体书脊 · 滑动浏览',
        caption: '真实透视书脊与封面展开、滑动惯性吸附、底部快速滑块与聚焦详情卡',
    },
    {
        id: 'bookshelf-spine-home',
        category: 'bookshelf',
        src: 'imgs/app/v2/2.0-bookshelf-spine-3d.jpg',
        title: '3D 立体书脊 · 继续阅读态',
        caption: '近期阅读作品自动前置队首，主题色高亮与进度条直达上次阅读位置',
    },
    {
        id: 'rankings',
        category: 'discover',
        src: 'imgs/app/v2/2.0-rankings.jpg',
        title: '二维排行榜矩阵',
        caption: '点击 / 推荐 / 收藏 / 字数 × 日 / 周 / 月 / 总榜，配合连接式分段按钮切换',
    },
    {
        id: 'novel-detail',
        category: 'discover',
        src: 'imgs/app/v2/2.0-novel-detail.jpg',
        title: '作品详情与分卷目录',
        caption: '封面共享元素过渡、分卷折叠目录、拆分式阅读主按钮与四策略离线缓存',
    },
    {
        id: 'reader-hud',
        category: 'reader',
        src: 'imgs/app/v2/2.0-reader-hud.jpg',
        title: '阅读引擎与悬浮工具栏',
        caption: '六模式 CJK 排版分页引擎搭配 M3 Expressive 底部悬浮胶囊工具栏',
    },
    {
        id: 'reader-typography',
        category: 'reader',
        src: 'imgs/app/v2/2.0-reader-typography.jpg',
        title: '排版与纸张工作室',
        caption: '半透明实时预览字号、行距、段距、页边距、繁简转换与多款护眼纸张预设',
    },
    {
        id: 'artbook',
        category: 'reader',
        src: 'imgs/app/v2/2.0-artbook.jpg',
        title: 'Artbook 插图画集灯箱',
        caption: '一键抽离本卷全部插图，支持双指缩放、跨页彩插拼合、胶片缩略图与原图保存',
    },
    {
        id: 'eink-bookshelf',
        category: 'eink',
        src: 'imgs/app/v2/2.0-eink-bookshelf.jpg',
        title: '墨水屏高对比 · 书架',
        caption: '纯白底色 #FFFFFF + 1dp 实体黑框，杜绝电子纸 16 阶灰度抖动与发虚',
    },
    {
        id: 'eink-reader',
        category: 'eink',
        src: 'imgs/app/v2/2.0-eink-reader.jpg',
        title: '墨水屏高对比 · 阅读器',
        caption: '零过渡动画与墨水屏极速瞬切翻页，支持物理音量键翻页，大幅降低刷新残影',
    },
];

export const STAFF = [
    {
        name: 'MewX',
        role: '项目发起人 · 架构与基础设施维护',
        bio: '0.5.x / 1.x 原作者，负责中转与分享服务端及 2.0 产品决策与真机验收',
        avatar: 'imgs/site/avatar_mewx.jpg',
        link: 'https://mewx.org/',
    },
    {
        name: 'ZERO',
        role: '1.x 界面设计 (Material Design 1)',
        bio: '初代 Material Design 经典视觉规范与界面设计原稿作者',
        avatar: 'imgs/site/avatar_zero.jpg',
        link: null,
    },
    {
        name: '妻兔姬',
        role: '早期测试与反馈协助',
        bio: '项目早期版本内测支持与社区用户体验反馈协助',
        avatar: 'imgs/site/avatar_rabbit.jpg',
        link: null,
    },
];

export const COMMUNITY_CREDITS = [
    {
        title: '2.0 Vibe Coding 世代重构',
        description:
            '2.0 版本 (:app-v2) 100% 采用 Kotlin + Jetpack Compose (Material 3 Expressive) 从零重写，由 Gemini Carbon (High) 完成全栈架构、视觉规范、阅读引擎、数据迁移与自动化测试。',
        tag: 'Vibe Coded',
    },
    {
        title: '3D 立体书脊设计灵感',
        description:
            '本地书架 3D 立体书脊视图的视觉与交互灵感源自社区开源项目 Wenku8_Flutter (by mikufoxxx)。',
        tag: 'Inspiration',
        link: 'https://github.com/mikufoxxx/Wenku8_Flutter',
        linkLabel: 'Wenku8_Flutter',
    },
    {
        title: '1.x 历史代码贡献者',
        description:
            '感谢 Yanix (SeraphRoy / Baby Pepper) 与 Jack Liu (aaassseee) 对经典 1.x 版本的长期代码贡献与维护支持。',
        tag: 'Contributors',
        links: [
            { label: 'SeraphRoy', href: 'https://github.com/SeraphRoy' },
            { label: 'aaassseee', href: 'https://github.com/aaassseee' },
        ],
    },
];

// Complete historical dev log (40 entries from 2014 to 2026).
// Structured cleanly as paragraphs & typed segments so JSX renders safely without raw HTML injection.
export const BLOG_POSTS = [
    {
        id: 40,
        author: 'MewX',
        date: '2026-11-22 22:33',
        paragraphs: [
            [
                {
                    text: '花了3天左右的时间用 Gemini 4 Carbon (High) 重写了整个app，太舒服了，自主完成milestone。主要原因还是有个现象级的fork爆火了，导致我的API访问激增，然后看了一下网上把我的API加密逆向了个底朝天，内裤都被看光了。所以这次干脆让AI重做，加密也让AI来做，我就甩手掌柜了，做下来效果竟然还可以。我觉得按照这个进度，下次Material Design 出4的时候我估计一个双休日就能出正式版了。',
                },
            ],
        ],
    },
    {
        id: 39,
        author: 'MewX',
        date: '2025-01-04 16:30',
        paragraphs: [
            [
                {
                    text: '花了一个多小时更新了网页，感觉AI真是生产力工具，旧版网页哼哧哼哧弄了好几天才弄完。之后会先优先更新API的加密，然后加入一个不影响使用的广告（因为服务器支出实在是呈指数级上升）。不知道从哪来的三方工具在访问我的服务器，但是为了保证旧版兼容，又没法大概接口（也许可以？）。Anyway，走一步是一步，如果广告能大幅cover服务器支出，我可能会多花些时间来维护一下，毕竟有AI了。',
                },
            ],
        ],
    },
    {
        id: 38,
        author: 'MewX',
        date: '2018-06-12 23:13',
        paragraphs: [
            [{ text: '本来能至少加一下评论列表的，但是因为生产工具出了问题，先发一个临时版本解决一下，后续有时间慢慢更新！' }],
            [{ text: '另外欢迎有兴趣的童鞋来一起维护！' }],
        ],
    },
    {
        id: 37,
        author: 'MewX',
        date: '2015-09-03 23:20',
        paragraphs: [
            [
                {
                    text: '好像新的app还没正式上线 =。= 不知道怎么回事，因为新版开发者并不是我喔。我今年考研，加上作为三方开发者被官方kick，所以暂时不会维护项目了。如果明年市场还有需求的话，我会把app改成聚合类的轻小说app，通过html解析的方式抓取各大轻小说站（轻国、轻文库、文库8、etc）的资源，继续开发此项目。不知道会不会有那么一天~真心感谢大家的关注和使用！',
                },
            ],
        ],
    },
    {
        id: 36,
        author: 'MewX',
        date: '2015-08-16 00:02',
        paragraphs: [
            [{ text: 'app里面也写了，尽量把想长期看的书全本缓存一下。', highlight: true }],
            [
                { text: '源码也将于月底全部开源！', highlight: true },
                { text: 'https://github.com/MewX', href: 'https://github.com/MewX', highlight: true },
            ],
        ],
    },
    {
        id: 35,
        author: 'MewX',
        date: '2015-08-02 22:33',
        paragraphs: [
            [
                {
                    text: '酷安大家接受程度还不错，大部分都认可MD设计风格。后来发现很有趣的现象，豌豆荚、安卓市场都从酷安转载了文库的app，当年可是拒绝过我的欸，乃们要矜持啊！！（收录当然是好事 _(:3」∠)_）我等会到博客上发点有趣的截图 :P 另一件事是由于网站搬迁至海外，多余并发请求的延迟高了很多，后期会着重加强缓存的设计、并且减少请求数、优化请求返回值~',
                },
            ],
        ],
    },
    {
        id: 34,
        author: 'MewX',
        date: '2015-08-01 09:55',
        paragraphs: [
            [
                {
                    text: '近期事情真多 =。= wenku8由于不可抗力换了国际域名，原cn域名不得不立即停用。这边上架也完全上不去，尝试修改分类蒙混过关也是失败，后来我又试了gfan、appchina、91都不行，准备最后再试一下小米（估计也不行）。全军覆没啊，只有酷安上架了，另外就是官网的渠道，发现官网的渠道用户量真的很大哦 :P 昨天更新比较仓促忘了一个地方要修改，所以今天抓紧增加一个简繁转换功能，顺便收拾一下昨天的残局。app暂时就这样吧，代码整理好之后还是开源，现在的代码太乱，前后编码风格都不一样不敢发 _(:3」∠)_',
                },
            ],
        ],
    },
    {
        id: 33,
        author: 'MewX',
        date: '2015-07-30 15:30',
        paragraphs: [
            [
                {
                    text: 'v1.0.0版本发布，准备上架，结果发现应用宝、360手机市场、搜狗、安智、华为审核都不通过，理由是阅读类产品必须企业身份提交，真悲剧；百度给我的回复是搜索结果中可以搜到进击的巨人这个被封杀的内容，所以不许上架；酷安上架了，豌豆荚正在等回复，不过豌豆荚老版本上面已经有了，大概能过。',
                },
            ],
        ],
    },
    {
        id: 32,
        author: 'MewX',
        date: '2015-06-15 02:32',
        paragraphs: [[{ text: '这个月可能能上架！' }]],
    },
    {
        id: 31,
        author: 'MewX',
        date: '2015-06-06 23:55',
        paragraphs: [
            [
                {
                    text: '所有的项目都忙完了，只剩app了，晚上花了一个小时把以前的阅读器移植了过来，并且去除了惯性滑动，看看反响先。目前只能在线阅读，见缝插针加功能。音量下键的夜间模式，下次把文字改成白色就好多了。阅读字体换成了“方正书宋”，是免费授权的字体，设计师决定的。',
                },
            ],
        ],
    },
    {
        id: 30,
        author: 'MewX',
        date: '2015-05-15 23:04',
        paragraphs: [
            [
                {
                    text: '这两个礼拜会持续更新app（在内测群里），之前一直没写新的blog是因为git版本在linux和windows下不一致，懒得同步了……预览版神马的可以加群（群号你们猜，还挺难进的），留邮箱没用的~这个礼拜写了5天，进度良好~',
                },
            ],
        ],
    },
    {
        id: 29,
        author: 'MewX',
        date: '2015-03-24 23:30',
        paragraphs: [
            [{ text: '已经开始继续开发了，现在在研读OSC、DesignerNews、IOSched的源代码，为了开发更美观的MD应用~' }],
        ],
    },
    {
        id: 28,
        author: 'MewX',
        date: '2015-03-20 20:06',
        paragraphs: [
            [
                { text: 'CROSS+CHANNEL汉化补丁发布！！！http://tieba.baidu.com/p/3648179205 接下来就是弄文库啦~' },
            ],
        ],
    },
    {
        id: 27,
        author: 'MewX',
        date: '2015-02-01 09:38',
        paragraphs: [
            [
                {
                    text: '二月了呢，现在做寒假工，给公司写CMS，何其蛋疼=。= 忙得要命，工作到睡觉。 _(:3」∠)_ 真是不容易，然后就是，现在又养成了记日记的习惯，每日一记。最后再来说说app的事，年前应该是弄不出来了，过年回老家看能不能弄了。',
                },
            ],
        ],
    },
    {
        id: 26,
        author: 'MewX',
        date: '2015-01-22 11:59',
        paragraphs: [
            [
                {
                    text: '好头疼。。。本来想异步加载节约流量的，但是类一分开之后就不好处理了，各个类之间不能互相访问，我再加个中间层好了。。。',
                },
            ],
        ],
    },
    {
        id: 25,
        author: 'MewX',
        date: '2015-01-12 21:05',
        paragraphs: [[{ text: '好久没有更新，这个月31日会有全新的版本！稳定性和外观都会上一个新的档次！' }]],
    },
    {
        id: 24,
        author: 'MewX',
        date: '2014-12-23 00:49',
        paragraphs: [[{ text: '换肤完成～请期待圣诞特别版～～' }]],
    },
    {
        id: 23,
        author: 'MewX',
        date: '2014-12-22 13:49',
        paragraphs: [
            [
                {
                    text: '用512MB调试机调试时遇到了"W/OpenGLRenderer(7256): Bitmap too large to be uploaded into a texture"这个问题，这个问题的原因是OpenGL硬件加速对于图片大小做了设置，不能载入2000px宽高的图片。于是这个uk.co.senab.photoview.PhotoView库不支持大图片，妥妥地更换成com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView，完美解决！～',
                },
            ],
        ],
    },
    {
        id: 22,
        author: 'MewX',
        date: '2014-12-21 23:22',
        paragraphs: [[{ text: '大部分时间在群里了，目前版本是0.5.1.0030，下一个版本增加设置界面。' }]],
    },
    {
        id: 21,
        author: 'MewX',
        date: '2014-12-18 2:30',
        paragraphs: [
            [{ text: '测试群号：427577610' }],
            [
                {
                    text: '0.5.1.0007;给页面菜单加上了阴影;提升ResideMenu的敏感度，现在是45度角判定;小说介绍界面加入了最新章节，修复了本地书架0值问题;给小说列表和小说信息界面增加了向右滑动返回的功能，引入了Issacw0ng/SwipeBackLayout这个库;增加了.nomedia文件的生成;修改了搜索的API，提高效率并且减少数据查询操作;',
                },
            ],
        ],
    },
    {
        id: 20,
        author: 'MewX',
        date: '2014-12-16 23:27',
        paragraphs: [[{ text: '添加了章节切换功能。文库管理员在招募测试人员了。' }]],
    },
    {
        id: 19,
        author: 'MewX',
        date: '2014-12-16 02:17',
        paragraphs: [[{ text: '加一个设置界面就可以发初版了、' }]],
    },
    {
        id: 18,
        author: 'MewX',
        date: '2014-12-15 01:09',
        paragraphs: [[{ text: '这周一定会发初版的。今天把风格优化了一下，简洁多了，书架按钮已搭好，白天填功能 ww' }]],
    },
    {
        id: 17,
        author: 'MewX',
        date: '2014-12-13 16:55',
        paragraphs: [
            [{ text: '明天考试 _(:з」∠)_ 这几天一点没动代码，看了评论决定考完试补一点功能就发初版。' }],
            [
                {
                    text: '还有就是初版放出来之后的计划，我打算首先把加强点放在阅读器上，为了达到更加自由定制的目的，决定加入高效的JNI代码，Freetype2是第一个要搞定的库，这个库加上来之后就可以自定义字体了，自定义字体之后就可以自己绘制阅读界面了，自己绘制阅读界面之后就可以多种方式翻页并且可以实现各种颜色效果 2333 神马渐变色简直爽爆（不YY了 ww）',
                },
            ],
        ],
    },
    {
        id: 16,
        author: 'MewX',
        date: '2014-12-10 22:57',
        paragraphs: [
            [
                {
                    text: '真是罪恶，好几天没写代码，汉化组招校对、收班里的课程设计电子版和代码以及打印版、还要准备周日嵌入式操作系统考试。。。以及扎堆的实验报告抄写。等本地书架按钮功能完成，再做一个设置界面差不多就可以发布0.5版本，这个版本可以正常阅读以及缓存小说。后续会添加线上功能以及重做界面和添加功能。',
                },
            ],
        ],
    },
    {
        id: 15,
        author: 'MewX',
        date: '2014-12-07 23:33',
        paragraphs: [
            [
                {
                    text: '对于小说文本的显示方式尝试了ListView的方式，但是由于异步加载图片以及文本高度不固定，显示出来会发生错乱，对于这种重用的View确实不好处理，即使不用UIL也是不好处理的。所以暂时回退到采用ScrollView的版本，用这个版本的缺陷就是存储的进度是按照滚屏的百分比来存档的，也就是说调整了字体大小和段间距就会导致读档不精确。这方面还需要参考别的阅读器的代码后面再改进。下个礼拜又有考试啊 _(:з」∠)_ 我尽快把本地书架功能弄好，然后先阶段交付以下～',
                },
            ],
        ],
    },
    {
        id: 14,
        author: 'MewX',
        date: '2014-12-07 01:30',
        paragraphs: [[{ text: '对搜索功能进一步完善；提高小说加载速度；计划白天完成本地书架搭建和存档功能。' }]],
    },
    {
        id: 13,
        author: 'MewX',
        date: '2014-12-06 02:28',
        paragraphs: [
            [
                {
                    text: '搜索功能搞定，由于既定的API有一定的不便捷性，所以搜索大量数据会比较慢，这边用低端机测试是1秒可以获取5个左右的搜索结果（高端机就各种快啦、）。但是总体来说还是慢，所以增加了一个进度条，并且允许取消加载。明天，哦不，白天把搜索和小说阅读功能略微加强一下 ww',
                },
            ],
        ],
    },
    {
        id: 12,
        author: 'MewX',
        date: '2014-12-04 21:11',
        paragraphs: [
            [{ text: '基本阅读功能开发完毕，姑且可以说是能用的软件，当然用户体验会比较差，但是用丰富的数据资源来说还是可以权衡的。' }],
        ],
    },
    {
        id: 11,
        author: 'MewX',
        date: '2014-12-03 23:25',
        paragraphs: [[{ text: '小说详情界面完成一半、RelativeLayout的大量应用使得布局简洁了许多。' }]],
    },
    {
        id: 10,
        author: 'MewX',
        date: '2014-12-02 23:48',
        paragraphs: [[{ text: '最近的考试和课程设计终于是告一段落了 _(:з」∠)_ 白天可以堆代码了' }]],
    },
    {
        id: 9,
        author: 'MewX',
        date: '2014-11-28 19:49',
        paragraphs: [
            [
                { text: '感谢' },
                { text: 'Revo巨巨', href: 'http://www.razorsh.com/' },
                { text: '的美术指导，新技能get！当然要制作出来还是要等软件原型出来之后的升级版本 :P' },
            ],
            [
                {
                    text: '另外新的界面计划用Material Design搭建，如果系统版本过低则不能显示最佳效果。当然，还是能够运行的（2.3+版本安卓系统）。',
                },
            ],
        ],
    },
    {
        id: 8,
        author: 'MewX',
        date: '2014-11-28 00:29',
        paragraphs: [
            [
                { text: '之前看到有人在广泛fork开源的实用APP，咱们项目也被荣幸收录，所以打算整理以下手头代码commit一下。' },
                { text: 'Release区', href: 'https://github.com/MewX/LightNovel/releases/tag/v0.1' },
                { text: '也同步发布了这个版本的apk，算是未完成版，版本号定为0.1。' },
            ],
        ],
    },
    {
        id: 7,
        author: 'MewX',
        date: '2014-11-26 03:00',
        paragraphs: [
            [
                {
                    text: '小说查询列表页面弄好了，翻到底部可以自动加载，加载过的图片再次加载会很快。当然做的时候遇到不少问题，比如getView调用次数异常、异步POST加载图片之类的，还好绝大部分解决了，还有些小问题，例如列表翻的太快图片会更新两次 = =、这些问题都不大，暂时先放着，继续写后面的功能。',
                },
            ],
        ],
    },
    {
        id: 6,
        author: 'MewX',
        date: '2014-11-23 01:50',
        paragraphs: [
            [{ text: '最初版本大概除了主界面和该有图的界面会有图，其他的基本上都是文字了。不过图都是高清带alpha通道的png32哦～' }],
            [{ text: '重用性上面下了不少功夫吧，尽可能减少要写的代码量（其实是怕升级时的修改:P）。' }],
        ],
    },
    {
        id: 5,
        author: 'MewX',
        date: '2014-11-20 00:53',
        paragraphs: [
            [{ text: '最近在看软件工程 ww 于是应用到本项目中就是决定要应用“快速原型模型“（RAD）了。' }],
            [{ text: '晚上把简约的书库分类入口界面做完了，新的app实用至上嘛。' }],
            [{ text: '　　本APP的设计原则——“岂止于全“！\n　　轻小说资源全才是王道！' }],
            [{ text: '在可用的版本发布之后，咱会大幅度修改界面的，华丽的界面是已经设计好的啦～' }],
        ],
    },
    {
        id: 4,
        author: 'MewX',
        date: '2014-11-18 23:46',
        paragraphs: [
            [{ text: 'GitHub二级域名的支持还是有点问题的，总是在commit半个小时后莫名其妙地显示GitHub-404。' }],
            [
                { text: '嘛，总之本页面的短语名就是这个啦：' },
                { text: 'http://L.mewx.org', href: 'http://l.mewx.org' },
                { text: '。' },
            ],
            [
                {
                    text: '还有就是要感谢“空之文库”app，MewX作为一个不成熟的开发者能拿这么成熟的作品作为参考真是非常感激，比如说咱也决定用以下这些开源库了：\n　　nostra13.universalImageLoader、uk.co.senab.photoView、de.hdodenhof.circleImageView、etc...',
                },
            ],
        ],
    },
    {
        id: 3,
        author: 'MewX',
        date: '2014-11-17 21:44',
        paragraphs: [[{ text: '开始正式写代码喽~~' }]],
    },
    {
        id: 2,
        author: 'MewX',
        date: '2014-11-17 20:19',
        paragraphs: [[{ text: '动态载入XML文件哦~~~ 手机版也可以调用哈哈' }]],
    },
    {
        id: 1,
        author: 'MewX',
        date: '2014-11-17 20:18',
        paragraphs: [[{ text: '主页即将诞生啦！！' }]],
    },
];
