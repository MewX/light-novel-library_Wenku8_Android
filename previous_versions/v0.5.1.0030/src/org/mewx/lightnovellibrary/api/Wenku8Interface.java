/**
 *  Wenku8 Interface
 **
 *  This class achieve the protocol:
 *      get args, encrypt, transfer to net-function, return results;
 * 
 *  NOTE: This file will be replaced in order to protect the protocol :(
 *        Because someone might want to capture website's database.
 **/

package org.mewx.lightnovellibrary.api;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.mewx.lightnovellibrary.util.LightBase64;
import org.mewx.lightnovellibrary.util.LightNetwork;

public class Wenku8Interface {
	final public static String BaseURL = "";

	// Here define the constance of language id

	public static enum LANG {
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

	public static enum NOVELSORTBY {
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

	private static String getNOVELSORTBY(NOVELSORTBY n) {
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

	public static NameValuePair getNovelCover(int aid) {
		// get the aid, and return a "jpg" file or other, in binary
		return null;
	}

	public static NameValuePair getNovelShortInfo(int aid, LANG l) {
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
		return null;
	}

	public static NameValuePair getNovelFullIntro(int aid, LANG l) {
		// get full XML intro of a novel, here is an example:
		// --------------------------------------------------
		// 　　在劍與魔法作為一股強大力量的世界裡，克雷歐過著只有繪畫是唯一生存意義的孤獨生活。
		// 　　不過生於名門的他，為了取得繼承人資格必須踏上試煉之旅。
		// 　　踏入禁忌森林的他，遇見一名半人半植物的魔物。
		// 　　輕易被抓的克雷歐設法勾起少女的興趣得到幫助，卻又被她當成寵物一般囚禁起來。
		// 　　兩人從此展開不可思議的同居時光，這樣的生活令他感到很安心。
		// 　　但平靜的日子沒有持續太久……
		// 　　描繪人與魔物的戀情，溫暖人心的奇幻故事。
		return null;
	}

	public static NameValuePair getNovelFullMeta(int aid, LANG l) {
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
		return null;
	}

	public static NameValuePair getNovelIndex(int aid, LANG l) {
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
		return null;
	}

	public static NameValuePair getNovelContent(int aid, int cid, LANG l) {
		// get full content of an article of a novel,
		// the images should be processed then, here is an example:
		// --------------------------------------------------------
		// 第一卷 告白于苍刻之夜 插图
		// ...... ......
		// <!--image-->http://pic.wenku8.cn/pictures/1/1305/41759/50471.jpg<!--image-->
		// <!--image-->http://pic.wenku8.cn/pictures/1/1305/41759/50472.jpg<!--image-->
		// <!--image-->http://pic.wenku8.cn/pictures/1/1305/41759/50473.jpg<!--image-->
		// ...... ......
		return null;
	}

	// ##########
	// # Here test: action=book&do=vote&aid=1239 //推荐小说
	// # (就是网站上面那个喜欢小说 就推一下那个，app日限制5次/需要登录账号)
	// ##########
	// ReqTest07 = ''
	// #return getResult( ReqTest07, True );

	public static NameValuePair searchNovelByNovelName(String novelName, LANG l) {
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
		return null;
	}

	public static NameValuePair searchNovelByAuthorName(String authorName,
			LANG l) {
		// get a list of search results.
		// Note: there are extra line-break.
		return null;
	}

	public static NameValuePair getNovelList(NOVELSORTBY n, int page) {
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
		return null;
	}

	public static NameValuePair getNovelListWithInfo(NOVELSORTBY n, int page,
			LANG l) {
		// get novel list with info digest
		// -------------------------------
		// <?xml version="1.0" encoding="utf-8"?>
		// <result>
		// <page num='166'/>
		//
		// <item aid='1143'>
		// <data name='Title'><![CDATA[约会大作战(DATE A LIVE)]]></data>
		// <data name='TotalHitsCount' value='2200395'/>
		// <data name='PushCount' value='164396'/>
		// <data name='FavCount' value='15114'/>
		// </item>
		//
		// <item aid='1034'>
		// <data name='Title'><![CDATA[恶魔高校DxD(High School DxD)]]></data>
		// <data name='TotalHitsCount' value='2316361'/>
		// <data name='PushCount' value='153422'/>
		// <data name='FavCount' value='14416'/>
		// </item>
		// ...... ......
		// </result>
		return null;
	}

	public static NameValuePair getLibraryList() {
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
		return null;
	}

	public static NameValuePair getNovelListByLibrary(int sortId, int page) {
		// sortId is from "getLibraryList"
		return null;
	}

	public static NameValuePair getNovelListByLibraryWithInfo(int sortId,
			int page, LANG l) {
		// sortId is from "getLibraryList"
		return null;
	}
}
