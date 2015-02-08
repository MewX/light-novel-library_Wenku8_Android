/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.tsz.afinal.bitmap.core;

import java.util.ArrayList;

public class BytesBufferPool {


    public static class BytesBuffer {
        public byte[] data;
        public int offset;
        public int length;

        private BytesBuffer(int capacity) {
            this.data = new byte[capacity];
        }
    }

    private final int mPoolSize;
    private final int mBufferSize;
    private final ArrayList<BytesBuffer> mList;

    public BytesBufferPool(int poolSize, int bufferSize) {
        mList = new ArrayList<BytesBuffer>(poolSize);
        mPoolSize = poolSize;
        mBufferSize = bufferSize;
    }

    public synchronized BytesBuffer get() {
        int n = mList.size();
        return n > 0 ? mList.remove(n - 1) : new BytesBuffer(mBufferSize);
    }

    public synchronized void recycle(BytesBuffer buffer) {
        if (buffer.data.length != mBufferSize) return;
        if (mList.size() < mPoolSize) {
            buffer.offset = 0;
            buffer.length = 0;
            mList.add(buffer);
        }
    }

    public synchronized void clear() {
        mList.clear();
    }
}
