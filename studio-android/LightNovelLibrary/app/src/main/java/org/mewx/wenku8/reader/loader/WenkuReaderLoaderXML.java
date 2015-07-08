package org.mewx.wenku8.reader.loader;

import android.graphics.Bitmap;

/**
 * Created by MewX on 2015/7/8.
 *
 * Raw data loader.
 */
public class WenkuReaderLoaderXML extends WenkuReaderLoader {
    @Override
    void initLoader(String srcPath) {

    }

    @Override
    ElementType getNextType() {
        return null;
    }

    @Override
    String getNextAsString() {
        return null;
    }

    @Override
    Bitmap getNextAsBitmap() {
        return null;
    }

    @Override
    ElementType getPreviousType() {
        return null;
    }

    @Override
    String getPreviousAsString() {
        return null;
    }

    @Override
    Bitmap getPreviousAsBitmap() {
        return null;
    }

    @Override
    int getElementCount() {
        return 0;
    }

    @Override
    int getCurrentIndex() {
        return 0;
    }

    @Override
    int setCurrentIndex(int i) {
        return 0;
    }

    @Override
    void closeLoader() {

    }
}
