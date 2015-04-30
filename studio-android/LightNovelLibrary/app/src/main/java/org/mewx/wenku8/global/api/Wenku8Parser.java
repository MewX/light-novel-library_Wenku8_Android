package org.mewx.wenku8.global.api;

import android.util.Log;

import org.mewx.wenku8.global.GlobalConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by MewX on 2015/4/21.
 */
public class Wenku8Parser {

    public static List<Integer> parseNovelItemList(String str, int page) {
        List<Integer> list = new ArrayList<Integer>();

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

        // The returning list of this xml is: (total page, aids)
        // { 166, 1143, 1034, 1213, 1, 1011, 1192, 433, 47, 7, 374 }

        final char SEPERATOR = '\''; // seperator

        // get total page
        int beg = 0, temp;
        beg = str.indexOf(SEPERATOR);
        temp = str.indexOf(SEPERATOR, beg + 1);
        if (beg == -1 || temp == -1) return null; // this is an exception
        list.add(Integer.parseInt(str.substring(beg + 1, temp)));
        if (GlobalConfig.inDebugMode())
            Log.v("MewX", "Add novel page number: " + list.get(list.size() - 1));
        beg = temp + 1; // prepare for loop

        // init array
        while (true) {
            beg = str.indexOf(SEPERATOR, beg);
            temp = str.indexOf(SEPERATOR, beg + 1);
            if (beg == -1 || temp == -1) break;

            list.add(Integer.parseInt(str.substring(beg + 1, temp)));
            if (GlobalConfig.inDebugMode())
                Log.v("MewX", "Add novel aid: " + list.get(list.size() - 1));

            beg = temp + 1; // prepare for next round
        }

        return list;
    }

}
