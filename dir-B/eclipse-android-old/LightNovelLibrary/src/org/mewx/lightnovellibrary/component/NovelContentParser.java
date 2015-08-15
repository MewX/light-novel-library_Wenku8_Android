package org.mewx.lightnovellibrary.component;

import java.util.ArrayList;
import java.util.List;

import android.app.ProgressDialog;
import android.util.Log;

public class NovelContentParser {
	public static class NovelContent {
		public char type = 't'; // 't' - text (default); 'i' - img
		public String content = "";
	}

	public static List<NovelContent> parseNovelContent(String raw,
			ProgressDialog pDialog) {
		List<NovelContent> result = new ArrayList<NovelContent>();

		// use split
		String[] s = raw.split("\r\n");
		int temp = 0;
		for (String t : s) {
			// escape empty line
			boolean isEmpty = true;
			for (int i = 0; i < t.length(); i++) {
				if (t.charAt(i) != ' ') {
					isEmpty = false;
					break;
				}
			}
			if (isEmpty)
				continue;

			// test
			temp = t.indexOf("<!--image-->", 0);
			if (temp == -1) {
				NovelContent nc = new NovelContent();
				nc.type = 't';
				nc.content = t;
				result.add(nc);

				// update progress
				if (pDialog != null)
					pDialog.setMax(result.size());
			} else {
				Log.v("MewX", "img index = " + temp);
				// nc.content = nc.content.substring(temp + 12,
				// nc.content.indexOf("<!--image-->", temp + 12));

				// one line contains more than one images
				temp = 0;
				while (true) {
					temp = t.indexOf("<!--image-->", temp);
					if (temp == -1)
						break;

					NovelContent nc2 = new NovelContent();
					int t2 = t.indexOf("<!--image-->", temp + 12);
					if (t2 < 0) {
						Log.v("MewX", "Breaked in parseNovelContent, t2 = "
								+ t2);
						NovelContent nc = new NovelContent();
						nc.type = 't';
						nc.content = t;
						result.add(nc);
						break;
					}
					nc2.content = t.substring(temp + 12, t2);
					nc2.type = 'i';
					result.add(nc2);
					temp = t2 + 12;

					// update progress
					if (pDialog != null)
						pDialog.setMax(result.size());

				}
			}
		}

		// int currentIndex = 0, temp;
		// while (true) {
		// if (currentIndex >= raw.length())
		// break;
		//
		// temp = raw.indexOf("\r\n", currentIndex);
		// Log.v("MewX", "\\n index = " + temp);
		// if (temp == -1 && currentIndex == raw.length() - 1)
		// break;
		// else {
		// if (temp == -1)
		// temp = raw.length();
		// NovelContent nc = new NovelContent();
		//
		// // find a NC
		// nc.content = raw.substring(currentIndex, temp);
		// currentIndex = temp + 2;
		//
		// // escape empty line
		// boolean isEmpty = true;
		// for (int i = 0; i < nc.content.length(); i++) {
		// if (nc.content.charAt(i) != ' ') {
		// isEmpty = false;
		// break;
		// }
		// }
		// if (isEmpty)
		// continue;
		// String ttt = "";
		// char[] bt = nc.content.toCharArray();
		// for (char b : bt)
		// ttt += String.format("%02X ", (int) b);
		// Log.v("MewX", "toCharArray(): " + ttt);
		//
		// // test
		// temp = nc.content.indexOf("<!--image-->", 0);
		// if (temp == -1) {
		// temp = nc.type = 't';
		// result.add(nc);
		//
		// // update progress
		// pDialog.setMax(result.size());
		// } else {
		// Log.v("MewX", "img index = " + temp);
		// nc.type = 'i';
		// // nc.content = nc.content.substring(temp + 12,
		// // nc.content.indexOf("<!--image-->", temp + 12));
		//
		// // one line contains more than one images
		// temp = 0;
		// while (true) {
		// temp = nc.content.indexOf("<!--image-->", temp);
		// if (temp == -1)
		// break;
		//
		// NovelContent nc2 = new NovelContent();
		// int t = nc.content.indexOf("<!--image-->", temp + 12);
		// nc2.content = nc.content.substring(temp + 12, t);
		// nc2.type = 'i';
		// result.add(nc2);
		// temp = t + 12;
		//
		// // update progress
		// pDialog.setMax(result.size());
		//
		// }
		// }
		// Log.v("MewX", "nc.content" + nc.content);
		// }
		// }

		return result;

	}

	public static List<NovelContent> NovelContentParser_onlyImage(String raw) {
		List<NovelContent> result = new ArrayList<NovelContent>();

		// use split
		String[] s = raw.split("\r\n");
		int temp = 0;
		for (String t : s) {
			// test
			temp = t.indexOf("<!--image-->", 0);
			if (temp != -1) {
				Log.v("MewX", "img index = " + temp);
				// nc.content = nc.content.substring(temp + 12,
				// nc.content.indexOf("<!--image-->", temp + 12));

				// one line contains more than one images
				temp = 0;
				while (true) {
					temp = t.indexOf("<!--image-->", temp);
					if (temp == -1)
						break;

					NovelContent nc2 = new NovelContent();
					int t2 = t.indexOf("<!--image-->", temp + 12);
					if (t2 < 0) {
						Log.v("MewX",
								"Breaked in NovelContentParser_onlyImage, t2 = "
										+ t2);
						break;
					}
					nc2.content = t.substring(temp + 12, t2);
					nc2.type = 'i';
					result.add(nc2);
					temp = t2 + 12;

				}
			}
		}
		return result;
	}

}
