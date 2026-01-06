import React, { useState, useEffect } from 'react';
import {
    Download,
    Github,
    Coffee,
    Menu,
    X,
    Moon,
    Sun,
    Smartphone,
    User,
    Calendar,
    ExternalLink,
    Heart,
    ChevronRight,
    Globe,
    History,
    ArrowRight,
    MessageSquare
} from 'lucide-react';

// --- Data Constants ---

const STAFF = [
    { name: 'MewX', role: '程序架构 (Architecture)', avatar: 'imgs/site/avatar_mewx.jpg', link: 'https://mewx.org/' },
    { name: 'ZERO', role: '界面设计 (UI Design)', avatar: 'imgs/site/avatar_zero.jpg', link: '#' },
    { name: '妻兔姬', role: '反馈协助 (Support)', avatar: 'imgs/site/avatar_rabbit.jpg', link: '#' },
];

// TODO: No need to have thumbnails nowadays.
const SCREENSHOTS = [
    { thumb: 'imgs/app/android.01.png', full: 'imgs/app/android.01.png' },
    { thumb: 'imgs/app/android.02.png', full: 'imgs/app/android.02.png' },
    { thumb: 'imgs/app/android.03.png', full: 'imgs/app/android.03.png' },
    { thumb: 'imgs/app/android.04.png', full: 'imgs/app/android.04.png' },
    { thumb: 'imgs/app/android.05.png', full: 'imgs/app/android.05.png' },
];

// Complete list converted from microblog.xml
const BLOG_POSTS = [
    {
        id: 39,
        author: 'MewX',
        date: '2025-01-04 16:30',
        content: '花了一个多小时更新了网页，感觉AI真是生产力工具，旧版网页哼哧哼哧弄了好几天才弄完。之后会先优先更新API的加密，然后加入一个不影响使用的广告（因为服务器支出实在是呈指数级上升）。不知道从哪来的三方工具在访问我的服务器，但是为了保证旧版兼容，又没法大概接口（也许可以？）。Anyway，走一步是一步，如果广告能大幅cover服务器支出，我可能会多花些时间来维护一下，毕竟有AI了。'
    },
    {
        id: 38,
        author: 'MewX',
        date: '2018-06-12 23:13',
        content: '本来能至少加一下评论列表的，但是因为生产工具出了问题，先发一个临时版本解决一下，后续有时间慢慢更新！<br/>另外欢迎有兴趣的童鞋来一起维护！'
    },
    {
        id: 37,
        author: 'MewX',
        date: '2015-09-03 23:20',
        content: '好像新的app还没正式上线 =。= 不知道怎么回事，因为新版开发者并不是我喔。我今年考研，加上作为三方开发者被官方kick，所以暂时不会维护项目了。如果明年市场还有需求的话，我会把app改成聚合类的轻小说app，通过html解析的方式抓取各大轻小说站（轻国、轻文库、文库8、etc）的资源，继续开发此项目。不知道会不会有那么一天~真心感谢大家的关注和使用！'
    },
    {
        id: 36,
        author: 'MewX',
        date: '2015-08-16 00:02',
        content: '<span class="text-red-500 font-bold">app里面也写了，尽量把想长期看的书全本缓存一下。<br/>源码也将于月底全部开源！http://github.com/MewX</span>'
    },
    {
        id: 35,
        author: 'MewX',
        date: '2015-08-02 22:33',
        content: '酷安大家接受程度还不错，大部分都认可MD设计风格。后来发现很有趣的现象，豌豆荚、安卓市场都从酷安转载了文库的app，当年可是拒绝过我的欸，乃们要矜持啊！！（收录当然是好事 _(:3」∠)_）我等会到博客上发点有趣的截图 :P 另一件事是由于网站搬迁至海外，多余并发请求的延迟高了很多，后期会着重加强缓存的设计、并且减少请求数、优化请求返回值~'
    },
    {
        id: 34,
        author: 'MewX',
        date: '2015-08-01 09:55',
        content: '近期事情真多 =。= wenku8由于不可抗力换了国际域名，原cn域名不得不立即停用。这边上架也完全上不去，尝试修改分类蒙混过关也是失败，后来我又试了gfan、appchina、91都不行，准备最后再试一下小米（估计也不行）。全军覆没啊，只有酷安上架了，另外就是官网的渠道，发现官网的渠道用户量真的很大哦 :P 昨天更新比较仓促忘了一个地方要修改，所以今天抓紧增加一个简繁转换功能，顺便收拾一下昨天的残局。app暂时就这样吧，代码整理好之后还是开源，现在的代码太乱，前后编码风格都不一样不敢发 _(:3」∠)_'
    },
    {
        id: 33,
        author: 'MewX',
        date: '2015-07-30 15:30',
        content: 'v1.0.0版本发布，准备上架，结果发现应用宝、360手机市场、搜狗、安智、华为审核都不通过，理由是阅读类产品必须企业身份提交，真悲剧；百度给我的回复是搜索结果中可以搜到进击的巨人这个被封杀的内容，所以不许上架；酷安上架了，豌豆荚正在等回复，不过豌豆荚老版本上面已经有了，大概能过。'
    },
    {
        id: 32,
        author: 'MewX',
        date: '2015-06-15 02:32',
        content: '这个月可能能上架！'
    },
    {
        id: 31,
        author: 'MewX',
        date: '2015-06-06 23:55',
        content: '所有的项目都忙完了，只剩app了，晚上花了一个小时把以前的阅读器移植了过来，并且去除了惯性滑动，看看反响先。目前只能在线阅读，见缝插针加功能。音量下键的夜间模式，下次把文字改成白色就好多了。阅读字体换成了“方正书宋”，是免费授权的字体，设计师决定的。'
    },
    {
        id: 30,
        author: 'MewX',
        date: '2015-05-15 23:04',
        content: '这两个礼拜会持续更新app（在内测群里），之前一直没写新的blog是因为git版本在linux和windows下不一致，懒得同步了……预览版神马的可以加群（群号你们猜，还挺难进的），留邮箱没用的~这个礼拜写了5天，进度良好~'
    },
    {
        id: 29,
        author: 'MewX',
        date: '2015-03-24 23:30',
        content: '已经开始继续开发了，现在在研读OSC、DesignerNews、IOSched的源代码，为了开发更美观的MD应用~'
    },
    {
        id: 28,
        author: 'MewX',
        date: '2015-03-20 20:06',
        content: 'CROSS+CHANNEL汉化补丁发布！！！http://tieba.baidu.com/p/3648179205 接下来就是弄文库啦~'
    },
    {
        id: 27,
        author: 'MewX',
        date: '2015-02-01 09:38',
        content: '二月了呢，现在做寒假工，给公司写CMS，何其蛋疼=。= 忙得要命，工作到睡觉。 _(:3」∠)_ 真是不容易，然后就是，现在又养成了记日记的习惯，每日一记。最后再来说说app的事，年前应该是弄不出来了，过年回老家看能不能弄了。'
    },
    {
        id: 26,
        author: 'MewX',
        date: '2015-01-22 11:59',
        content: '好头疼。。。本来想异步加载节约流量的，但是类一分开之后就不好处理了，各个类之间不能互相访问，我再加个中间层好了。。。'
    },
    {
        id: 25,
        author: 'MewX',
        date: '2015-01-12 21:05',
        content: '好久没有更新，这个月31日会有全新的版本！稳定性和外观都会上一个新的档次！'
    },
    {
        id: 24,
        author: 'MewX',
        date: '2014-12-23 00:49',
        content: '换肤完成～请期待圣诞特别版～～'
    },
    {
        id: 23,
        author: 'MewX',
        date: '2014-12-22 13:49',
        content: '用512MB调试机调试时遇到了"W/OpenGLRenderer(7256): Bitmap too large to be uploaded into a texture"这个问题，这个问题的原因是OpenGL硬件加速对于图片大小做了设置，不能载入2000px宽高的图片。于是这个uk.co.senab.photoview.PhotoView库不支持大图片，妥妥地更换成com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView，完美解决！～'
    },
    {
        id: 22,
        author: 'MewX',
        date: '2014-12-21 23:22',
        content: '大部分时间在群里了，目前版本是0.5.1.0030，下一个版本增加设置界面。'
    },
    {
        id: 21,
        author: 'MewX',
        date: '2014-12-18 2:30',
        content: '测试群号：427577610<br/><br/>0.5.1.0007;给页面菜单加上了阴影;提升ResideMenu的敏感度，现在是45度角判定;小说介绍界面加入了最新章节，修复了本地书架0值问题;给小说列表和小说信息界面增加了向右滑动返回的功能，引入了Issacw0ng/SwipeBackLayout这个库;增加了.nomedia文件的生成;修改了搜索的API，提高效率并且减少数据查询操作;'
    },
    {
        id: 20,
        author: 'MewX',
        date: '2014-12-16 23:27',
        content: '添加了章节切换功能。文库管理员在招募测试人员了。'
    },
    {
        id: 19,
        author: 'MewX',
        date: '2014-12-16 02:17',
        content: '加一个设置界面就可以发初版了、'
    },
    {
        id: 18,
        author: 'MewX',
        date: '2014-12-15 01:09',
        content: '这周一定会发初版的。今天把风格优化了一下，简洁多了，书架按钮已搭好，白天填功能 ww'
    },
    {
        id: 17,
        author: 'MewX',
        date: '2014-12-13 16:55',
        content: '明天考试 _(:з」∠)_ 这几天一点没动代码，看了评论决定考完试补一点功能就发初版。<br/>还有就是初版放出来之后的计划，我打算首先把加强点放在阅读器上，为了达到更加自由定制的目的，决定加入高效的JNI代码，Freetype2是第一个要搞定的库，这个库加上来之后就可以自定义字体了，自定义字体之后就可以自己绘制阅读界面了，自己绘制阅读界面之后就可以多种方式翻页并且可以实现各种颜色效果 2333 神马渐变色简直爽爆（不YY了 ww）'
    },
    {
        id: 16,
        author: 'MewX',
        date: '2014-12-10 22:57',
        content: '真是罪恶，好几天没写代码，汉化组招校对、收班里的课程设计电子版和代码以及打印版、还要准备周日嵌入式操作系统考试。。。以及扎堆的实验报告抄写。等本地书架按钮功能完成，再做一个设置界面差不多就可以发布0.5版本，这个版本可以正常阅读以及缓存小说。后续会添加线上功能以及重做界面和添加功能。'
    },
    {
        id: 15,
        author: 'MewX',
        date: '2014-12-07 23:33',
        content: '对于小说文本的显示方式尝试了ListView的方式，但是由于异步加载图片以及文本高度不固定，显示出来会发生错乱，对于这种重用的View确实不好处理，即使不用UIL也是不好处理的。所以暂时回退到采用ScrollView的版本，用这个版本的缺陷就是存储的进度是按照滚屏的百分比来存档的，也就是说调整了字体大小和段间距就会导致读档不精确。这方面还需要参考别的阅读器的代码后面再改进。下个礼拜又有考试啊 _(:з」∠)_ 我尽快把本地书架功能弄好，然后先阶段交付以下～'
    },
    {
        id: 14,
        author: 'MewX',
        date: '2014-12-07 01:30',
        content: '对搜索功能进一步完善；提高小说加载速度；计划白天完成本地书架搭建和存档功能。'
    },
    {
        id: 13,
        author: 'MewX',
        date: '2014-12-06 02:28',
        content: '搜索功能搞定，由于既定的API有一定的不便捷性，所以搜索大量数据会比较慢，这边用低端机测试是1秒可以获取5个左右的搜索结果（高端机就各种快啦、）。但是总体来说还是慢，所以增加了一个进度条，并且允许取消加载。明天，哦不，白天把搜索和小说阅读功能略微加强一下 ww'
    },
    {
        id: 12,
        author: 'MewX',
        date: '2014-12-04 21:11',
        content: '基本阅读功能开发完毕，姑且可以说是能用的软件，当然用户体验会比较差，但是用丰富的数据资源来说还是可以权衡的。'
    },
    {
        id: 11,
        author: 'MewX',
        date: '2014-12-03 23:25',
        content: '小说详情界面完成一半、RelativeLayout的大量应用使得布局简洁了许多。'
    },
    {
        id: 10,
        author: 'MewX',
        date: '2014-12-02 23:48',
        content: '最近的考试和课程设计终于是告一段落了 _(:з」∠)_ 白天可以堆代码了'
    },
    {
        id: 9,
        author: 'MewX',
        date: '2014-11-28 19:49',
        content: '感谢<a href="http://www.razorsh.com/" target="_blank" class="text-blue-500 hover:underline">Revo巨巨</a>的美术指导，新技能get！当然要制作出来还是要等软件原型出来之后的升级版本 :P<br/>另外新的界面计划用Material Design搭建，如果系统版本过低则不能显示最佳效果。当然，还是能够运行的（2.3+版本安卓系统）。'
    },
    {
        id: 8,
        author: 'MewX',
        date: '2014-11-28 00:29',
        content: '之前看到有人在广泛fork开源的实用APP，咱们项目也被荣幸收录，所以打算整理以下手头代码commit一下。<a href="https://github.com/MewX/LightNovel/releases/tag/v0.1" target="_blank" class="text-blue-500 hover:underline">Release区</a>也同步发布了这个版本的apk，算是未完成版，版本号定为0.1。'
    },
    {
        id: 7,
        author: 'MewX',
        date: '2014-11-26 03:00',
        content: '小说查询列表页面弄好了，翻到底部可以自动加载，加载过的图片再次加载会很快。当然做的时候遇到不少问题，比如getView调用次数异常、异步POST加载图片之类的，还好绝大部分解决了，还有些小问题，例如列表翻的太快图片会更新两次 = =、这些问题都不大，暂时先放着，继续写后面的功能。'
    },
    {
        id: 6,
        author: 'MewX',
        date: '2014-11-23 01:50',
        content: '最初版本大概除了主界面和该有图的界面会有图，其他的基本上都是文字了。不过图都是高清带alpha通道的png32哦～<br/>重用性上面下了不少功夫吧，尽可能减少要写的代码量（其实是怕升级时的修改:P）。'
    },
    {
        id: 5,
        author: 'MewX',
        date: '2014-11-20 00:53',
        content: '最近在看软件工程 ww 于是应用到本项目中就是决定要应用“快速原型模型“（RAD）了。<br/>晚上把简约的书库分类入口界面做完了，新的app实用至上嘛。<br/><br/>　　本APP的设计原则——“岂止于全“！<br/>　　轻小说资源全才是王道！<br/><br/>在可用的版本发布之后，咱会大幅度修改界面的，华丽的界面是已经设计好的啦～'
    },
    {
        id: 4,
        author: 'MewX',
        date: '2014-11-18 23:46',
        content: 'GitHub二级域名的支持还是有点问题的，总是在commit半个小时后莫名其妙地显示GitHub-404。<br/>嘛，总之本页面的短语名就是这个啦： <a href="http://l.mewx.org" class="text-blue-500 hover:underline">http://L.mewx.org</a>。<br/><br/>还有就是要感谢“空之文库”app，MewX作为一个不成熟的开发者能拿这么成熟的作品作为参考真是非常感激，比如说咱也决定用以下这些开源库了：<br/>　　nostra13.universalImageLoader、uk.co.senab.photoView、de.hdodenhof.circleImageView、etc...'
    },
    {
        id: 3,
        author: 'MewX',
        date: '2014-11-17 21:44',
        content: '开始正式写代码喽~~'
    },
    {
        id: 2,
        author: 'MewX',
        date: '2014-11-17 20:19',
        content: '动态载入XML文件哦~~~ 手机版也可以调用哈哈'
    },
    {
        id: 1,
        author: 'MewX',
        date: '2014-11-17 20:18',
        content: '主页即将诞生啦！！'
    }
];

// --- Components ---

const Navbar = ({ darkMode, toggleDarkMode }) => {
    const [isOpen, setIsOpen] = useState(false);

    return (
        <nav className={`fixed w-full z-50 transition-all duration-300 ${darkMode ? 'bg-slate-900/90' : 'bg-white/80'} backdrop-blur-md shadow-sm`}>
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                <div className="flex items-center justify-between h-16">
                    <div className="flex-shrink-0 flex items-center gap-2">
                        <span className={`font-bold text-xl ${darkMode ? 'text-blue-400' : 'text-blue-600'}`}>Wenku8 Android</span>
                        <span className="text-xs px-2 py-0.5 rounded-full bg-blue-100 text-blue-800 font-semibold">随缘维护</span>
                    </div>

                    <div className="hidden md:block">
                        <div className="ml-10 flex items-baseline space-x-4">
                            <a href="#home" className={`hover:bg-blue-500 hover:text-white px-3 py-2 rounded-md text-sm font-medium transition-colors ${darkMode ? 'text-gray-300' : 'text-gray-700'}`}>首页</a>
                            <a href="#news" className={`hover:bg-blue-500 hover:text-white px-3 py-2 rounded-md text-sm font-medium transition-colors ${darkMode ? 'text-gray-300' : 'text-gray-700'}`}>日志</a>
                            <a href="#staff" className={`hover:bg-blue-500 hover:text-white px-3 py-2 rounded-md text-sm font-medium transition-colors ${darkMode ? 'text-gray-300' : 'text-gray-700'}`}>作者</a>
                            <a href="#comments" className={`hover:bg-blue-500 hover:text-white px-3 py-2 rounded-md text-sm font-medium transition-colors ${darkMode ? 'text-gray-300' : 'text-gray-700'}`}>留言</a>
                            <button
                                onClick={toggleDarkMode}
                                className={`p-2 rounded-full hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors ${darkMode ? 'text-yellow-400' : 'text-slate-600'}`}
                            >
                                {darkMode ? <Sun size={20} /> : <Moon size={20} />}
                            </button>
                        </div>
                    </div>

                    <div className="-mr-2 flex md:hidden">
                        <button
                            onClick={toggleDarkMode}
                            className={`mr-2 p-2 rounded-full hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors ${darkMode ? 'text-yellow-400' : 'text-slate-600'}`}
                        >
                            {darkMode ? <Sun size={20} /> : <Moon size={20} />}
                        </button>
                        <button
                            onClick={() => setIsOpen(!isOpen)}
                            className={`inline-flex items-center justify-center p-2 rounded-md hover:text-white hover:bg-blue-500 focus:outline-none ${darkMode ? 'text-gray-400' : 'text-gray-700'}`}
                        >
                            {isOpen ? <X size={24} /> : <Menu size={24} />}
                        </button>
                    </div>
                </div>
            </div>

            {/* Mobile menu */}
            {isOpen && (
                <div className={`md:hidden ${darkMode ? 'bg-slate-800' : 'bg-white'} shadow-lg`}>
                    <div className="px-2 pt-2 pb-3 space-y-1 sm:px-3">
                        <a href="#home" className={`block px-3 py-2 rounded-md text-base font-medium ${darkMode ? 'text-gray-300 hover:text-white hover:bg-slate-700' : 'text-gray-700 hover:text-gray-900 hover:bg-gray-50'}`}>首页</a>
                        <a href="#news" className={`block px-3 py-2 rounded-md text-base font-medium ${darkMode ? 'text-gray-300 hover:text-white hover:bg-slate-700' : 'text-gray-700 hover:text-gray-900 hover:bg-gray-50'}`}>日志</a>
                        <a href="#staff" className={`block px-3 py-2 rounded-md text-base font-medium ${darkMode ? 'text-gray-300 hover:text-white hover:bg-slate-700' : 'text-gray-700 hover:text-gray-900 hover:bg-gray-50'}`}>作者</a>
                        <a href="#comments" className={`block px-3 py-2 rounded-md text-base font-medium ${darkMode ? 'text-gray-300 hover:text-white hover:bg-slate-700' : 'text-gray-700 hover:text-gray-900 hover:bg-gray-50'}`}>留言</a>
                    </div>
                </div>
            )}
        </nav>
    );
};

const Hero = ({ darkMode }) => {
    return (
        <div id="home" className="relative pt-24 pb-16 md:pt-32 md:pb-24 overflow-hidden">
            <div className="absolute inset-0 z-0">
                <img
                    src="imgs/site/tab_bg.jpg"
                    alt="Background"
                    className="w-full h-full object-cover object-top opacity-30" />
                <div className={`absolute inset-0 bg-gradient-to-b 
                    ${darkMode
                        ? 'from-slate-900/10 to-slate-900/90'   // Dark mode: 30% top opacity -> 90% bottom opacity
                        : 'from-blue-50/0 to-white/90'         // Light mode: 10% top opacity -> 80% bottom opacity
                    }`}>
                </div>
            </div>

            <div className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
                <div className="inline-block p-4 rounded-full bg-blue-100 dark:bg-blue-900/50 mb-6 animate-bounce-slow">
                    <img src="imgs/app/app_icon.png" alt="App Icon" className="w-12 h-12 object-contain" />
                </div>

                <h1 className={`text-4xl md:text-6xl font-extrabold tracking-tight mb-4 ${darkMode ? 'text-white' : 'text-slate-900'}`}>
                    轻小说文库 <span className="text-blue-600">Android</span>
                </h1>

                <p className={`mt-4 max-w-2xl mx-auto text-xl ${darkMode ? 'text-gray-300' : 'text-gray-600'}`}>
                    与轻小说文库 (Wenku8.com) 合作的开源安卓轻小说阅读器。
                    <br />
                    简洁、高效、完全免费。
                </p>

                <div className="mt-10 flex justify-center gap-4 flex-wrap">
                    <a
                        href="https://buymeacoffee.com/mewx"
                        target="_blank"
                        rel="noopener noreferrer"
                        className="flex items-center px-8 py-3 rounded-lg bg-yellow-400 hover:bg-yellow-300 text-yellow-900 font-bold shadow-lg shadow-yellow-400/30 transition-all hover:scale-105"
                    >
                        <Coffee className="mr-2" size={20} />
                        赞助服务器
                    </a>

                    <a
                        href="https://play.google.com/store/apps/details?id=org.mewx.wenku8"
                        target="_blank"
                        rel="noopener noreferrer"
                        className="flex items-center px-8 py-3 rounded-lg bg-blue-600 hover:bg-blue-700 text-white font-semibold shadow-lg shadow-blue-500/30 transition-all hover:scale-105"
                    >
                        <Download className="mr-2" size={20} />
                        从 Play Store 安装
                    </a>
                </div>

                <div className="mt-8 flex justify-center flex-wrap gap-4 text-sm">
                    <a
                        href="https://www.wenku8.com"
                        target="_blank"
                        rel="noopener noreferrer"
                        className={`flex items-center gap-2 px-4 py-2 rounded-full border transition-colors ${darkMode ? 'border-slate-700 bg-slate-800/50 hover:bg-slate-700 text-gray-300' : 'border-gray-200 bg-white/50 hover:bg-white text-gray-600'}`}
                    >
                        <Globe size={16} /> wenku8.com
                    </a>
                    <a
                        href="https://mewx.org"
                        target="_blank"
                        rel="noopener noreferrer"
                        className={`flex items-center gap-2 px-4 py-2 rounded-full border transition-colors ${darkMode ? 'border-slate-700 bg-slate-800/50 hover:bg-slate-700 text-gray-300' : 'border-gray-200 bg-white/50 hover:bg-white text-gray-600'}`}
                    >
                        <User size={16} /> mewx.org
                    </a>
                    <a
                        href="https://github.com/MewX/light-novel-library_Wenku8_Android"
                        target="_blank"
                        rel="noopener noreferrer"
                        className={`flex items-center gap-2 px-4 py-2 rounded-full border transition-colors ${darkMode ? 'border-slate-700 bg-slate-800/50 hover:bg-slate-700 text-gray-300' : 'border-gray-200 bg-white/50 hover:bg-white text-gray-600'}`}
                    >
                        <Github size={16} /> 查看源码
                    </a>
                </div>

                <div className="mt-8 flex flex-col items-center justify-center gap-2 text-sm text-gray-500">
                    <div className="flex items-center gap-2">
                        <span>最新版本:</span>
                        <a href="https://github.com/MewX/light-novel-library_Wenku8_Android/releases" target="_blank" className="font-mono bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300 px-2 py-1 rounded">v1.24 (2026-01-04)</a>
                    </div>
                    <div className="flex items-center gap-2">
                        <span>历史稳定版本:</span>
                        <a href="https://github.com/MewX/light-novel-library_Wenku8_Android/releases/tag/v1.13" target="_blank" className="font-mono bg-gray-100 dark:bg-slate-800 px-2 py-1 rounded">v1.13 (2021-01-17)</a>
                    </div>
                </div>
            </div>
        </div>
    );
};


const ScreenshotGallery = ({ darkMode }) => {
    const [selectedImage, setSelectedImage] = useState(null);

    return (
        <section id="screenshots" className={`py-16 ${darkMode ? 'bg-slate-900' : 'bg-gray-50'}`}>
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                <h2 className={`text-3xl font-bold mb-12 text-center ${darkMode ? 'text-white' : 'text-gray-900'}`}>
                    App Previews
                </h2>

                <div className="flex overflow-x-auto pb-8 gap-6 scrollbar-hide snap-x">
                    {SCREENSHOTS.map((shot, index) => (
                        <div
                            key={index}
                            className="flex-shrink-0 snap-center cursor-pointer transform transition-transform hover:scale-105"
                            onClick={() => setSelectedImage(shot.full)}
                        >
                            <img
                                src={shot.thumb}
                                alt={`Screenshot ${index + 1}`}
                                className="h-96 w-auto rounded-xl shadow-xl object-cover border-4 border-white dark:border-slate-700"
                            />
                        </div>
                    ))}
                </div>
            </div>

            {/* Lightbox Modal */}
            {selectedImage && (
                <div
                    className="fixed inset-0 z-50 flex items-center justify-center bg-black/90 backdrop-blur-sm p-4"
                    onClick={() => setSelectedImage(null)}
                >
                    <button
                        className="absolute top-4 right-4 text-white hover:text-gray-300"
                        onClick={() => setSelectedImage(null)}
                    >
                        <X size={32} />
                    </button>
                    <img
                        src={selectedImage}
                        alt="Full size preview"
                        className="max-w-full max-h-[90vh] rounded-lg shadow-2xl"
                        onClick={(e) => e.stopPropagation()}
                    />
                </div>
            )}
        </section>
    );
};

const BlogCard = ({ post, darkMode, compact = false, fullText = false, className = '' }) => {
    const [likes, setLikes] = useState(Math.floor(Math.random() * 20));
    const [liked, setLiked] = useState(false);

    const handleLike = () => {
        if (liked) {
            setLikes(likes - 1);
        } else {
            setLikes(likes + 1);
        }
        setLiked(!liked);
    };

    // Base classes with dynamic parts
    const baseClasses = `rounded-2xl transition-all duration-300 hover:shadow-lg border ${className}`;
    const themeClasses = darkMode
        ? 'bg-slate-800 border-slate-700 hover:border-blue-500/50'
        : 'bg-white border-gray-100 hover:border-blue-200';
    const spacingClasses = compact ? 'p-4' : 'p-6 mb-6';

    return (
        <div className={`${baseClasses} ${themeClasses} ${spacingClasses} flex flex-col`}>
            <div className="flex justify-between items-start mb-4">
                <div className="flex items-center gap-3">
                    <div className={`flex-shrink-0 rounded-full flex items-center justify-center font-bold text-white ${compact ? 'w-8 h-8 text-xs' : 'w-10 h-10'} ${post.author === 'MewX' ? 'bg-gradient-to-br from-blue-400 to-blue-600' : 'bg-gray-400'}`}>
                        {post.author[0]}
                    </div>
                    <div>
                        <h3 className={`font-bold ${compact ? 'text-sm' : ''} ${darkMode ? 'text-gray-100' : 'text-gray-900'}`}>{post.author}</h3>
                        <div className={`flex items-center ${compact ? 'text-[10px]' : 'text-xs'} text-gray-500`}>
                            <Calendar size={compact ? 10 : 12} className="mr-1" />
                            {post.date}
                        </div>
                    </div>
                </div>
            </div>

            <div
                className={`prose prose-sm max-w-none flex-grow ${compact ? 'text-xs' : ''} ${compact && !fullText ? 'line-clamp-4' : ''} mb-4 ${darkMode ? 'prose-invert text-gray-300' : 'text-gray-600'}`}
                dangerouslySetInnerHTML={{ __html: post.content }}
            />

            {!compact && (
                <div className={`pt-4 border-t flex items-center justify-between ${darkMode ? 'border-slate-700' : 'border-gray-100'}`}>
                    <button
                        onClick={handleLike}
                        className={`flex items-center text-sm gap-1 transition-colors ${liked ? 'text-red-500' : 'text-gray-400 hover:text-red-400'}`}
                    >
                        <Heart size={16} fill={liked ? "currentColor" : "none"} />
                        <span>{likes}</span>
                    </button>
                    <button className="text-gray-400 hover:text-blue-500">
                        <ExternalLink size={16} />
                    </button>
                </div>
            )}
        </div>
    );
};

const NewsDrawer = ({ isOpen, onClose, darkMode }) => {
    return (
        <>
            {/* Backdrop */}
            <div
                className={`fixed inset-0 z-[60] bg-black/50 backdrop-blur-sm transition-opacity duration-300 ${isOpen ? 'opacity-100' : 'opacity-0 pointer-events-none'}`}
                onClick={onClose}
            />

            {/* Drawer */}
            <div className={`fixed top-0 right-0 z-[70] h-full w-full sm:w-[500px] shadow-2xl transform transition-transform duration-300 ease-in-out ${isOpen ? 'translate-x-0' : 'translate-x-full'} ${darkMode ? 'bg-slate-900/95' : 'bg-white/95'} backdrop-blur-xl border-l ${darkMode ? 'border-slate-700' : 'border-gray-200'}`}>
                <div className="flex flex-col h-full">
                    <div className={`flex items-center justify-between p-6 border-b ${darkMode ? 'border-slate-800' : 'border-gray-100'}`}>
                        <div className="flex items-center gap-3">
                            <div className={`p-2 rounded-full ${darkMode ? 'bg-blue-900/30 text-blue-400' : 'bg-blue-100 text-blue-600'}`}>
                                <History size={24} />
                            </div>
                            <div>
                                <h2 className={`text-xl font-bold ${darkMode ? 'text-white' : 'text-gray-900'}`}>开发日志</h2>
                                <p className={`text-sm ${darkMode ? 'text-gray-400' : 'text-gray-500'}`}>{BLOG_POSTS.length} 条记录</p>
                            </div>
                        </div>
                        <button
                            onClick={onClose}
                            className={`p-2 rounded-full hover:bg-gray-200 dark:hover:bg-slate-800 transition-colors ${darkMode ? 'text-gray-400' : 'text-gray-500'}`}
                        >
                            <X size={24} />
                        </button>
                    </div>

                    <div className="flex-1 overflow-y-auto p-6 scrollbar-thin scrollbar-thumb-gray-300 dark:scrollbar-thumb-slate-700">
                        {BLOG_POSTS.map((post) => (
                            <BlogCard key={post.id} post={post} darkMode={darkMode} compact={true} fullText={true} className="mb-4" />
                        ))}

                        <div className="text-center py-8">
                            <p className={`text-sm ${darkMode ? 'text-gray-500' : 'text-gray-400'}`}>已显示全部内容</p>
                            <p className={`text-xs mt-2 ${darkMode ? 'text-gray-600' : 'text-gray-300'}`}>感谢一路相伴！</p>
                        </div>
                    </div>
                </div>
            </div>
        </>
    );
};

const NewsSection = ({ darkMode, onOpenDrawer }) => {
    // Show only the latest 3 posts in the main view
    const recentPosts = BLOG_POSTS.slice(0, 3);

    return (
        <section id="news" className={`py-16 ${darkMode ? 'bg-slate-800/50' : 'bg-white'}`}>
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                <div className="flex items-center justify-between mb-10">
                    <h2 className={`text-3xl font-bold ${darkMode ? 'text-white' : 'text-gray-900'}`}>
                        开发日志
                    </h2>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-12">
                    {recentPosts.map((post) => (
                        <BlogCard key={post.id} post={post} darkMode={darkMode} compact={true} className="h-full" />
                    ))}
                </div>

                <div className="text-center">
                    <button
                        onClick={onOpenDrawer}
                        className={`group inline-flex items-center gap-2 px-8 py-4 rounded-full text-lg font-bold shadow-lg transition-all hover:scale-105 ${darkMode ? 'bg-blue-600 hover:bg-blue-500 text-white shadow-blue-900/20' : 'bg-blue-600 hover:bg-blue-700 text-white shadow-blue-200'}`}
                    >
                        查看完整更新记录 <ArrowRight size={20} className="transition-transform group-hover:translate-x-1" />
                    </button>
                </div>
            </div>
        </section>
    );
};

const StaffCard = ({ member, darkMode }) => (
    <div className={`flex flex-col items-center p-6 rounded-2xl text-center transition-all hover:-translate-y-1 ${darkMode ? 'bg-slate-800 hover:bg-slate-750' : 'bg-white shadow-lg shadow-gray-200/50'}`}>
        <div className="relative mb-4">
            <div className="w-24 h-24 rounded-full overflow-hidden border-4 border-blue-50 dark:border-slate-700">
                <img src={member.avatar} alt={member.name} className="w-full h-full object-cover" />
            </div>
            <div className="absolute bottom-0 right-0 bg-blue-500 text-white p-1 rounded-full border-2 border-white dark:border-slate-800">
                <User size={12} />
            </div>
        </div>
        <h3 className={`text-lg font-bold mb-1 ${darkMode ? 'text-white' : 'text-gray-900'}`}>{member.name}</h3>
        <p className={`text-sm mb-4 ${darkMode ? 'text-blue-400' : 'text-blue-600'}`}>{member.role}</p>
        {member.link && member.link !== '#' && (
            <a
                href={member.link}
                target="_blank"
                rel="noopener noreferrer"
                className={`text-xs flex items-center gap-1 hover:underline ${darkMode ? 'text-gray-500 hover:text-gray-300' : 'text-gray-400 hover:text-gray-600'}`}
            >
                访问主页 <ChevronRight size={12} />
            </a>
        )}
    </div>
);

const StaffSection = ({ darkMode }) => {
    return (
        <section id="staff" className={`py-16 ${darkMode ? 'bg-slate-900' : 'bg-gray-50'}`}>
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                <h2 className={`text-3xl font-bold mb-12 text-center ${darkMode ? 'text-white' : 'text-gray-900'}`}>
                    制作团队
                </h2>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-8 max-w-4xl mx-auto">
                    {STAFF.map((member, index) => (
                        <StaffCard key={index} member={member} darkMode={darkMode} />
                    ))}
                </div>
            </div>
        </section>
    );
};


const DisqusComments = ({ darkMode }) => {
    useEffect(() => {
        // Define global config if not already defined
        window.disqus_config = function () {
            this.page.url = 'http://wenku8.mewx.org/';
            this.page.identifier = 'wenku8android';
        };

        // Load Disqus script
        const d = document;
        const s = d.createElement('script');
        s.src = 'https://wenku8android.disqus.com/embed.js';
        s.setAttribute('data-timestamp', +new Date());
        (d.head || d.body).appendChild(s);

        // Remove the annoying disqus Ads.
        // Based on a copy of https://stackoverflow.com/a/78583202/4206925
        const disqusThread = d.getElementById('disqus_thread');
        if (disqusThread) {
            const observer = new MutationObserver((mutations) => {
                mutations.forEach(() => {
                    const iframes = disqusThread.getElementsByTagName('iframe');
                    // Original logic: assumes ads create multiple iframes and the 2nd one is the real comment box? 
                    // Replicating exactly as requested:
                    if (iframes.length > 1) {
                        const commentsIframe = iframes[1];
                        while (disqusThread.firstChild) {
                            disqusThread.removeChild(disqusThread.firstChild);
                        }
                        disqusThread.appendChild(commentsIframe);
                        observer.disconnect();
                    }
                });
            });
            observer.observe(disqusThread, { childList: true, subtree: true });

            // Clean up observer on unmount
            return () => observer.disconnect();
        }
    }, []);

    return (
        <section id="comments" className={`py-16 ${darkMode ? 'bg-slate-900' : 'bg-gray-50'}`}>
            <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
                <div className="flex items-center gap-3 mb-10">
                    <div className={`p-2 rounded-full ${darkMode ? 'bg-blue-900/30 text-blue-400' : 'bg-blue-100 text-blue-600'}`}>
                        <MessageSquare size={24} />
                    </div>
                    <h2 className={`text-3xl font-bold ${darkMode ? 'text-white' : 'text-gray-900'}`}>
                        留言板
                    </h2>
                </div>

                <div className={`p-6 md:p-8 rounded-2xl shadow-lg border ${darkMode ? 'bg-slate-800 border-slate-700' : 'bg-white border-gray-100'}`}>
                    <div id="disqus_thread"></div>
                    <noscript>
                        Please enable JavaScript to view the <a href="https://disqus.com/?ref_noscript">comments powered by Disqus.</a>
                    </noscript>
                </div>
            </div>
        </section>
    );
};


const Footer = ({ darkMode }) => {
    return (
        <footer className={`py-12 ${darkMode ? 'bg-slate-950 text-gray-400' : 'bg-gray-900 text-gray-400'}`}>
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                <div className="text-center text-sm">
                    <p className="font-semibold mb-2">Wenku8 Android</p>
                    <p>&copy; {new Date().getFullYear()} MewX & Contributors. 保留所有权利。</p>
                    <p className="mt-2 text-xs text-gray-600">
                        本项目是粉丝作品，与轻小说文库 (Wenku8.com) 无官方关联。
                    </p>
                </div>
            </div>
        </footer>
    );
};

const App = () => {
    const [darkMode, setDarkMode] = useState(false);
    const [isNewsDrawerOpen, setIsNewsDrawerOpen] = useState(false);

    // Check system preference on load
    useEffect(() => {
        if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
            setDarkMode(true);
        }
    }, []);

    const toggleDarkMode = () => setDarkMode(!darkMode);

    return (
        <div className={`min-h-screen font-sans transition-colors duration-300 ${darkMode ? 'bg-slate-900' : 'bg-white'}`}>
            <Navbar darkMode={darkMode} toggleDarkMode={toggleDarkMode} />
            <Hero darkMode={darkMode} />
            <ScreenshotGallery darkMode={darkMode} />
            <NewsSection darkMode={darkMode} onOpenDrawer={() => setIsNewsDrawerOpen(true)} />
            <StaffSection darkMode={darkMode} />
            <DisqusComments darkMode={darkMode} />
            <Footer darkMode={darkMode} />

            <NewsDrawer
                isOpen={isNewsDrawerOpen}
                onClose={() => setIsNewsDrawerOpen(false)}
                darkMode={darkMode}
            />
        </div>
    );
};

export default App;
