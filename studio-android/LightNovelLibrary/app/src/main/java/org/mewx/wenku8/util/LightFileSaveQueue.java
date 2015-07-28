package org.mewx.wenku8.util;

import java.util.Queue;

/**
 * Created by MewX on 2015/1/24.
 * This class is used for save more and more file in one time, and not in the main thread.
 */
public class LightFileSaveQueue {
    private class F {
        String fileName;
        byte[] fileContent;
    }

    private Queue q;
}
