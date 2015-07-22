package org.mewx.wenku8.reader.loader;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.OldNovelContentParser;

import java.util.List;

/**
 * Created by MewX on 2015/7/8.
 *
 * Raw data loader. Need async call!
 */
public class WenkuReaderLoaderXML extends WenkuReaderLoader {

    private int currentIndex = 0;
    private List<OldNovelContentParser.NovelContent> nc = null; // 't'-text; 'i'-image
    public String chapterName;

    public WenkuReaderLoaderXML(List<OldNovelContentParser.NovelContent> onc) {
        nc = onc;
    }

    @Override
    public void setChapterName(String name) {
        chapterName = name;
    }

    @Override
    public String getChapterName() {
        return chapterName;
    }

    @Override
    public boolean hasNext(int wordIndex) {
        if(currentIndex < nc.size() && currentIndex >= 0) {
            // size legal
            if(currentIndex + 1 < nc.size()) {
                // remain one more
                return true;
            }
            else {
                 // last one
                if(nc.get(currentIndex).type == 't' && wordIndex + 1 < nc.get(currentIndex).content.length()) {
                    // text but not last word
                    return true;
                }
                else if(nc.get(currentIndex).type != 't' && wordIndex == 0) {
                    // image
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean hasPrevious(int wordIndex) {
        if(currentIndex < nc.size() && currentIndex >= 0) {
            // size legal
            if(currentIndex - 1 >= 0) {
                // one more ahead
                return true;
            }
            else {
                // first one
                if(nc.get(currentIndex).type == 't' && wordIndex - 1 >= 0) {
                    // one more word ahead
                    return true;
                }
                else if(nc.get(currentIndex).type != 't' && wordIndex == nc.get(currentIndex).content.length() - 1)
                    // image previous use index last
                    return true;
            }
        }
        return false;
    }

    @Override
    public ElementType getNextType() {
        // nullable
        if(currentIndex + 1 < nc.size() && currentIndex >= 0) {
            if(currentIndex != nc.size() - 1)
                return intepreteOldSign(nc.get(currentIndex + 1).type);
        }
        return null;
    }

    @Override
    public String getNextAsString() {
        if(currentIndex + 1 < nc.size() && currentIndex >= 0) {
            currentIndex ++;
            return nc.get(currentIndex).content;
        }
        return null;
    }

    @Override
    public Bitmap getNextAsBitmap() {
        // Async get bitmap from local or internet
        if(currentIndex + 1 < nc.size() && currentIndex >= 0) {
            currentIndex++;
            String imgFileName = GlobalConfig.generateImageFileNameByURL(nc.get(currentIndex).content);
            String path = GlobalConfig.getAvailableNovolContentImagePath(imgFileName);

            if (path == null || path.equals("")) {
                GlobalConfig.saveNovelContentImage(nc.get(currentIndex).content);
                String name = GlobalConfig.generateImageFileNameByURL(nc.get(currentIndex).content);
                path = GlobalConfig.getAvailableNovolContentImagePath(name);
            }

            // load bitmap
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            Bitmap bm = BitmapFactory.decodeFile(path, options);
            if(bm != null)
                return bm;
        }

        return null;
    }

    @Override
    public ElementType getCurrentType() {
        // nullable
        if(currentIndex < nc.size() && currentIndex >= 0) {
            return intepreteOldSign(nc.get(currentIndex).type);
        }
        return null;
    }

    @Override
    public String getCurrentAsString() {
        if(currentIndex < nc.size() && currentIndex >= 0) {
            return nc.get(currentIndex).content;
        }
        return null;
    }

    @Override
    public Bitmap getCurrentAsBitmap() {
        // Async get bitmap from local or internet
        if(currentIndex < nc.size() && currentIndex >= 0) {
            String imgFileName = GlobalConfig.generateImageFileNameByURL(nc.get(currentIndex).content);
            String path = GlobalConfig.getAvailableNovolContentImagePath(imgFileName);

            if (path == null || path.equals("")) {
                GlobalConfig.saveNovelContentImage(nc.get(currentIndex).content);
                String name = GlobalConfig.generateImageFileNameByURL(nc.get(currentIndex).content);
                path = GlobalConfig.getAvailableNovolContentImagePath(name);
            }

            // load bitmap
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            Bitmap bm = BitmapFactory.decodeFile(path, options);
            if(bm != null)
                return bm;
        }
        return null;
    }

    @Override
    public ElementType getPreviousType() {
        // nullable
        if(currentIndex < nc.size() && currentIndex - 1 >= 0) {
            if(currentIndex != 0)
                return intepreteOldSign(nc.get(currentIndex - 1).type);
        }
        return null;
    }

    @Override
    public String getPreviousAsString() {
        if(currentIndex < nc.size() && currentIndex - 1 >= 0) {
            currentIndex --;
            return nc.get(currentIndex).content;
        }
        return null;
    }

    @Override
    public Bitmap getPreviousAsBitmap() {
        // Async get bitmap from local or internet
        if(currentIndex < nc.size() && currentIndex - 1 >= 0) {
            currentIndex--;
            String imgFileName = GlobalConfig.generateImageFileNameByURL(nc.get(currentIndex).content);
            String path = GlobalConfig.getAvailableNovolContentImagePath(imgFileName);

            if (path == null || path.equals("")) {
                GlobalConfig.saveNovelContentImage(nc.get(currentIndex).content);
                String name = GlobalConfig.generateImageFileNameByURL(nc.get(currentIndex).content);
                path = GlobalConfig.getAvailableNovolContentImagePath(name);
            }

            // load bitmap
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            Bitmap bm = BitmapFactory.decodeFile(path, options);
            if(bm != null)
                return bm;
        }
        return null;
    }

    @Override
    public int getStringLength(int n) {
        if(n >= 0 && n < getElementCount())
            return nc.get(n).content.length();
        return 0;
    }

    @Override
    public int getElementCount() {
        return nc.size();
    }

    @Override
    public int getCurrentIndex() {
        return currentIndex;
    }

    @Override
    public void setCurrentIndex(int i) {
        currentIndex = i;
    }

    @Override
    public void closeLoader() {
        nc = null;
    }

    private ElementType intepreteOldSign(char s) {
        switch (s) {
            case 't': return ElementType.TEXT;
            case 'i': return ElementType.IMAGE_DEPENDENT;
            default: return ElementType.TEXT;
        }
    }
}
