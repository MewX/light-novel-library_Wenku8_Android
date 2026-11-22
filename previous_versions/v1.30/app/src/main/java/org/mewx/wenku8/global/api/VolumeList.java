package org.mewx.wenku8.global.api;

import java.util.ArrayList;

/**
 * Created by MewX on 2015/5/13.
 * Volume List.
 *
 * <p>Deliberately not Serializable. This carries every ChapterInfo in the volume, so passing
 * one through an Intent put a long series over the ~1MB Binder transaction buffer and threw
 * TransactionTooLargeException on exactly the most-engaged readers. The readers take aid + vid
 * and rebuild it from the cached index instead, and dropping the interface is what stops that
 * shortcut being available again.
 */
public class VolumeList {
    public String volumeName;
    public int vid;
    public boolean inLocal = false;
    public ArrayList<ChapterInfo> chapterList;

}
