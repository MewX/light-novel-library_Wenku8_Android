package org.mewx.wenku8.global.api;

import android.content.ContentValues;
import android.util.Log;

import com.umeng.analytics.MobclickAgent;
import com.umeng.onlineconfig.OnlineConfigAgent;

import org.mewx.wenku8.MyApp;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.util.LightBase64;
import org.mewx.wenku8.util.LightNetwork;
import org.mewx.wenku8.R;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class Wenku8API {

    /**
     * Basic definitions
     */

    public static String NoticeString = "";
    final public static String RegisterURL = "http://www.wenku8.com/register.php";
    final private static String BaseURL = "http://app.wenku8.com/android.php";
    private static boolean hasEdited = false;
    final private static String NovelFinishedSC = "已完成", NovelFinishedTC = "已完成",
            NovelNotFinishedSC = "连载中", NovelNotFinishedTC = "連載中";

    public static String getBaseURL() {
        if(NoticeString.equals("") || NoticeString.equals("http://weuku8.mewx.org")) {
            NoticeString = OnlineConfigAgent.getInstance().getConfigParams(MyApp.getContext(), GlobalConfig.getCurrentLang() != LANG.SC ? "wenku8_notice_tw" : "wenku8_notice");
        }
        return BaseURL;
    }

    public static void replaceBaseURL(String str) {
        // replace URL for backup


        hasEdited = true;
    }

    public static boolean hasEdited() {
        return hasEdited;
    }

    public static String getCoverURL(int aid) {
        return "http://img.wenku8.com/image/" + Integer.toString(aid / 1000)
                + "/" + Integer.toString(aid) + "/" + Integer.toString(aid) + "s.jpg";
    }

    public static String[] badWords = {
            "共产党", "政府",  "毛泽东",  "邓小平",  "江泽民",  "胡锦涛",  "温家宝",  "习近平",
            "李克强", "台独",  "藏独", "反日", "反共", "反中", "达赖", "刘晓波", "毛主席", "愤青",
            "反华", "右翼", "游行", "示威", "静坐", "公安", "李洪志", "法轮功", "刷分", "路过路过",
            ".......", "。。。。", "色情", "吃屎", "你妈", "他妈", "她妈", "操你", "垃圾", "去死",
            "迷魂药", "催情药", "毒品"
    };


    /**
     * Basic converter functions
     */

    public enum LANG {
        SC, // simplified Chinese
        TC // traditional Chinese
    }

    private static int getLANG(LANG l) {
        switch (l) {
            case SC:
                return 0;
            case TC:
                return 1;
            default:
                return 0; // for extended language
        }
    }

    public enum STATUS {
        FINISHED, // novel's publishing finished
        NOTFINISHED // novel's publishing not finished
    }

    public static STATUS getSTATUSByInt(int i) {
        return i == 0 ? STATUS.NOTFINISHED : STATUS.FINISHED;
    }

    public static STATUS getSTATUSByString(String s) {
        return s.equals(NovelNotFinishedSC) || s.equals(NovelNotFinishedTC) ? STATUS.NOTFINISHED : STATUS.FINISHED;
    }

    public static String getStatusBySTATUS(STATUS s) {
        switch (GlobalConfig.getCurrentLang()) {
            case SC:
                if (s == STATUS.FINISHED)
                    return NovelFinishedSC;
                else
                    return NovelNotFinishedSC;

            case TC:
                if (s == STATUS.FINISHED)
                    return NovelFinishedTC;
                else
                    return NovelNotFinishedTC;

            default:
                if (s == STATUS.FINISHED)
                    return NovelFinishedSC;
                else
                    return NovelNotFinishedSC;
        }
    }

    public enum NOVELSORTBY {
        // sort arguments:
        // allvisit 总排行榜; allvote 总推荐榜; monthvisit 月排行榜; monthvote 月推荐榜;
        // weekvisit 周排行榜; weekvote 周推荐榜; dayvisit 日排行榜; dayvote 日推荐榜;
        // postdate 最新入库; lastupdate 最近更新; goodnum 总收藏榜; size 字数排行;
        // fullflag 完结列表
        allVisit, allVote, monthVisit, monthVote, weekVisit, weekVote, dayVisit, dayVote, postDate, lastUpdate, goodNum, size, fullFlag
    }

    public static NOVELSORTBY getNOVELSORTBY(String n) {
        switch (n) {
            case "allvisit":
                return NOVELSORTBY.allVisit;
            case "allvote":
                return NOVELSORTBY.allVote;
            case "monthvisit":
                return NOVELSORTBY.monthVisit;
            case "monthvote":
                return NOVELSORTBY.monthVote;
            case "weekvisit":
                return NOVELSORTBY.weekVisit;
            case "weekvote":
                return NOVELSORTBY.weekVote;
            case "dayvisit":
                return NOVELSORTBY.dayVisit;
            case "dayvote":
                return NOVELSORTBY.dayVote;
            case "postdate":
                return NOVELSORTBY.postDate;
            case "lastupdate":
                return NOVELSORTBY.lastUpdate;
            case "goodnum":
                return NOVELSORTBY.goodNum;
            case "size":
                return NOVELSORTBY.size;
            case "fullflag":
                return NOVELSORTBY.fullFlag;
            default:
                return NOVELSORTBY.allVote; // default
        }
    }

    public static String getNOVELSORTBY(NOVELSORTBY n) {
        switch (n) {
            case allVisit:
                return "allvisit";
            case allVote:
                return "allvote";
            case monthVisit:
                return "monthvisit";
            case monthVote:
                return "monthvote";
            case weekVisit:
                return "weekvisit";
            case weekVote:
                return "weekvote";
            case dayVisit:
                return "dayvisit";
            case dayVote:
                return "dayvote";
            case postDate:
                return "postdate";
            case lastUpdate:
                return "lastupdate";
            case goodNum:
                return "goodnum";
            case size:
                return "size";
            case fullFlag:
                return "fullflag";
            default:
                return "allvote"; // default
        }
    }

    public static int getNOVELSORTBY_ChsId(NOVELSORTBY n) {
        switch (n) {
            case allVisit:
                return R.string.tab_allvisit;
            case allVote:
                return R.string.tab_allvote;
            case monthVisit:
                return R.string.tab_monthvisit;
            case monthVote:
                return R.string.tab_monthvote;
            case weekVisit:
                return R.string.tab_weekvisit;
            case weekVote:
                return R.string.tab_weekvote;
            case dayVisit:
                return R.string.tab_dayvisit;
            case dayVote:
                return R.string.tab_dayvote;
            case postDate:
                return R.string.tab_postdate;
            case lastUpdate:
                return R.string.tab_lastupdate;
            case goodNum:
                return R.string.tab_goodnum;
            case size:
                return R.string.tab_size;
            case fullFlag:
                return R.string.tab_fullflag;
            default:
                return R.string.tab_allvote; // default
        }
    }

    public static int getErrorInfo_ResId(int errNo) {
        switch (errNo) {
            case 0:
                // 请求发生错误
                return R.string.error_00;
            case 1:
                // 成功(登陆、添加、删除、发帖)
                return R.string.error_01;
            case 2:
                // 用户名错误
                return R.string.error_02;
            case 3:
                // 密码错误
                return R.string.error_03;
            case 4:
                // 请先登陆
                return R.string.error_04;
            case 5:
                // 已经在书架
                return R.string.error_05;
            case 6:
                // 书架已满
                return R.string.error_06;
            case 7:
                // 小说不在书架
                return R.string.error_07;
            case 8:
                // 回复帖子主题不存在
                return R.string.error_08;
            case 9:
                // 签到失败
                return R.string.error_09;
            case 10:
                // 推荐失败
                return R.string.error_10;
            case 11:
                // 帖子发送失败
                return R.string.error_11;
            case 22:
                // refer page 0
                return R.string.error_22;
            default:
                // unknown
                return R.string.error_unknown;
        }
    }


    /**
     * This part are the old API writing ways.
     * It's not efficient enough, and maybe bug-hidden.
     */
    private static Map<String,String> getEncryptedMAP(String str) {
        Map<String, String> params = new HashMap<>();
        params.put("request", LightBase64.EncodeBase64(str+"&timetoken="+System.currentTimeMillis()));
        return params;
    }

    private static ContentValues getEncryptedCV(String str) {
        ContentValues cv = new ContentValues();
        cv.put("request",LightBase64.EncodeBase64(str+"&timetoken="+System.currentTimeMillis()));
//        Log.e("MewX", "request = " + LightBase64.EncodeBase64(str+"&timetoken="+System.currentTimeMillis()));
        return cv;
    }

    public static ContentValues getNovelCover(int aid) {
        // get the aid, and return a "jpg" file or other, in binary
        return getEncryptedCV("action=book&do=cover&aid=" + aid);
    }

    public static ContentValues getNovelShortInfo(int aid, LANG l) {
        // get short XML info of a novel, here is an example:
        // --------------------------------------------------
        // <?xml version="1.0" encoding="utf-8"?>
        // <metadata>
        // <data name="Title" aid="1305"><![CDATA[绝对双刃absolute duo]]></data>
        // <data name="Author" value="柊★巧"/>
        // <data name="BookStatus" value="0"/>
        // <data name="LastUpdate" value="2014-10-01"/>
        // <data
        // name="IntroPreview"><![CDATA[　　「焰牙」——那是藉由超化之后的精神力将自身灵...]]></data>
        // </metadata>
        return getEncryptedCV("action=book&do=info&aid=" + aid + "&t=" + getLANG(l));
    }

    public static Map<String,String> getNovelShortInfoUpdate(int aid, LANG l) {
        // action=book&do=bookinfo&aid=3&t=1 //小说信息（升级版）
//        Map<String, String> params = new HashMap<String, String>();
//        params.put("action", "book");
//        params.put("do", "bookinfo");
//        params.put("aid",Integer.toString(aid));
//        params.put("t", Integer.toString(getLANG(l)));
        //return params;

        return getEncryptedMAP("action=book&do=bookinfo&aid=" + aid + "&t=" + getLANG(l));
    }

    public static ContentValues getNovelShortInfoUpdate_CV(int aid, LANG l) {
        return getEncryptedCV("action=book&do=bookinfo&aid=" + aid + "&t=" + getLANG(l));
    }

    public static ContentValues getNovelFullIntro(int aid, LANG l) {
        // get full XML intro of a novel, here is an example:
        // --------------------------------------------------
        // 　　在劍與魔法作為一股強大力量的世界裡，克雷歐過著只有繪畫是唯一生存意義的孤獨生活。
        // 　　不過生於名門的他，為了取得繼承人資格必須踏上試煉之旅。
        // 　　踏入禁忌森林的他，遇見一名半人半植物的魔物。
        // 　　輕易被抓的克雷歐設法勾起少女的興趣得到幫助，卻又被她當成寵物一般囚禁起來。
        // 　　兩人從此展開不可思議的同居時光，這樣的生活令他感到很安心。
        // 　　但平靜的日子沒有持續太久……
        // 　　描繪人與魔物的戀情，溫暖人心的奇幻故事。
        return getEncryptedCV("action=book&do=intro&aid=" + aid + "&t="
                + getLANG(l));
    }

    public static ContentValues getNovelFullMeta(int aid, LANG l) {
        // get full XML metadata of a novel, here is an example:
        // -----------------------------------------------------
        // <?xml version="1.0" encoding="utf-8"?>
        // <metadata>
        // <data name="Title"
        // aid="1306"><![CDATA[向森之魔物献上花束(向森林的魔兽少女献花)]]></data>
        // <data name="Author" value="小木君人"/>
        // <data name="DayHitsCount" value="26"/>
        // <data name="TotalHitsCount" value="43984"/>
        // <data name="PushCount" value="1735"/>
        // <data name="FavCount" value="848"/>
        // <data name="PressId" value="小学馆" sid="10"/>
        // <data name="BookStatus" value="已完成"/>
        // <data name="BookLength" value="105985"/>
        // <data name="LastUpdate" value="2012-11-02"/>
        // <data name="LatestSection" cid="41897"><![CDATA[第一卷 插图]]></data>
        // </metadata>
        return getEncryptedCV("action=book&do=meta&aid=" + aid + "&t="
                + getLANG(l));
    }

    public static ContentValues getNovelIndex(int aid, LANG l) {
        // get full XML index of a novel, here is an example:
        // --------------------------------------------------
        // <?xml version="1.0" encoding="utf-8"?>
        // <package>
        // <volume vid="41748"><![CDATA[第一卷 告白于苍刻之夜]]>
        // <chapter cid="41749"><![CDATA[序章]]></chapter>
        // <chapter cid="41750"><![CDATA[第一章「去对我的『楯』说吧——」]]></chapter>
        // <chapter cid="41751"><![CDATA[第二章「我真的对你非常感兴趣」]]></chapter>
        // <chapter cid="41752"><![CDATA[第三章「揍我吧！」]]></chapter>
        // <chapter cid="41753"><![CDATA[第四章「下次，再来喝苹果茶」]]></chapter>
        // <chapter cid="41754"><![CDATA[第五章「这是约定」]]></chapter>
        // <chapter cid="41755"><![CDATA[第六章「你的背后——由我来守护！」]]></chapter>
        // <chapter cid="41756"><![CDATA[第七章「茱莉——爱交给你！」]]></chapter>
        // <chapter cid="41757"><![CDATA[尾声]]></chapter>
        // <chapter cid="41758"><![CDATA[后记]]></chapter>
        // <chapter cid="41759"><![CDATA[插图]]></chapter>
        // </volume>
        // <volume vid="45090"><![CDATA[第二卷 谎言、真相与赤红]]>
        // <chapter cid="45091"><![CDATA[序章]]></chapter>
        // <chapter cid="45092"><![CDATA[第一章「莉莉丝·布里斯托」]]></chapter>
        // <chapter cid="45093"><![CDATA[第二章「借你的话来说就是……」]]></chapter>
        // <chapter cid="45094"><![CDATA[第三章「这真是个好提议」]]></chapter>
        // <chapter cid="45095"><![CDATA[第四章「如守护骑士一般」]]></chapter>
        // <chapter cid="45096"><![CDATA[第五章「『咬龙战』，开始！」]]></chapter>
        // <chapter cid="45097"><![CDATA[第六章「超越人类的存在」]]></chapter>
        // <chapter cid="45098"><![CDATA[第七章「『灵魂』」]]></chapter>
        // <chapter cid="45099"><![CDATA[尾声]]></chapter>
        // <chapter cid="45100"><![CDATA[后记]]></chapter>
        // <chapter cid="45105"><![CDATA[插图]]></chapter>
        // </volume>
        // ...... ......
        // </package>
        return getEncryptedCV("action=book&do=list&aid=" + aid + "&t="
                + getLANG(l));
    }

    public static ContentValues getNovelContent(int aid, int cid, LANG l) {
        // get full content of an article of a novel,
        // the images should be processed then, here is an example:
        // --------------------------------------------------------
        // 第一卷 告白于苍刻之夜 插图
        // ...... ......
        // <!--image-->http://pic.wenku8.cn/pictures/1/1305/41759/50471.jpg<!--image-->
        // <!--image-->http://pic.wenku8.cn/pictures/1/1305/41759/50472.jpg<!--image-->
        // <!--image-->http://pic.wenku8.cn/pictures/1/1305/41759/50473.jpg<!--image-->
        // ...... ......
        return getEncryptedCV("action=book&do=text&aid=" + aid + "&cid=" + cid
                + "&t=" + getLANG(l));
    }

    // ##########
    // # Here test: action=book&do=vote&aid=1239 //推荐小说
    // # (就是网站上面那个喜欢小说 就推一下那个，app日限制5次/需要登录账号)
    // ##########
    // ReqTest07 = ''
    // #return getResult( ReqTest07, True );
    public static ContentValues searchNovelByNovelName(String novelName, LANG l) {
        // get a list of search results, here is an example:
        // Note: there are extra line-break.
        // -------------------------------------------------
        // <?xml version="1.0" encoding="utf-8"?>
        // <result>
        // <item aid='1699'/>
        // <item aid='1638'/>
        // <item aid='1293'/>
        // <item aid='977'/>
        // <item aid='693'/>
        // <item aid='993'/>
        // <item aid='333'/>
        // <item aid='499'/>
        // <item aid='826'/>
        // </result>
        return getEncryptedCV("action=search&searchtype=articlename&searchkey="
                + LightNetwork.encodeToHttp(novelName) + "&t=" + getLANG(l));
    }

    public static ContentValues searchNovelByAuthorName(String authorName,
                                                        LANG l) {
        // get a list of search results.
        // Note: there are extra line-break.
        return getEncryptedCV("action=search&searchtype=author&searchkey="
                + LightNetwork.encodeToHttp(authorName) + "&t=" + getLANG(l));
    }

    public static ContentValues getNovelList(NOVELSORTBY n, int page) {
        // here get a specific list of novels, sorted by NOVELSORTBY
        // ---------------------------------------------------------
        // <?xml version="1.0" encoding="utf-8"?>
        // <result>
        // <page num='166'/>
        // <item aid='1143'/>
        // <item aid='1034'/>
        // <item aid='1213'/>
        // <item aid='1'/>
        // <item aid='1011'/>
        // <item aid='1192'/>
        // <item aid='433'/>
        // <item aid='47'/>
        // <item aid='7'/>
        // <item aid='374'/>
        // </result>
        return getEncryptedCV("action=articlelist&sort=" + getNOVELSORTBY(n)
                + "&page=" + page);
    }

    public static ContentValues getNovelListWithInfo(NOVELSORTBY n, int page,
                                                     LANG l) {
        // get novel list with info digest
        // -------------------------------
        // <?xml version="1.0" encoding="utf-8"?>
        // <result>
        // <page num='166'/>
        //
        // <item aid='1034'>
        // <data name='Title'><![CDATA[恶魔高校DxD(High School DxD)]]></data>
        // <data name='TotalHitsCount' value='2316361'/>
        // <data name='PushCount' value='153422'/>
        // <data name='FavCount' value='14416'/>
        // <data name='Author' value='xxx'/>
        // <data name='BookStatus' value='xxx'/>
        // <data name='LastUpdate' value='xxx'/>
        // <data name='IntroPreview' value='xxx'/>
        // </item>
        // ...... ......
        // </result>
        return getEncryptedCV("action=novellist&sort=" + getNOVELSORTBY(n)
                + "&page=" + page + "&t=" + getLANG(l));
    }

    public static ContentValues getLibraryList() {
        // return an XML file, once get the "sort id",
        // call getNovelListByLibrary
        // --------------------------
        // <?xml version="1.0" encoding="utf-8"?>
        // <metadata>
        // <item sort="1">电击文库</item>
        // <item sort="2">富士见文库</item>
        // <item sort="3">角川文库</item>
        // <item sort="4">MF文库J</item>
        // <item sort="5">Fami通文库</item>
        // <item sort="6">GA文库</item>
        // <item sort="7">HJ文库</item>
        // <item sort="8">一迅社</item>
        // <item sort="9">集英社</item>
        // <item sort="10">小学馆</item>
        // <item sort="11">讲谈社</item>
        // <item sort="12">少女文库</item>
        // <item sort="13">其他文库</item>
        // <item sort="14">游戏剧本</item>
        // </metadata> '''; # action=xml&item=sort&t=0
        return getEncryptedCV("action=xml&item=sort&t=0");
    }

    public static ContentValues getNovelListByLibrary(int sortId, int page) {
        // sortId is from "getLibraryList" above
        return getEncryptedCV("action=articlelist&sort=" + sortId + "&page="
                + page);
    }

    public static ContentValues getNovelListByLibraryWithInfo(int sortId,
                                                              int page, LANG l) {
        // sortId is from "getLibraryList" above
        return getEncryptedCV("action=novellist&sort=" + sortId + "&page="
                + page + "&t=" + getLANG(l));
    }


    /**
     * I rewrite part of the APIs to get the best performance.
     * The old APIs are above, and use HttpRequest.
     * This part uses AFinal and that's more efficient.
     */

    /**
     * This part is user related, and is using the latest API 22 features.
     * Banned NameValuePair, HttpPost.
     */
    public static ContentValues getUserLoginParams(String username, String password) {
        // 使用session方式判断是否已登录
//        Log.e("MewX", "Uname: " + username + "\nPwd: " + password + " (" + LightNetwork.encodeToHttp(password) + ")");
        String temp = "action=login&username=" + LightNetwork.encodeToHttp(username) + "&password=" + LightNetwork.encodeToHttp(password);
        return getEncryptedCV(temp);
    }

    public static ContentValues getUserAvatar( ) {
        // return jpeg raw data
        return getEncryptedCV("action=avatar");
    }

    public static ContentValues getUserLogoutParams( ) {
        return getEncryptedCV("action=logout");
    }

    public static ContentValues getUserInfoParams( ) {
        /**
         * <?xml version="1.0" encoding="utf-8"?>
         * <metadata>
         * <item name="uname"><![CDATA[apptest]]></item>
         * <item name="nickname"><![CDATA[apptest]]></item>
         * <item name="score">10</item>
         * <item name="experience">10</item>
         * <item name="rank"><![CDATA[新手上路]]></item>
         * </metadata>
         */
        return getEncryptedCV("action=userinfo");
    }

    public static ContentValues getUserSignParams( ) {
        /**
         * _cb({"ret":0});
         */
        return getEncryptedCV("action=block&do=sign"); // 增加一个积分/天
    }

    public static ContentValues getVoteNovelParams(int aid) {
        // 推荐小说  (就是网站上面那个喜欢小说 就推一下那个，app日限制5次/需要登录账号)
        return getEncryptedCV("action=book&do=vote&aid=" + aid);
    }

    public static ContentValues getBookshelfListAid(LANG l) {
        // 查询书架列表，只含有aid

        /**
         * <?xml version="1.0" encoding="utf-8"?>
         * <metadata>
         *     <book aid="1499" />
         *     <book aid="1754" />
         *     <book aid="1605" />
         *     <book aid="1483" />
         *     <book aid="1469" />
         *     <book aid="1087" />
         * </metadata>
         */

        return getEncryptedCV("action=bookcase&do=list&t=" + getLANG(l));
    }

    public static ContentValues getBookshelfListParams(LANG l) {
        // 查询书架列表

        // find "aid", find first \" to second \"
        /**
         * <?xml version="1.0" encoding="utf-8"?>
         * <metadata>
         *
         * <book aid="1499" date="2015-04-19">
         * <name><![CDATA[時鐘機關之星Clockwork Planet]]></name>
         * <chapter cid="64896"><![CDATA[插圖]]></chapter>
         * </book>
         *
         * <book aid="1754" date="2014-12-05">
         * <name><![CDATA[貓耳天使與戀愛蘋果]]></name>
         * <chapter cid="60552"><![CDATA[插圖]]></chapter>
         * </book>
         *
         * <book aid="1605" date="2014-05-06">
         * <name><![CDATA[驚悚文集]]></name>
         * <chapter cid="54722"><![CDATA[插圖]]></chapter>
         * </book>
         *
         * <book aid="1483" date="2013-08-24">
         * <name><![CDATA[茉建寺埃莉諾的非主流科學研究室]]></name>
         * <chapter cid="49057"><![CDATA[插圖]]></chapter>
         * </book>
         *
         * <book aid="1469" date="2013-08-05">
         * <name><![CDATA[塔京靈魂術士]]></name>
         * <chapter cid="48537"><![CDATA[插圖]]></chapter>
         * </book>
         *
         * <book aid="1087" date="2013-05-15">
         * <name><![CDATA[我的她是戰爭妖精]]></name>
         * <chapter cid="46779"><![CDATA[插圖]]></chapter>
         * </book>
         *
         * </metadata>
         */

        return getEncryptedCV("action=bookcase&t=" + getLANG(l));
    }

    public static ContentValues getAddToBookshelfParams(int aid) {
        // 新增书架 aid为文章ID
        return getEncryptedCV("action=bookcase&do=add&aid=" + aid);
    }

    public static ContentValues getDelFromBookshelfParams(int aid) {
        // 删除书架 aid为文章ID
        return getEncryptedCV("action=bookcase&do=del&aid=" + aid);
    }

    public static ContentValues getCommentListParams(int aid, int page) {
        // 书评列表 aid为文章ID  page不得为空（从1开始）

        // Return:
        //

        return getEncryptedCV("action=review&do=list&aid=" + aid + "&page=" + page);
    }

    public static ContentValues getCommentContentParams(int rid, int page) {
        // 书评内容 rid为主题ID（不是aid）  page不得为空

        // Return:
        //

        return getEncryptedCV("action=review&do=show&rid=" + rid + "&page=" + page);
    }

    public static ContentValues getCommentNewThreadParams(int aid, String title, String content) {
        // 书评发帖 aid为文章ID
        // 书评那边限制不少于7个中文字符才可发送，每次发送间隔10s以上
        // 采用简体！

        // 需要敏感词过滤，特殊符号处理

        return getEncryptedCV("action=review&do=post&aid=" + aid + "&title="+ LightBase64.EncodeBase64(title)
                +"&content=" + LightBase64.EncodeBase64(content));
    }

    public static ContentValues getCommentReplyParams(int rid, String content) {
        // 书评回帖 rid为主题ID
        // 书评那边限制不少于7个中文字符才可发送，每次发送间隔10s以上
        // 采用简体！

        // 需要敏感词过滤，特殊符号处理

        return getEncryptedCV("action=review&do=reply&rid=" + rid + "&content=" + LightBase64.EncodeBase64(content));
    }

}
