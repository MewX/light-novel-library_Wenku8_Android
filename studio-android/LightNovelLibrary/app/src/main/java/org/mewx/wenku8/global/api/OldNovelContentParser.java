package org.mewx.wenku8.global.api;

import androidx.annotation.NonNull;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Created by MewX on 2015/6/6.
 * Old Novel Content Parser.
 */
public class OldNovelContentParser {
    private static final String TAG = OldNovelContentParser.class.getSimpleName();

    public enum NovelContentType {
        TEXT, IMAGE
    }

    public static class NovelContent {
        public NovelContentType type = NovelContentType.TEXT;
        public String content = "";
    }

    private static final String IMAGE_ENTRY = "<!--image-->";

    @NonNull
    public static List<NovelContent> parseNovelContent(@NonNull String raw, @NonNull IntConsumer setMaxProgress) {
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
            temp = t.indexOf(IMAGE_ENTRY, 0);
            if (temp == -1) {
                NovelContent nc = new NovelContent();
                nc.type = NovelContentType.TEXT;
                nc.content = t.trim();
                result.add(nc);

                // update progress
                setMaxProgress.accept(result.size());
            } else {
                Log.d(TAG, "img index = " + temp);

                // one line contains more than one images
                temp = 0;
                while (true) {
                    temp = t.indexOf(IMAGE_ENTRY, temp);
                    if (temp == -1)
                        break;

                    NovelContent nc2 = new NovelContent();
                    int t2 = t.indexOf(IMAGE_ENTRY, temp + IMAGE_ENTRY.length());
                    if (t2 < 0) {
                        Log.d(TAG, "Incomplete image pair, t2 = " + t2);
                        NovelContent nc = new NovelContent();
                        nc.type = NovelContentType.TEXT;
                        nc.content = t.trim();
                        result.add(nc);
                        break;
                    }
                    nc2.content = t.substring(temp + IMAGE_ENTRY.length(), t2);
                    nc2.type = NovelContentType.IMAGE;
                    result.add(nc2);
                    temp = t2 + IMAGE_ENTRY.length();

                    // update progress
                    setMaxProgress.accept(result.size());
                }
            }
        }

        return result;

    }

    @NonNull
    public static List<NovelContent> NovelContentParser_onlyImage(@NonNull String raw) {
        List<NovelContent> result = new ArrayList<>();

        // use split
        String[] s = raw.split("\r\n");
        int temp;
        for (String t : s) {
            // test
            temp = t.indexOf(IMAGE_ENTRY, 0);
            if (temp != -1) {
                Log.d(TAG, "img index = " + temp);

                // one line contains more than one images
                temp = 0;
                while (true) {
                    temp = t.indexOf(IMAGE_ENTRY, temp);
                    if (temp == -1)
                        break;

                    NovelContent nc2 = new NovelContent();
                    int t2 = t.indexOf(IMAGE_ENTRY, temp + IMAGE_ENTRY.length());
                    if (t2 < 0) {
                        Log.d(TAG, "Breaked in NovelContentParser_onlyImage, t2 = " + t2);
                        break;
                    }
                    nc2.content = t.substring(temp + IMAGE_ENTRY.length(), t2);
                    nc2.type = NovelContentType.IMAGE;
                    result.add(nc2);
                    temp = t2 + IMAGE_ENTRY.length();

                }
            }
        }
        return result;
    }
}
