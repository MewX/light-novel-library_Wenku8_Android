文档目录
==========

- [写在前面](#foreword)
- [Repo目录说明](#dir-info)
- [App程序结构简明](#app-info)
  - [1.x 版本](#app-info)
    - [主要视图](#app-1.x-main-view)
    - [源代码结构](#app-1.x-source)
  - [0.5.2.1 典藏版](#app-0520)
    - [预览图](#app-0520-preview)
    - [源代码结构](#app-0520-source)
  - [UMENG统计数据](#umeng)
- [App存档文件结构](#saves)
  - [总览](#saves-dir)
  - [存档流程](#saves-procedure)
  - [技术细节](#saves-tech)
- [App二次开发指引](#app-sec-dev)
  - [如何用其他文库的文档兼容本app](#saves-compat)
- [用到的开源库](#libs)
- [开发者的碎碎念](#mewx)
- [LICENSE](#license)


----


<a id="foreword"></a>写在前面
==========

Material Design风格的`轻小说文库`App**完全**开源了！想实践Material Design的可以参考本项目的源代码，不清楚的、以及各种相关话题都可以发`issue`交流~

这个项目是我第一次写安卓App，首先看了一本入门书籍，是chinapub写书评赠送的`《第一行代码 Android》`，然后便开始着手写这个app了。从2014年10月份开始动手写，都是些零零散散的时间，写到12月底也就是0.5.2.0版本，那时基本上是啥都不会，写的代码也是烂的不行。后来内测用户群里面有个设计师提出来设计Material Design（后面简称`MD`）风格的app，于是就打算重新开发。也是拖啊拖，到7月底才完成新的app，如果满打满算的话，应该是历时一整个月，手写代码量2W行以上。

本app完全是出于兴趣以及作为一个项目经历，所以`完全无偿`开发、源代码也是`完全开源`！欢迎大家交流。之前迟迟不开源新的`MD`，**一方面**是觉得写得太不容易了，各种谷歌查资料，还为红杏插件贡献了好几十 \_(:3」∠)\_ **另一方面**是正式用git管理，Wenku8的站长也禁止将API公开，所以分来两个版本管理也很麻烦，**还有一方面**是因为代码非常乱，没有整理。

**注：**1.x版本源代码有部分是拷贝0520的，而0520的风格非常糟糕，比如`AsyncTask`的`isLoading`定义在类外这样。所以编码风格会有不统一，新写的代码我还是比较满意的，老的代码也是因为修改的需求不大，所以也就没有重构了。编码风格的建议也欢迎发issue~

----

**为什么App里面说要尽快缓存感兴趣的小说？为什么现在这么紧急地开放源代码？**

请参阅文档末尾的[`开发者的碎碎念`](#mewx)。


----


<a id="dir-info"></a>Repo目录说明
==========

1. design-source/

  设计师提供的app制作效果图，大家可以参考学习~

2. eclipse-android/

  0.5.1.0030版本的源代码（`eclipse`+`ADT`版本），这份源代码阉割了API部分，所以编译后也无法正常获取数据。

3. eclipse-android-old/

  0.5.2.1`典藏版`的源代码（`Android Studio`版本），完整从私有库里拷贝出来的。release版本和正式版发布地址一样。

4. graph-source/

  老版的制图源文件，当时自己弄得，完全就是随性而无章法 =。=

5. **studio-android/**

  1.x正式版的所有源代码存放处，`MD`风格的App完整源码，可编译通过并正常运行！（`Android Studio`版本）


----


<a id="app-info"></a>App程序结构简明
==========

## <a id="app-1.x-info"></a>1.x 版本

新版的`MD`风格App，设计图纸在`design-source/`，工程文件在`studio-android/`。

三方库的引用全部采用Gradle dependency，在混淆方面会直接忽略，加密强度低，不过开发起来各种方便~


### <a id="app-1.x-main-view"></a>主要视图

- App启动界面

[![1.x-cover-sc](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-cover-sc.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-cover-sc.jpg)

    Model & Controller: activity/WelcomeActivity.java
    View: layout/layout_welcome.xml

- 侧栏菜单栏

[![1.x-custom-menu](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-custom-menu.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-custom-menu.jpg)

    Model & Controller: fragment/NavigationDrawerFragment.java
    View: layout/layout_main_menu.xml

- 最近更新列表

[![1.x-list-loading](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-list-loading.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-list-loading.jpg)
[![1.x-list](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-list.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-list.jpg)

    Model & Controller: fragment/FavFragment.java
    Adapter: adapter/NovelItemAdapter.java
    View: layout/fragment_latest.xml

- 搜索界面

[![1.x-search](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-search.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-search.jpg)
[![1.x-searching](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-searching.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-searching.jpg)
[![1.x-search-result](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-search-result.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-search-result.jpg)

    搜索界面
      Model & Controller: activity/SearchActivity.java
      Adapter: adapter/SearchHistoryAdapter.java
      View: layout/layout_search.xml
    搜索结果界面
      Model & Controller: activity/SearchResultActivity.java
      Adapter: adapter/NovelItemAdapterUpdate.java
      View: layout/layout_search_result.xml

- 排行榜列表

[![1.x-rank-list](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-rank-list.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-rank-list.jpg)
[![1.x-rank-list2](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-rank-list2.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-rank-list2.jpg)

    Model & Controller: fragment/RKListFragment.java
    Adapter: adapter/NovelItemAdapterUpdate.java
    View: layout/fragment_rklist.xml + (layout/fragment_novel_item_list.xml)s

- 本地书架（收藏）列表

[![1.x-fav](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-fav.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-fav.jpg)

    Model & Controller: fragment/FavFragment.java
    Adapter: adapter/NovelItemAdapterUpdate.java
    View: layout/fragment_novel_item_list.xml

- 小说详细信息

[![1.x-info](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-info.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-info.jpg)
[![1.x-info-menu](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-info-menu.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-info-menu.jpg)
[![1.x-info-hotspot](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-info-hotspot.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-info-hotspot.jpg)

    Model & Controller: activity/NovolInfoActivity.java
    View: layout/layout_novel_info.xml

- 小说章节选择

[![1.x-chapter](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-chapter.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-chapter.jpg)

    Model & Controller: activity/NovolChapterActivity.java
    View: layout/layout_novel_chapter.xml

- 左右滑动阅读引擎

[![1.x-reader](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-reader.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-reader.jpg)
[![1.x-reader-swipe](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-reader-swipe.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-reader-swipe.jpg)
[![1.x-reader-menu](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-reader-menu.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-reader-menu.jpg)
[![1.x-reader-dark](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-reader-dark.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-reader-dark.jpg)
[![1.x-reader-vertical](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-reader-vertical.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-reader-vertical.jpg)
[![1.x-image-view](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-image-view.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-image-view.jpg)
[![1.x-reader-jump](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-reader-jump.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-reader-jump.jpg)
[![1.x-reader-jump-show](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-reader-jump-show.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-reader-jump-show.jpg)
[![1.x-reader-setting](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-reader-setting.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-reader-setting.jpg)
[![1.x-font-custom](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-font-custom.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-font-custom.jpg)
[![1.x-font-seek](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-font-seek.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-font-seek.jpg)

    Model & Controller: reader/activity/Wenku8ReaderActivityV1.java
    View: layout/layout_reader_swipe_temp.xml + (layout/layout_reader_swipe_page.xml)s

- 上下滑动阅读引擎

[![1.x-reader-horizental](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-reader-horizental.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-reader-horizental.jpg)

    Model & Controller: activity/VerticalReaderActivity.java
    View: layout/layout_vertical_reader_temp.xml

- 设置界面

[![1.x-config](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-config.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-config.jpg)
[![1.x-switch-tc](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-switch-tc.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-switch-tc.jpg)
[![1.x-cover-tc](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-cover-tc.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-cover-tc.jpg)
[![1.x-config-tc](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-config-tc.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-config-tc.jpg)

    Model & Controller: fragment/ConfigFragment.java
    View: layout/fragment_config.xml


### <a id="app-1.x-source"></a>源代码结构

    studio-android/LightNovelLibrary/app/src/main
    |   AndroidManifest.xml
    |
    +---assets/fonts
    |       fzss-gbk.ttf 方正书宋GBK字体
    |
    +---java/org/mewx/wenku8
    |       |   MyApp.java 自定义Application为了全局获取Context
    |       |
    |       +---activity
    |       |       AboutActivity.java 关于界面
    |       |       MainActivity.java 主界面
    |       |       MenuBackgroundSelectorActivity.java 侧栏菜单背景选择界面
    |       |       NovelChapterActivity.java 章节选择界面
    |       |       NovelInfoActivity.java 小说信息界面
    |       |       SearchActivity.java 搜索界面
    |       |       SearchResultActivity.java 搜索结果框架界面
    |       |       UserInfoActivity.java 用户信息界面
    |       |       UserLoginActivity.java 用户登录界面
    |       |       VerticalReaderActivity.java 上下滑动界面
    |       |       ViewImageDetailActivity.java 查看大图界面
    |       |       WelcomeActivity.java 启动界面
    |       |
    |       +---adapter
    |       |       NovelItemAdapter.java 老版的小说项Adapter，每10项更新一次
    |       |       NovelItemAdapterUpdate.java 更新的小说项Adapter，动态更新每一项
    |       |       SearchHistoryAdapter.java 搜索历史Adapter
    |       |
    |       +---component
    |       |       PagerSlidingTabStrip.java 排行榜自定义的标签类
    |       |       ScrollViewNoFling.java 可控滑动阻尼的ScrollView
    |       |
    |       +---fragment
    |       |       ConfigFragment.java 设置界面
    |       |       FavFragment.java 本地书架框架
    |       |       LatestFragment.java 最近更新
    |       |       NavigationDrawerFragment.java 侧栏菜单
    |       |       NovelItemListFragment.java 通用的小说列表界面（嵌入框架中）
    |       |       RKListFragment.java 排行榜框架
    |       |
    |       +---global
    |       |   |   GlobalConfig.java 全局设置（糟糕向），其中inAlphaBuild控制内测版/正式版
    |       |   |
    |       |   \---api
    |       |           ChapterInfo.java 章节信息类
    |       |           NovelItemInfo.java 小说信息类
    |       |           NovelItemInfoUpdate.java 更新的小说信息类
    |       |           NovelItemList.java 小说项列表类
    |       |           NovelItemMeta.java 小说完整信息类
    |       |           OldNovelContentParser.java 旧的小说内容解析器，解析成text和image
    |       |           UserInfo.java 用户信息类
    |       |           VolumeList.java 卷信息类
    |       |           Wenku8API.java API类
    |       |           Wenku8Error.java 错误信息类，后期的编码中定义的
    |       |           Wenku8Parser.java 通用项目解析器
    |       |
    |       +---listener
    |       |       MyItemClickListener.java RecyclerView的单击监听接口
    |       |       MyItemLongClickListener.java RecyclerView的长按监听接口
    |       |
    |       +---reader 这边准备封装成的UniversalReaderActivity库的
    |       |   +---activity
    |       |   |       Wenku8ReaderActivityV1.java 左右滑动阅读界面
    |       |   |
    |       |   +---loader
    |       |   |       WenkuReaderLoader.java 小说载入类的抽象类
    |       |   |       WenkuReaderLoaderXML.java XML格式小说载入类
    |       |   |
    |       |   +---setting
    |       |   |       WenkuReaderSettingV1.java 阅读设置类V1
    |       |   |
    |       |   +---slider 三方划屏库
    |       |   |   |   SlidingAdapter.java
    |       |   |   |   SlidingLayout.java
    |       |   |   |
    |       |   |   \---base
    |       |   |           BaseSlider.java
    |       |   |           OverlappedSlider.java
    |       |   |           PageSlider.java
    |       |   |           Slider.java
    |       |   |
    |       |   \---view
    |       |           WenkuReaderPageBatteryView.java 电池View（未使用）
    |       |           WenkuReaderPageView.java 单页小说View（效率低）
    |       |
    |       +---service
    |       |       HeartbeatSessionKeeper.java 心跳包保持session类（未使用）
    |       |
    |       \---util
    |               LightBase64.java 轻量级base64封装库
    |               LightCache.java 轻量级文件操作库（容易OOM）
    |               LightNetwork.java 轻量级网络通信库（容易OOM）
    |               LightTool.java 轻量级工具集合类
    |               LightUserSession.java 轻量级用户Session管理（包括账号密码加解密）
    |               Logger.java 轻量级日志类（未使用）
    |
    \---res/
        |    ... 略
        \


----


## <a id="app-0520"></a>0.5.2.1 典藏版

老版的App，启动和运行方面都比`MD`版本流畅，针对旧机型维护。`Eclipse`版本源码见`eclipse-android/`目录（非最新），`Android Studio`版本源码见`eclipse-android-old/`目录（典藏版最新），老版的图片资源都是用Fireworks做的，源文件见`graph-source/`目录。

### <a id="app-0520-preview"></a>预览图

[![0520-cover](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/0520-cover.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/0520-cover.jpg)
[![0520-menu](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/0520-menu.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/0520-menu.jpg)
[![0520-list](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/0520-list.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/0520-list.jpg)
[![0520-info](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/0520-info.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/0520-info.jpg)
[![0520-reader](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/0520-reader.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/0520-reader.jpg)


### <a id="app-0520-source"></a>源代码结构

这边引入了不少三方库，但是方法笨拙，主要采用复制、合并源代码的方式，所以文件目录会比较乱。但是加密强度高 \_(:3」∠)\_ 以后做商业软件还是建议这样操作，虽然麻烦，但是混淆之后烦的要命~

    eclipse-android-old\LightNovelLibrary\src
    +---com
    |   +---davemorrissey
    |   |   \---labs
    |   |       \---subscaleview 分部加载图片的库，查看大图防止OOM
    |   |               ImageViewState.java
    |   |               ScaleImageView.java
    |   |               SubsamplingScaleImageView.java
    |   |
    |   +---ecloud
    |   |   \---pulltozoomview 下拉放大的视图，设置界面用的小苹果~
    |   |           IPullToZoom.java
    |   |           PullToZoomBase.java
    |   |           PullToZoomListView.java
    |   |           PullToZoomListViewEx.java
    |   |           PullToZoomScrollView.java
    |   |           PullToZoomScrollViewEx.java
    |   |
    |   +---facebook
    |   |   \---rebound 物理、动画库，用途忘记了 =。= 大半年没动了，好像是侧栏菜单用的
    |   |       |   AndroidSpringLooperFactory.java
    |   |       |   BaseSpringSystem.java
    |   |       |   OrigamiValueConverter.java
    |   |       |   SimpleSpringListener.java
    |   |       |   Spring.java
    |   |       |   SpringConfig.java
    |   |       |   SpringConfigRegistry.java
    |   |       |   SpringListener.java
    |   |       |   SpringLooper.java
    |   |       |   SpringSystem.java
    |   |       |   SpringSystemListener.java
    |   |       |   SpringUtil.java
    |   |       |   SteppingLooper.java
    |   |       |   SynchronousLooper.java
    |   |       |
    |   |       \---ui
    |   |               SpringConfiguratorView.java
    |   |               Util.java
    |   |
    |   +---special
    |   |   \---ResideMenu 侧边滑动菜单iOS风格的
    |   |           ResideMenu.java
    |   |           ResideMenuItem.java
    |   |           TouchDisableView.java
    |   |
    |   \---zcw
    |       \---togglebutton iOS风格的切换按钮
    |               ToggleButton.java
    |
    +---me
    |   \---imid
    |       \---swipebacklayout 右滑返回layout
    |           \---lib
    |               |   SwipeBackLayout.java
    |               |   Utils.java
    |               |   ViewDragHelper.java
    |               |
    |               \---app
    |                       SwipeBackActivity.java
    |                       SwipeBackActivityBase.java
    |                       SwipeBackActivityHelper.java
    |                       SwipeBackPreferenceActivity.java
    |
    +---org
    |   \---mewx
    |       \---lightnovellibrary
    |           +---activity
    |           |       AboutActivity.java 关于界面
    |           |       BookshelfFragment.java 本地书架
    |           |       LibraryFragment.java 小说库（主界面入口）
    |           |       MainActivity.java 主界面
    |           |       NovelImageActivity.java 看大图界面
    |           |       NovelInfoActivity.java 小说信息界面
    |           |       NovelListActivity.java 小说列表界面
    |           |       NovelReaderActivity.java 阅读器界面（上下滑动）
    |           |       NovelSearchActivity.java 搜索界面
    |           |       SettingFragment.java 设置
    |           |       StartActivity.java 启动界面
    |           |       Wenku8Fragment.java （未完成）
    |           |
    |           +---api
    |           |       Wenku8Interface.java 你懂的
    |           |
    |           +---component
    |           |   |   GlobalConfig.java 全局设置界面
    |           |   |   MyApp.java 用于获取Context的全局Application
    |           |   |   NovelContentParser.java 小说内容解析器
    |           |   |   XMLParser.java XML解析器
    |           |   |
    |           |   \---adapter
    |           |           EntryElement.java 书库分类项
    |           |           EntryElementAdapter.java 书库分类项Adapter
    |           |           NovelContentAdapter.java 小说内容Adapter
    |           |           NovelElement.java 小说项
    |           |           NovelElementAdapter.java 小说项Adapter
    |           |           NovelElementSearch.java 小说搜索项
    |           |           NovelElementSearchAdapter.java 小说搜索项Adapter
    |           |           NovelIcon.java 小说封面
    |           |           NovelIconAdapter.java 小说封面Adapter
    |           |
    |           \---util
    |                   LightBase64.java 轻量级Base64库
    |                   LightCache.java 轻量级文件操作库
    |                   LightNetwork.java 轻量级网络通信库
    |
    \---uk
        \---co
            \---senab
                \---photoview 轻量级看图的View
                    |   Compat.java
                    |   DefaultOnDoubleTapListener.java
                    |   IPhotoView.java
                    |   PhotoView.java
                    |   PhotoViewAttacher.java
                    |
                    +---gestures
                    |       CupcakeGestureDetector.java
                    |       EclairGestureDetector.java
                    |       FroyoGestureDetector.java
                    |       GestureDetector.java
                    |       OnGestureListener.java
                    |       VersionedGestureDetector.java
                    |
                    +---log
                    |       Logger.java
                    |       LoggerDefault.java
                    |       LogManager.java
                    |
                    \---scrollerproxy
                            GingerScroller.java
                            IcsScroller.java
                            PreGingerScroller.java
                            ScrollerProxy.java


----


## <a id="umeng"></a>UMENG统计数据（2015/10/01残念）

[![1.x-statistic](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-statistic-20151001.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-statistic-20151001.jpg)
[![0520-statistic](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/0520-statistic-20151001.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/0520-statistic-20151001.jpg)

新版的app用户量10天增长到了1W用户量，全是托wenku8的福。照现在的增长速度速度估计用户量峰值是3W左右。

还有几个有趣的统计图分享一下：

[![1.x-statistic-entry](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-statistic-entry.thumb.jpg)](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-statistic-entry.jpg)

![1.x-statistic-entry](https://raw.githubusercontent.com/MewX/light-novel-library_Wenku8_Android/master/screenshots/1.x-statistic-rank.jpg)


----


<a id="saves"></a>App存档文件结构
==========

存档文件示例可以到[`release`](https://github.com/MewX/light-novel-library_Wenku8_Android/releases/tag/save-sample-v1.2)区下载查看~

## <a id="saves-dir"></a>总览

    sdcard/wenku8
    +---cache 完全由UIL接管的图片缓存文件夹
    +---custom 用户自定义文件夹，可以放入自定义侧栏壁纸、自定义阅读字体、自定义阅读背景
    |       .nomedia
    |
    +---imgs 小说封面
    |       .nomedia
    |       *.jpg
    |
    \---saves 存档文件夹
        |   avatar.jpg 登陆后的头像
        |   bookshelf_local.wk8 本地书架
        |   cert.wk8 简单加密的登陆用户名和密码
        |   read_saves.wk8 旧版的上下滑动阅读进度存档
        |   read_saves_v1.wk8 新版的左右滑动阅读进度存档
        |   search_history.wk8 搜索历史存档
        |   settings.wk8 设置存档
        |
        +---imgs 小说插图
        |       .nomedia
        |       *.jpg
        |
        +---intro 小说信息
        |       *.xml
        |
        \---novel 小说正文
                *.xml


## <a id="saves-procedure"></a>存档流程

- App开启后初始化UIL，接下来所有的图片都使用UIL载入，每次加载一个图片都会自动生成`cache目录`下的以CRC32为文件名缓存文件；
- App启动即读取`settings.wk8`，每次设置被改变了也会立即保存设置；
- 进入小说信息界面，会自动保存当前小说的封面于`imgs目录`；
- 小说的`收藏`功能会将aid写入`saves/bookshelf_local.wk8`，取消收藏会取消写入并删除本地的相关xml内容（图片不会删除）；
- 小说的下载功能分为4种：
  - 检查更新：仅更新当前小说在`saves/intro`中的文件，每个小说3个文件，更新后刷新Activity。
    - aid-intro.xml 小说信息摘要
    - aid-introfull.xml 小说完整介绍
    - aid-volume.xml 小说卷信息
  - 更新下载：先更新当前小说在`saves/intro`中的文件，然后按照`aid-volume.xml`中的信息，下载小说具体内容（存在`saves/novel`中），每次下载完毕后检查xml中是否含有图片，如果有图片则插入下载图片的任务**（这就是为什么下载的时候进度条会跳变了 2331）**；
  - 覆盖下载：与更新下载不同的是，这种方式是强制覆盖所有文件，而更新下载会跳过已下载的同名文件；
  - 分卷下载：更新下载的分卷版；
- 小说在章节长按章节可以选择以下两种阅读引擎：
  - 左右翻页引擎V1：进入时读取`saves/read_saves_v1.wk8`，退出时保存进度；
  - 上下滑动引擎（旧）：进入时读取`saves/read_saves.wk8`，退出时保存进度；
- 调用搜索记录时会自动读取`saves/search_history.wk8`，每次搜索一次即保存最新搜索记录；
- 用户登录后会生成`saves/cert.wk8`和`saves/avatar.jpg`，每次启动app时自动登陆；若未联网，点击头像或有访问请求时会自动登录；若登陆失败，会删除这两个凭据文件；


## <a id="saves-tech"></a>技术细节

主要是存档的实现和兼容存档的规范。

### saves/bookshelf_local.wk8

文件内容示例：

    1098||1939||1924||1749||278

文件保存规则：

1. 收藏只保存aid，aid之间用`||`分隔，读取时调用`str.split("\\|\\|");`；
2. 每个aid对应`saves/intro`目录下3个文件：`aid-intro.xml`、`aid-introfull.xml`、`aid-volume.xml`，如果缺少一个文件的话则会忽略该aid对应的书，可以通过`检查更新`（下拉书架、小说信息界面的检查更新）来修复该错误；

### saves/read_saves.wk8

文件内容示例：

    66761,,23799,,25575||66752,,134,,15090||63125,,1139,,3059||
    63126,,904,,2824||63127,,11299,,157217

文件保存规则：

1. 上下滑动阅读存档基本元素是`cid,,position,,height`：
  - cid: chapter id，对应`saves/novel`里面的文件名；
  - position: 滚动的位置，当前屏幕顶部的位置(px)；
  - height: ScrollView的总高度(px)；
2. 每个基本元素之间用`||`分隔，读取时调用`str.split("\\|\\|");`；
3. 每次记录阅读的最底部位置，往下翻再上翻只记录最底下位置；
4. 一个误判区是100px，如果不小心点进去再退出，则不会记录，只有翻过100px才会记录进度；
5. 每一章一个记录，只添加不删除 =。=

### saves/read_saves_v1.wk8

文件内容示例：

    1863:65378:65380:10:0||1922:67426:67427:0:0||1656:56193:56194:38:0||
    1244:38825:38827:0:0||1163:35537:35538:0:0||1151:35126:35128:0:0||
    1247:38920:38922:230:0||1759:60160:60161:159:0||1886:66259:66261:0:0||
    1016:51951:51952:0:0||1749:59813:59814:0:0||1575:52853:52855:0:0||
    278:10165:10166:36:0||1213:37499:37503:0:0||1928:67584:67585:0:0||
    1446:47586:47588:90:0||1755:60043:60044:0:0||1519:50813:50814:0:0||
    1932:67774:67782:255:55||1701:57929:57935:0:0

文件保存规则：

1. 基本元素是`aid:cid:vid:paraid:wordid`：
  - aid: 小说id
  - cid: 卷id
  - vid: 章id
  - paraid: 段id（从0开始）
  - wordid: 字在段中的id（从0开始）
2. 每个基本元素之间用`||`分隔，读取时调用`str.split("\\|\\|");`；
3. 每一本书一条记录，卷末自动删除记录，相当于阅读完毕；
4. 记录的原理和分页算法相关的，分页算法是通过指定`一个段落(paraid)`的`一个起始字符(wordid)`，然后动态分页；

### saves/search_history.wk8

文件内容示例：

    [田中][1到][音][刀剑][1日日日][进击的巨人][光还在][人类衰退][古][寒蝉]

文件保存规则：

1. 每一项通过`[]`分开，UTF-8无BOM编码；
2. `0520`版本会在开头加一个数字：
  - 0: 搜索作者，例如：`[0田中]`
  - 1: 搜索小说名，例如：`[1到]`
3. 默认保存10条，在`GlobalConfig.java`中记录；

### saves/settings.wk8

文件内容示例：

    reader_line_distance::::10||||
    menu_bg_path::::/storage/emulated/0/tencent/QQ_Images/5699e514d0bb9779.png||||
    reader_background_path::::0||||
    version::::1||||
    reader_paragraph_distance::::14||||
    language::::SC||||
    menu_bg_id::::0||||
    reader_font_size::::18||||
    reader_font_path::::0

键的代码片段：

    public enum SettingItems {
        version, // (int) 1
        language,
        menu_bg_id, // (int) 1-5 by system, 0 for user
        menu_bg_path, // (String) for user custom
        reader_font_path, // (String) path to ttf, "0" means default
        reader_font_size, // (int) sp (8 - 32)
        reader_line_distance, // (int) dp (0 - 32)
        reader_paragraph_distance, // (int) dp (0 - 48)
        reader_paragraph_edge_distance, // (int) dp (0 - 16)
        reader_background_path, // (String) path to an image, day mode only, "0" means default
    }

文件保存规则：

1. 采用键值对的形式，代码中使用`ContentValue`存储；
2. 存储文件时，键与值用`::::`分隔，键值对与键值对用`||||`分隔；
3. 存键的时候使用的是`enum.toString()`方法获取，避免硬编码产生的低级错误；

### saves/cert.wk8

文件内容示例：

    Z0M5a0daRXBsZG5SMFZpaFhlPT0K
    |b1FQOUVXYjFja1d1SlRkazVtV3BSWGI9Cg==

文件保存规则：`用户名|密码`，换行是系统函数自动产生的，不影响读取。

    加密流程：
        1. 原文: str
        2. 一遍加密: base64(str)
        3. 大小写互转: switch(base64(str))
        4. 两遍加密: base64(switch(base64(str)))
        5. 除了等号外，前后字符交换: swap(base64(switch(base64(str))))
        6. 三次加密: base64(swap(base64(switch(base64(str)))))

    解密流程：
        同理


----


<a id="app-sec-dev"></a>App二次开发指引
==========

本App目前决定暂停维护了……

原因一方面是开发者自己要准备年底的研究生考试；另一方面也是**最主要**的方面，鹅厂大规模购置轻小说版权，这边有一些暂时不能公开的原因，总之本三方app得告一段落了，具体可以阅读[`开发者的碎碎念`](#mewx)。

前几天我用HTTrack抓取了`lknovel.cn`的数据库，总共20G，太过于庞大，文件数20W，我的xp爬虫机已卡死。但是由于服务器在国内，速度还不错，用1~2天扒完。

后来我又尝试抓取`wenku8.com`，服务器在美国，这边**速度太慢**了，抓了一整天才1G，我估计wenku8的数据量大约有30G，实在hold不住。

    所以尽管API开源了，但是因为内陆速度实在太慢，我都没有兴趣抓取了，所以劝大家去抓其他站：
    linovel.com 可以用HTTrack设置总目录页面为入口，深度5，然后设定MAX 10000000，图片采用探索模式即可（一般的扒站工具抓不下来图片），服务器没有防护；
    lknovel.cn  可以用HTTrack设置章节页面及小说信息界面为入口（excel生成url即可），深度2，设定MAX 10000000，服务器没有防护。
    wenku8.com  速度太慢了，数据量还大，插图还在文末，没啥性价比，别抓了。

也就是说以后本app将没有数据源了，如果想继续做app的话那就只能通过抓取html提纯的方式，如果有开发者感兴趣，想通过抓取的方式继续本app（比如说：可以同时抓取lknovel、linovel、wenku8的数据展示给用户），可以阅读下面的部分：

## <a id="saves-compat"></a>如何用其他文库的文档兼容本app

目前`wenku8`的书目2000不到，也就是说aid是4位数；`linovel`、`lknovel`的aid也都是4位数，而且都没有`wenku8`的大，所以：

    在aid方面，可以采取aid+10000000的方式，比如linovel的aid是+1000万，lknovel的aid是+2000万，这样本地书架的内容就错开了。

在存档方面：

    aid-intro.xml 可以采取两种文件格式：
        最小文件体：
        <?xml version="1.0" encoding="utf-8"?>
        <metadata>
        <data name="Title" aid="1749"><![CDATA[残酷童话]]></data>
        <data name="Author" value="仓桥由美子"/>
        <data name="BookStatus" value="已完成"/>
        <data name="LastUpdate" value="2015-08-01"/>
        <data name="IntroPreview"><![CDATA[　　现实残酷，童话幻灭
        　　在现实生活中，王子与...]]></data>
        </metadata>

        扩展文件体（可以自定义字段）但是已定义的字段有这些：
        <?xml version="1.0" encoding="utf-8"?>
        <metadata>
        <data name="Title" aid="5"><![CDATA[狼与香辛料(狼与辛香料)]]></data>
        <data name="Author" value="支仓冻砂"/>
        <data name="DayHitsCount" value="14"/>
        <data name="TotalHitsCount" value="394148"/>
        <data name="PushCount" value="22940"/>
        <data name="FavCount" value="6003"/>
        <data name="PressId" value="电击文库" sid="1"/>
        <data name="BookStatus" value="已完成"/>
        <data name="BookLength" value="2004567"/>
        <data name="LastUpdate" value="2012-02-08"/>
        <data name="LatestSection" cid="36097"><![CDATA[第十七卷 插图]]></data>
        </metadata>

    aid-introfull.xml 的文件内容示例：
        旅行于各地贩卖并收购物品的商人克拉福·罗伦斯，拜访帕斯罗村并离开后，在自己的马车上发现了不知从哪里跑来的东西。
        拨开从帕斯罗村购买的小麦束后，竟然发现一位拥有兽耳与尾巴的美少女。
        少女自称为贤狼赫萝，是带给帕斯罗村长期丰收的少女。
        “虽然咱长久以来被尊为神，不过，咱就是咱，咱是赫萝。”见到少女的一只手变化成狼脚的罗伦斯，虽然一边怀疑赫萝的身份，但一边也答应让想回到出生遥远北方的少女一同旅行。

    aid-volume.xml 文件内容示例：
        <?xml version="1.0" encoding="utf-8"?>
        <package>
        <volume vid="55995"><![CDATA[第一卷]]>
        <chapter cid="55996"><![CDATA[序章 Prologue]]></chapter>
        <chapter cid="55997"><![CDATA[第一话 A-part 死与不死]]></chapter>
        <chapter cid="55998"><![CDATA[第一话 B-part 死与不死]]></chapter>
        <chapter cid="55999"><![CDATA[第二话 A-part 杀人与异能]]></chapter>
        <chapter cid="56000"><![CDATA[第二话 B-part 杀人与异能]]></chapter>
        <chapter cid="56001"><![CDATA[第三话 A-part 命与心]]></chapter>
        <chapter cid="56002"><![CDATA[第三话 B-part 命与心]]></chapter>
        <chapter cid="56003"><![CDATA[插曲 Interlude]]></chapter>
        <chapter cid="56004"><![CDATA[终章 Epilogue]]></chapter>
        <chapter cid="56005"><![CDATA[后记]]></chapter>
        <chapter cid="56006"><![CDATA[插图]]></chapter>
        </volume>
        <volume vid="68031"><![CDATA[第二卷]]>
        <chapter cid="68032"><![CDATA[【序章】Prologue]]></chapter>
        <chapter cid="68033"><![CDATA[【第一话】First Story 别墅与杀人事件]]></chapter>
        <chapter cid="68034"><![CDATA[【插曲】～Interlude～ 梦与心的夹缝间]]></chapter>
        <chapter cid="68035"><![CDATA[【第二话】Second Story 内心与创伤]]></chapter>
        <chapter cid="68036"><![CDATA[【插曲】～Interlude～ 某人的黑暗]]></chapter>
        <chapter cid="68037"><![CDATA[【第三话】Third Story 紧张局势与众人目的]]></chapter>
        <chapter cid="68038"><![CDATA[【插曲】～Interlude～ 黑暗之中]]></chapter>
        <chapter cid="68039"><![CDATA[【第四话】Fourth Story 白天与夜晚]]></chapter>
        <chapter cid="68040"><![CDATA[【插曲】～Interlude～ 黑夜与白昼的夹缝间]]></chapter>
        <chapter cid="68041"><![CDATA[【终章】Epilogue]]></chapter>
        <chapter cid="68042"><![CDATA[后记]]></chapter>
        <chapter cid="68255"><![CDATA[插图]]></chapter>
        </volume>
        <volume vid="68043"><![CDATA[第三卷]]>
        <chapter cid="68044"><![CDATA[【序章】Prologue]]></chapter>
        <chapter cid="68045"><![CDATA[【第一话·前篇】First Story*First part 妹妹和传说]]></chapter>
        <chapter cid="68046"><![CDATA[【第一话·后篇】First Story*Latter part 妹妹和传说]]></chapter>
        <chapter cid="68047"><![CDATA[【插曲】～Interlude～ 她所身处的黑暗]]></chapter>
        <chapter cid="68048"><![CDATA[【第二话·前篇】Second Story*First part 真实与冒牌货]]></chapter>
        <chapter cid="68049"><![CDATA[【第二话·后篇】Second Story*Latter part 真实与冒牌货]]></chapter>
        <chapter cid="68050"><![CDATA[【插曲】～Interlude～ 她所身处的黑暗]]></chapter>
        <chapter cid="68051"><![CDATA[【终章】～Story of End～ 于是来到她的生日]]></chapter>
        <chapter cid="68052"><![CDATA[【章外篇】～Epilogue～]]></chapter>
        <chapter cid="68053"><![CDATA[后记]]></chapter>
        <chapter cid="68256"><![CDATA[插图]]></chapter>
        </volume>
        </package>

    vid.xml 小说内容示例（目前版本支持图文混排，自动提纯多余换行和前导空格）：
        一场死亡游戏
        即将揭开序幕
        SAO玩家·桐人，以完全攻略为目标，
        在游戏舞台「艾恩葛朗特」城堡里展开一连串严酷的冒险。
        途中与女剑士·亚丝娜的相遇，也为桐人带来命中注定的契机——
        川原砾
        出身于光之国度，居住在亚兹罗斯。人生就是独行剑士。
        虽然嘴里一直逞强说自己不需要一起组队的伙伴，但是最近因为许多任务的难易度提高而感到相当棘手。虽然一辈子都没办法单独去唱KTV，但希望至少能升级到独自去吃烧肉的等级。
        <!--image-->http://pic.wenku8.com/pictures/0/471/17513/3213.jpg<!--image-->
        <!--image-->http://pic.wenku8.com/pictures/0/471/17513/3214.jpg<!--image-->
        <!--image-->http://pic.wenku8.com/pictures/0/471/17513/3215.jpg<!--image-->
        <!--image-->http://pic.wenku8.com/pictures/0/471/17513/3216.jpg<!--image-->

只要将抓取的内容通过`正则表达式`或者可更新的`lua`脚本，就可以实现抓包了，那么文库主界面可以换成这样：

    文库入口(Material Card)：
        轻国文库
        轻之文库
        轻小说文库
        etc

点进去是搜索界面，然后搜索、下载、转换成可以识别的格式这样。**目前我打听到的是国内文库还是会免费维持下去的！**

如果有学生党or开发者有兴趣，可以和我联系~


----


<a id="libs"></a>用到的开源库
==========

    - jgilfelt / SystemBarTint (Apache License 2.0)
        用于设置Kitkat以上版本StatusBar和NavigationBar颜色透明度等。
    - nostra13 / Android-Universal-Image-Loader (Apache License 2.0)
        著名的UIL，用于管理图片缓存和加载的库，非常方便，不会OOM。
    - astuetz / PagerSlidingTabStrip (Apache License 2.0)
        -> branch: jpardogo / PagerSlidingTabStrip
        Material风格的标签及页面库，jpardogo的分支在自定制方面功能更强。
    - jpardogo / GoogleProgressBar (Apache License 2.0)
        -> branch: MewX / google-progress-bar
        谷歌风格的加载动画，我的分支添加了Google Doodle-notifier的样式。
        原版的样式是圆角矩形，这边间距细节什么的模仿的不是很完美。
    - Google / Volley (Apache License 2.0)
        Google发布的大规模并发加载库。原版不支持byte返回值，我这边稍加修改了。
    - afollestad / material-dialogs (MIT License)
        完美的Material Dialog兼容库！
    - futuresimple / android-floating-action-button (Apache License 2.0)
        可以展开的FAB，动画效果很自然，但是不支持ripple比较遗憾，而且阴影会截断。
    - vinc3m1 / RoundedImageView (Apache License 2.0)
        圆形ImageView，显示头像用的。
    - chrisbanes / SmoothProgressBar (BEER-WARE LICENSE)
        平滑进度条，这里用在小说信息、搜索结果页面的ActionBar下部。
    - davemorrissey / subsampling-scale-image-view (Apache License 2.0)
        支持局部加载的ImageView，用于小说查看大图，用一般的ImageView容易OOM。
    - martiansutdio / SlidingLayout (Unknown License)
        针对电子书的划屏库，非常省内存，只有3页，自由度不高，但是毕竟拿来主义 =。=
    - AnderWeb / discreteSeekBar (Apache License 2.0)
        动画效果不错的SeekBar，用在小说阅读的设置和跳转页面中。


----


<a id="mewx"></a>开发者的碎碎念
==========

本来app是不打算开源的，比如说代码写得不好，开源的话还要维护两个版本之类的理由……

主要还是学习Material走了很多弯路，很多尝试也都在历史push里面能看到，谷歌这边推出系统的Design，而且官方的app也开发的很好了，但是开发者真正要用的除了support-design库，就只有三方库了。不过各方面还是没有Google Play Store这款app做得好啊。我各种舍不得代码呢，不希望app被复制这种心态=。= 然而事实证明是我想多了……

不过，最近又接到各种`死亡预告`，于是这个app也快要`寿终正寝`了 233 现在完全开源，包括APPKEY啊、API啥的都开放了~**欢迎大家交流学习~**

分享一下这次开发app的`经验教训`吧：

- 依赖于网站的app，一定要确保网站不会左右app发展。

  目前的情况是网站战略变化，导致本app的接口服务会停止，取而代之的是另一个合作的app（非三方）。之前说大家`抓紧缓存`的意思是，趁目前还能用，抓紧下载，指不定以后要付费啥的呢，照鹅厂这动作！

  所以我觉得，自己做app最好顺便把服务网站给一套做掉，就像`空之文库`那样有一份服务器镜像，这样LK出问题不会殃及空文。

- 开发app类型要慎重。

  这边[博文](http://www.mewx.org/blog/201507/about-app-markets/)也写了，`版权敏感的软件`市场是不轻易收的，要提供版权证明。所以这边一直上不了架，`轻文`因为有原创的成分在，所以可以上架。现在国内审查越来越严了哎……

- 用户操作指引很重要。

  这个app里面隐藏了很多小功能，比如：用户找不到搜索功能，长按章节可以选择`左右翻页`或者`上下滑动`阅读，长按历史搜索可以删除某条记录。又比如：音量键可以翻页、单击也可以翻页，还可以切换章节etc。就包括看图用户也不会，这边看图把按钮放在ActionBar上是为了避免与翻页操作冲突，但是用户说找不到图 =。= 也比方说自定义字体、图片什么的说了不能在外置SD卡，但是用户还是不能按照要求的操作。

  所以进入界面的时候应该用户友好，弹出一个操作提示，所以这方面是在下输了！

- 原有的用户黏着性需要很强大的优势才能打破。

  本来轻小说有个app叫`空之文库`，我这边app出来之后，用户量确实增加了不少，但是插图在文末这点确实是最大的软肋，因为轻小说有不少的用户是中学生，这部分用户说难听点就是比较叽歪，但是为了用户量，作为`替代品`目标的app，确实需要在功能上满足绝大多数用户的需求。所以`轻文`出来之后，这边用户量确实流失了不少。


----


<a id="license"></a>LICENSE
==========

    GNU GENERAL PUBLIC LICENSE

                         Version 2, June 1991

    Copyright (C) 1989, 1991 Free Software Foundation, Inc., <http://fsf.org/>
    51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
    Everyone is permitted to copy and distribute verbatim copies
    of this license document, but changing it is not allowed.

    ......
