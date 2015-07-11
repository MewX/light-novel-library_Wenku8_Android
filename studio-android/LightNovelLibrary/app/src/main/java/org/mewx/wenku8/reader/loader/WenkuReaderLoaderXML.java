package org.mewx.wenku8.reader.loader;

import android.graphics.Bitmap;

/**
 * Created by MewX on 2015/7/8.
 *
 * Raw data loader.
 */
public class WenkuReaderLoaderXML extends WenkuReaderLoader {
    @Override
    public void initLoader(String srcPath) {

    }

    @Override
    public boolean hasNext(int wordIndex) {
        return false;
    }

    @Override
    public boolean hasPrevious(int wordIndex) {
        return false;
    }

    @Override
    public ElementType getNextType() {
        return null;
    }

    @Override
    public String getNextAsString() {
        return null;
    }

    @Override
    public Bitmap getNextAsBitmap() {
        return null;
    }

    @Override
    public ElementType getPreviousType() {
        return null;
    }

    @Override
    public String getPreviousAsString() {
        return null;
    }

    @Override
    public Bitmap getPreviousAsBitmap() {
        return null;
    }

    @Override
    public int getElementCount() {
        return 0;
    }

    @Override
    public int getCurrentIndex() {
        return 0;
    }

    @Override
    public int setCurrentIndex(int i) {
        return 0;
    }

    @Override
    public void closeLoader() {

    }
}
