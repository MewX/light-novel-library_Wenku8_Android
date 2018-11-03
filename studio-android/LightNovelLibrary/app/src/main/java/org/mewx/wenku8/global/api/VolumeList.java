package org.mewx.wenku8.global.api;

import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.util.LightCache;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by MewX on 2015/5/13.
 * Volume List.
 */
public class VolumeList implements Serializable {
    public String volumeName;
    public int vid;
    public boolean inLocal = false;
    public ArrayList<ChapterInfo> chapterList;

    public void cleanLocalCache() {
        for (ChapterInfo tempCi : this.chapterList) {
            String xml = GlobalConfig.loadFullFileFromSaveFolder("novel", tempCi.cid + ".xml");
            if (xml.length() == 0) {
                return;
            }
            List<OldNovelContentParser.NovelContent> nc = OldNovelContentParser.NovelContentParser_onlyImage(xml);
            for (int i = 0; i < nc.size(); i++) {
                if (nc.get(i).type == OldNovelContentParser.NovelContentType.IMAGE) {
                    String imgFileName = GlobalConfig.generateImageFileNameByURL(nc.get(i).content);
                    LightCache.deleteFile(
                            GlobalConfig.getFirstFullSaveFilePath() +
                                    GlobalConfig.imgsSaveFolderName + File.separator + imgFileName);
                    LightCache.deleteFile(
                            GlobalConfig.getSecondFullSaveFilePath() +
                                    GlobalConfig.imgsSaveFolderName + File.separator + imgFileName);
                }
            }
            LightCache.deleteFile(GlobalConfig.getFirstFullSaveFilePath(), "novel" + File.separator + tempCi.cid + ".xml");
            LightCache.deleteFile(GlobalConfig.getSecondFullSaveFilePath(), "novel" + File.separator + tempCi.cid + ".xml");
        }
        this.inLocal = false;
    }
}
