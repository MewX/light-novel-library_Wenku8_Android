/**
 *  XML Parser
 **
 *  Process the received XML byte array.
 *  And convert them to ArrayList for returning.
 **/

package org.mewx.lightnovellibrary.component;

import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import android.util.Log;

public class XMLParser {
	// NovelListWithInfo
	static public class NovelListWithInfo {
		public int aid = 0;
		public String name = "";
		public int hit = 0;
		public int push = 0;
		public int fav = 0;
	}

	static public int getNovelListWithInfoPageNum(String xml) {
		try {
			XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
			XmlPullParser xmlPullParser = factory.newPullParser();
			xmlPullParser.setInput(new StringReader(xml));
			int eventType = xmlPullParser.getEventType();

			while (eventType != XmlPullParser.END_DOCUMENT) {
				switch (eventType) {
				case XmlPullParser.START_DOCUMENT:
					break;

				case XmlPullParser.START_TAG:
					if ("page".equals(xmlPullParser.getName())) {
						return new Integer(xmlPullParser.getAttributeValue(0));
					}
					break;
				}
				eventType = xmlPullParser.next();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 0; // default
	}

	static public ArrayList<NovelListWithInfo> getNovelListWithInfo(String xml) {
		try {
			XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
			XmlPullParser xmlPullParser = factory.newPullParser();
			ArrayList<NovelListWithInfo> l = null;
			NovelListWithInfo n = null;
			xmlPullParser.setInput(new StringReader(xml));
			int eventType = xmlPullParser.getEventType();

			while (eventType != XmlPullParser.END_DOCUMENT) {
				switch (eventType) {
				case XmlPullParser.START_DOCUMENT:
					l = new ArrayList<NovelListWithInfo>();
					break;

				case XmlPullParser.START_TAG:

					if ("item".equals(xmlPullParser.getName())) {
						n = new NovelListWithInfo();
						n.aid = new Integer(xmlPullParser.getAttributeValue(0));
						// Log.v("MewX-XML", "aid=" + n.aid);
					} else if ("data".equals(xmlPullParser.getName())) {
						if ("Title".equals(xmlPullParser.getAttributeValue(0))) {
							n.name = xmlPullParser.nextText();
							// Log.v("MewX-XML", n.name);
						} else if ("TotalHitsCount".equals(xmlPullParser
								.getAttributeValue(0))) {
							n.hit = new Integer(
									xmlPullParser.getAttributeValue(1));
							// Log.v("MewX-XML", "hit=" + n.hit);
						} else if ("PushCount".equals(xmlPullParser
								.getAttributeValue(0))) {
							n.push = new Integer(
									xmlPullParser.getAttributeValue(1));
							// Log.v("MewX-XML", "push=" + n.push);
						} else if ("FavCount".equals(xmlPullParser
								.getAttributeValue(0))) {
							n.fav = new Integer(
									xmlPullParser.getAttributeValue(1));
							// Log.v("MewX-XML", "fav=" + n.fav);
						}
					}
					break;

				case XmlPullParser.END_TAG:
					if ("item".equals(xmlPullParser.getName())) {
						Log.v("MewX-XML", n.aid + ";" + n.name + ";" + n.hit
								+ ";" + n.push + ";" + n.fav);
						l.add(n);
						n = null;
					}
					break;
				}
				eventType = xmlPullParser.next();
			}
			return l;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	// NovelIntro
	static public class NovelIntro {
		public int aid = 0;
		public String title = "";
		public String author = "";
		public int status = 0; // 0 - not; 1 - finished
		public String update = ""; // last update time
		public String intro_short = "";
		public String intro_full = "";
	}

	static public NovelIntro getNovelIntro(String xml) {
		try {
			XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
			XmlPullParser xmlPullParser = factory.newPullParser();
			NovelIntro ni = null;
			xmlPullParser.setInput(new StringReader(xml));
			int eventType = xmlPullParser.getEventType();

			while (eventType != XmlPullParser.END_DOCUMENT) {
				switch (eventType) {
				case XmlPullParser.START_DOCUMENT:// all start
					break;

				case XmlPullParser.START_TAG:

					if ("metadata".equals(xmlPullParser.getName())) {
						ni = new NovelIntro();
						// Log.v("MewX-XML", "aid=" + n.aid);
					} else if ("data".equals(xmlPullParser.getName())) {
						if ("Title".equals(xmlPullParser.getAttributeValue(0))) {
							ni.aid = new Integer(
									xmlPullParser.getAttributeValue(1));
							ni.title = xmlPullParser.nextText();
						} else if ("Author".equals(xmlPullParser
								.getAttributeValue(0))) {
							ni.author = xmlPullParser.getAttributeValue(1);
						} else if ("BookStatus".equals(xmlPullParser
								.getAttributeValue(0))) {
							ni.status = new Integer(
									xmlPullParser.getAttributeValue(1));
							// Log.v("MewX-XML", "push=" + n.push);
						} else if ("LastUpdate".equals(xmlPullParser
								.getAttributeValue(0))) {
							ni.update = xmlPullParser.getAttributeValue(1);
							// Log.v("MewX-XML", "fav=" + n.fav);
						} else if ("IntroPreview".equals(xmlPullParser
								.getAttributeValue(0))) {
							ni.intro_short = xmlPullParser.nextText();
							// Log.v("MewX-XML", "fav=" + n.fav);
						}
					}
					break;

				case XmlPullParser.END_TAG:
					if ("metadata".equals(xmlPullParser.getName())) {
						// nothing
					}
					break;
				}
				eventType = xmlPullParser.next();
			}
			return ni;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	// Volume List
	static public class ChapterInfo implements Serializable {
		public int cid;
		public String chapterName;
	}

	static public class VolumeList implements Serializable {
		public String volumeName;
		public int vid;
		public ArrayList<ChapterInfo> chapterList;
	}

	static public ArrayList<VolumeList> getVolumeList(String xml) {
		try {
			XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
			XmlPullParser xmlPullParser = factory.newPullParser();
			ArrayList<VolumeList> l = null;
			VolumeList vl = null;
			ChapterInfo ci = null;
			xmlPullParser.setInput(new StringReader(xml));
			int eventType = xmlPullParser.getEventType();

			while (eventType != XmlPullParser.END_DOCUMENT) {
				switch (eventType) {
				case XmlPullParser.START_DOCUMENT:
					l = new ArrayList<VolumeList>();
					break;

				case XmlPullParser.START_TAG:

					if ("volume".equals(xmlPullParser.getName())) {
						vl = new VolumeList();
						vl.chapterList = new ArrayList<ChapterInfo>();
						vl.vid = new Integer(xmlPullParser.getAttributeValue(0));

						// Here the returned text has some format error
						// And I will handle them then
						Log.v("MewX-XML", "+ " + vl.vid + "; ");
					} else if ("chapter".equals(xmlPullParser.getName())) {
						ci = new ChapterInfo();
						ci.cid = new Integer(xmlPullParser.getAttributeValue(0));
						ci.chapterName = xmlPullParser.nextText();
						Log.v("MewX-XML", ci.cid + "; " + ci.chapterName);
						vl.chapterList.add(ci);
						ci = null;
					}
					break;

				case XmlPullParser.END_TAG:
					if ("volume".equals(xmlPullParser.getName())) {
						l.add(vl);
						vl = null;
					}
					break;
				}
				eventType = xmlPullParser.next();
			}

			/** Handle the rest problem */
			// Problem like this:
			// <volume vid="41748"><![CDATA[第一卷 告白于苍刻之夜]]>
			// <chapter cid="41749"><![CDATA[序章]]></chapter>
			int currentIndex = 0;
			for (int i = 0; i < l.size(); i++) {
				currentIndex = xml.indexOf("volume", currentIndex);
				if (currentIndex != -1) {
					currentIndex = xml.indexOf("CDATA[", currentIndex);
					if (xml.indexOf("volume", currentIndex) != -1) {
						int beg = currentIndex + 6;
						int end = xml.indexOf("]]", currentIndex);

						if (end != -1) {
							l.get(i).volumeName = xml.substring(beg, end);
							Log.v("MewX-XML", "+ " + l.get(i).volumeName + "; ");
							currentIndex = end + 1;
						} else
							break;

					} else
						break;
				} else
					break;
			}

			return l;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	// Search result list
	static public ArrayList<Integer> getSearchResult(String xml) {
		if (xml.indexOf("java.net.") != -1)
			return null; // exception

		// This returned XML contains errors, so I have to handle this..
		ArrayList<Integer> l = new ArrayList<Integer>();

		int a = 0, temp = 0;
		while (true) {
			temp = xml.indexOf("aid=\'", a);
			if (temp == -1)
				break;

			a = temp + 5;
			temp = xml.indexOf("\'", a);
			if (temp == -1)
				break;
			else
				l.add(new Integer(xml.substring(a, temp)));

		}

		return l;
	}

	static public NovelListWithInfo getNovelShortInfoBySearching(String xml) {
		try {
			XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
			XmlPullParser xmlPullParser = factory.newPullParser();
			NovelListWithInfo n = null;
			xmlPullParser.setInput(new StringReader(xml));
			int eventType = xmlPullParser.getEventType();

			while (eventType != XmlPullParser.END_DOCUMENT) {
				switch (eventType) {
				case XmlPullParser.START_DOCUMENT:
					break;

				case XmlPullParser.START_TAG:

					if ("metadata".equals(xmlPullParser.getName())) {
						n = new NovelListWithInfo();
					} else if ("data".equals(xmlPullParser.getName())) {
						if ("Title".equals(xmlPullParser.getAttributeValue(0))) {
							n.name = xmlPullParser.nextText();
						} else if ("TotalHitsCount".equals(xmlPullParser
								.getAttributeValue(0))) {
							n.hit = new Integer(
									xmlPullParser.getAttributeValue(1));
						} else if ("PushCount".equals(xmlPullParser
								.getAttributeValue(0))) {
							n.push = new Integer(
									xmlPullParser.getAttributeValue(1));
						} else if ("FavCount".equals(xmlPullParser
								.getAttributeValue(0))) {
							n.fav = new Integer(
									xmlPullParser.getAttributeValue(1));
						}
					}
					break;
				}
				eventType = xmlPullParser.next();
			}
			return n;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	
	static public class NovelFullInfo {
		public int aid;
		public String title;
		public String author;
		public int dayHitsCount;
		public int totalHitsCount;
		public int pushCount;
		public int favCount;
		public String pressId;
		public String bookStatus; // just text, differ from "NovelIntro"
		public int bookLength;
		public String lastUpdate;
		public int latestSectionCid;
		public String latestSectionName;
		public String fullIntro; // fetch from another place
	}
	
	static public NovelFullInfo getNovelFullInfo(String xml) {
		try {
			XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
			XmlPullParser xmlPullParser = factory.newPullParser();
			NovelFullInfo nfi = null;
			xmlPullParser.setInput(new StringReader(xml));
			int eventType = xmlPullParser.getEventType();

			while (eventType != XmlPullParser.END_DOCUMENT) {
				switch (eventType) {
				case XmlPullParser.START_DOCUMENT:
					break;

				case XmlPullParser.START_TAG:

					if ("metadata".equals(xmlPullParser.getName())) {
						nfi = new NovelFullInfo();
					} else if ("data".equals(xmlPullParser.getName())) {
						if ("Title".equals(xmlPullParser.getAttributeValue(0))) {
							nfi.aid = new Integer(
									xmlPullParser.getAttributeValue(1));
							nfi.title = xmlPullParser.nextText();
						} else if ("Author".equals(xmlPullParser
								.getAttributeValue(0))) {
							nfi.author = xmlPullParser.getAttributeValue(1);
						} else if ("DayHitsCount".equals(xmlPullParser
								.getAttributeValue(0))) {
							nfi.dayHitsCount = new Integer(xmlPullParser.getAttributeValue(1));
						} else if ("TotalHitsCount".equals(xmlPullParser
								.getAttributeValue(0))) {
							nfi.totalHitsCount = new Integer(xmlPullParser.getAttributeValue(1));
						} else if ("PushCount".equals(xmlPullParser
								.getAttributeValue(0))) {
							nfi.pushCount = new Integer(xmlPullParser.getAttributeValue(1));
						} else if ("FavCount".equals(xmlPullParser
								.getAttributeValue(0))) {
							nfi.favCount = new Integer(xmlPullParser.getAttributeValue(1));
						} else if ("PressId".equals(xmlPullParser
								.getAttributeValue(0))) {
							nfi.pressId = xmlPullParser.getAttributeValue(1);
						} else if ("BookStatus".equals(xmlPullParser
								.getAttributeValue(0))) {
							nfi.bookStatus = xmlPullParser.getAttributeValue(1);
						} else if ("BookLength".equals(xmlPullParser
								.getAttributeValue(0))) {
							nfi.bookLength = new Integer(xmlPullParser.getAttributeValue(1));
						} else if ("LastUpdate".equals(xmlPullParser
								.getAttributeValue(0))) {
							nfi.lastUpdate = xmlPullParser.getAttributeValue(1);
						} else if ("LatestSection".equals(xmlPullParser
								.getAttributeValue(0))) {
							nfi.latestSectionCid = new Integer(
									xmlPullParser.getAttributeValue(1));
							nfi.latestSectionName=xmlPullParser.nextText();
						}
					}
					break;
				}
				eventType = xmlPullParser.next();
			}
			return nfi;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
