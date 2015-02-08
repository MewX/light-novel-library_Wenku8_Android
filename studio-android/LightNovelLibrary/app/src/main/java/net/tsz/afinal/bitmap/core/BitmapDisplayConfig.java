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

import android.graphics.Bitmap;
import android.view.animation.Animation;

public class BitmapDisplayConfig {
	
	
	private int bitmapWidth;
	private int bitmapHeight;
	
	private Animation animation;
	
	private int animationType;
	private Bitmap loadingBitmap;
	private Bitmap loadfailBitmap;
	

	public int getBitmapWidth() {
		return bitmapWidth;
	}

	public void setBitmapWidth(int bitmapWidth) {
		this.bitmapWidth = bitmapWidth;
	}

	public int getBitmapHeight() {
		return bitmapHeight;
	}

	public void setBitmapHeight(int bitmapHeight) {
		this.bitmapHeight = bitmapHeight;
	}

	public Animation getAnimation() {
		return animation;
	}

	public void setAnimation(Animation animation) {
		this.animation = animation;
	}

	public int getAnimationType() {
		return animationType;
	}

	public void setAnimationType(int animationType) {
		this.animationType = animationType;
	}

	public Bitmap getLoadingBitmap() {
		return loadingBitmap;
	}

	public void setLoadingBitmap(Bitmap loadingBitmap) {
		this.loadingBitmap = loadingBitmap;
	}

	public Bitmap getLoadfailBitmap() {
		return loadfailBitmap;
	}

	public void setLoadfailBitmap(Bitmap loadfailBitmap) {
		this.loadfailBitmap = loadfailBitmap;
	}

	
	public class AnimationType{
		public static final int userDefined = 0;
		public static final int fadeIn = 1;
	}

}
