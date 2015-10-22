package org.mewx.wenku8.global.api;

import android.util.Log;

import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.util.LightTool;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by MewX on 2015/1/21.
 * Novel Item List.
 */
public class NovelItemList {


    List<Integer> l;
    int currentPage;
    int totalPage;
    int lastRecord; // this save the last add number for reversing operation

    // Variables
    private boolean parseStatus; // default false

    /**
     * Init the whole struct with the received XML string
     *
     * @param str only str[0] is available, because I use array for pass by reference
     */
    public NovelItemList(String[] str, int page) {
        setNovelItemList(str, page);
    }

    public NovelItemList() {
        // init all values
        parseStatus = false;
        currentPage = 1;
        totalPage = 1;
    }

    public void setNovelItemList(String[] str, int page) {
        parseStatus = parseNovelItemList(str, page);
    }

    /**
     * get parse status
     *
     * @return true - parsed, else failed.
     */
    public boolean getParseStatus() {
        return parseStatus;
    }

    public int getTotalPage() {
        return totalPage;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public List<Integer> getNovelItemList() {
        return l;
    }

    private boolean parseNovelItemList(String[] str, int page) {
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

        final char SEPERATOR = '\''; // seperator
        //if( page > totalPage ) return true;
        currentPage = page;
        if(l!=null) {
            lastRecord = l.size()==0?0:l.size()/10*10; // save last size
            Log.i("MewX","set lastRecord: "+Integer.toString(lastRecord));
        }
        else
        lastRecord = 0;

        // get total page
        int beg, temp;
        beg = str[0].indexOf(SEPERATOR);
        temp = str[0].indexOf(SEPERATOR, beg + 1);
        if (beg == -1 || temp == -1) return false; // this is an exception
        if(LightTool.isInteger(str[0].substring(beg + 1, temp)))
            totalPage = Integer.parseInt(str[0].substring(beg + 1, temp));
        Log.v("MewX", "TotalPage = " + totalPage + "; CurrentPage = " + currentPage + ".");
        beg = temp + 1; // prepare for loop

        // init array
        if(l==null)
            l = new ArrayList<>();
        while (true) {
            beg = str[0].indexOf(SEPERATOR, beg);
            temp = str[0].indexOf(SEPERATOR, beg + 1);
            if (beg == -1 || temp == -1) break;

            if(LightTool.isInteger(str[0].substring(beg + 1, temp)))
                l.add(Integer.parseInt(str[0].substring(beg + 1, temp)));
            Log.v("MewX", "Add novel aid: " + l.get(l.size() - 1));

            beg = temp + 1; // prepare for next round
        }

        return true;
    }

    public void requestForReverse(){
        for( int i = lastRecord; i < l.size(); i ++ )
            l.remove(lastRecord);
    }
}
