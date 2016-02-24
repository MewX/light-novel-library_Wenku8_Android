package org.mewx.wenku8.global.api;

import android.util.Log;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;

import org.mewx.wenku8.MyApp;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by MewX on 2015/6/6.
 * Old Novel Content Parser.
 */
public class OldNovelContentParser {
    public static class NovelContent {
        public char type = 't'; // 't' - text (default); 'i' - img
        public String content = "";
    }

    public static List<NovelContent> parseNovelContent(String raw, MaterialDialog pDialog) {
        List<NovelContent> result = new ArrayList<>();

        // use split
        String[] s = raw.split("\r\n");
        int temp;
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
                nc.content = t.trim(); //.replaceAll("[ |　]", " ").trim();
                result.add(nc);

                // update progress
                if (pDialog != null)
                    pDialog.setMaxProgress(result.size());
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
                        pDialog.setMaxProgress(result.size());

                }
            }
        }

        return result;

    }

    public static List<NovelContent> NovelContentParser_onlyImage(String raw) {
        List<NovelContent> result = new ArrayList<>();

        // use split
        String[] s = raw.split("\r\n");
        int temp;
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
