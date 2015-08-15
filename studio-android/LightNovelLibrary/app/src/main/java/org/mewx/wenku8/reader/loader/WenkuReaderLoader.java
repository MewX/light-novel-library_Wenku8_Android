package org.mewx.wenku8.reader.loader;

import android.graphics.Bitmap;

/**
 * Created by MewX on 2015/7/8.
 * Parent of all loaders.
 */
public abstract class WenkuReaderLoader {
    /**
     * ElementType: the type of next element to get
     */
    public enum ElementType {
        TEXT,
        IMAGE_INDEPENDENT, // be in different line from text
        IMAGE_DEPENDENT, // may be in the same line with text
    }

    // public abstract void initLoader(String srcPath);

    public abstract void setChapterName(String name); // set chapter name

    public abstract String getChapterName(); // get chapter name

    public abstract boolean hasNext(int wordIndex); // word in current line

    public abstract boolean hasPrevious(int wordIndex); // word in current line

    public abstract ElementType getNextType(); // next is {index}, nullable (index keep)

    public abstract String getNextAsString(); // index ++

    public abstract Bitmap getNextAsBitmap(); // index ++

    public abstract ElementType getCurrentType(); //

    public abstract String getCurrentAsString(); // index keep

    public abstract Bitmap getCurrentAsBitmap(); // index keep

    public abstract ElementType getPreviousType(); // nullable (index keep)

    public abstract String getPreviousAsString(); // index --

    public abstract Bitmap getPreviousAsBitmap(); // index --

    public abstract int getStringLength(int n);

    public abstract int getElementCount();

    public abstract int getCurrentIndex(); // from 0, to {Count - 1}

    public abstract void setCurrentIndex(int i); // set a index, should optimize for the same or relation lines

    public abstract void closeLoader();

}
