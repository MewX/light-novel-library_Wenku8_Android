package org.mewx.wenku8.reader.loader;

import android.graphics.Bitmap;

/**
 * Created by MewX on 2015/7/8.
 *
 * Parent of all loaders.
 */
public abstract class WenkuReaderLoader {
    /**
     * ElementType: the type of next element to get
     */
    enum ElementType {
        TEXT,
        IMAGE_INDEPENDENT, // be in different line from text
        IMAGE_DEPENDENT, // may be in the same line with text
    }

    abstract void initLoader(String srcPath);

    abstract ElementType getNextType(); // next is {index}, nullable

    abstract String getNextAsString();

    abstract Bitmap getNextAsBitmap();

    abstract ElementType getPreviousType(); // nullable

    abstract String getPreviousAsString();

    abstract Bitmap getPreviousAsBitmap();

    abstract int getElementCount();

    abstract int getCurrentIndex(); // from 0, to {Count - 1}

    abstract int setCurrentIndex(int i); // set a index

    abstract void closeLoader();

}
