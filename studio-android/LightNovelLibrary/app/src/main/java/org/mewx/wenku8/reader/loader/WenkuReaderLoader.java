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

    public abstract void initLoader(String srcPath);

    public abstract boolean hasNext(int wordIndex); // word in current line

    public abstract boolean hasPrevious(int wordIndex); // word in current line

    public abstract ElementType getNextType(); // next is {index}, nullable

    public abstract String getNextAsString();

    public abstract Bitmap getNextAsBitmap();

    public abstract ElementType getPreviousType(); // nullable

    public abstract String getPreviousAsString();

    public abstract Bitmap getPreviousAsBitmap();

    public abstract int getElementCount();

    public abstract int getCurrentIndex(); // from 0, to {Count - 1}

    public abstract int setCurrentIndex(int i); // set a index, should optimize for the same or relation lines

    public abstract void closeLoader();

}
