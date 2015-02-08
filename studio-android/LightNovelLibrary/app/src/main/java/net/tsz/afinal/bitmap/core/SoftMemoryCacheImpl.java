/**
 * Copyright (c) 2012-2013, Michael Yang 杨福海 (www.yangfuhai.com).
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

import java.lang.ref.SoftReference;
import java.util.HashMap;

import android.graphics.Bitmap;

public class SoftMemoryCacheImpl implements IMemoryCache {

	private final HashMap<String, SoftReference<Bitmap>> mMemoryCache;
	
	public SoftMemoryCacheImpl(int size) {
		
		mMemoryCache = new HashMap<String, SoftReference<Bitmap>>();
	}
	
	@Override
	public void put(String key, Bitmap bitmap) {
		mMemoryCache.put(key, new SoftReference<Bitmap>(bitmap));
	}

	@Override
	public Bitmap get(String key) {
		 SoftReference<Bitmap> memBitmap = mMemoryCache.get(key);
         if(memBitmap!=null){
         	return memBitmap.get();
         }
         return null;
	}

	@Override
	public void evictAll() {
		mMemoryCache.clear();
	}

	@Override
	public void remove(String key) {
		mMemoryCache.remove(key);
	}

}
