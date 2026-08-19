package org.mewx.wenku8.global.api.custom;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import org.mewx.wenku8.global.api.NovelItemInfoUpdate;
import org.mewx.wenku8.util.CrashReporter;

public class NovelListWithInfoParser {
    public static class Result {
        public int pageNum;
        public List<NovelItemInfoUpdate> items;

        public Result() {
            this.pageNum = 0;
            this.items = new ArrayList<>();
        }
    }

    @Nullable
    public static Result parse(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return null;
        }

        try {
            JSONObject json = new JSONObject(jsonString);
            Result result = new Result();
            if (json.has("page_num")) {
                result.pageNum = json.getInt("page_num");
            }

            if (json.has("items")) {
                JSONArray itemsArray = json.getJSONArray("items");
                for (int i = 0; i < itemsArray.length(); i++) {
                    JSONObject item = itemsArray.getJSONObject(i);
                    int aid = item.optInt("aid", 0);
                    NovelItemInfoUpdate info = new NovelItemInfoUpdate(aid);

                    if (item.has("Title")) info.title = item.getString("Title");
                    if (item.has("Author")) info.author = item.getString("Author");
                    if (item.has("BookStatus")) info.status = item.getString("BookStatus");
                    if (item.has("LastUpdate")) info.update = item.getString("LastUpdate");
                    if (item.has("IntroPreview")) {
                        info.intro_short = item.getString("IntroPreview").replaceAll("[ |　]", " ").trim();
                    }
                    if (item.has("Tags")) info.tags = item.getString("Tags");

                    // Cache the result so other components can reuse it
                    NovelItemInfoUpdate.putToCache(info);

                    result.items.add(info);
                }
            }
            return result;

        } catch (JSONException e) {
            CrashReporter.recordException("NovelListWithInfoParser.parse", e);
            return null;
        }
    }
}
