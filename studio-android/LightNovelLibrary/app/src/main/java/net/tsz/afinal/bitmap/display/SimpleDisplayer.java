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
package net.tsz.afinal.bitmap.display;

import net.tsz.afinal.bitmap.core.BitmapDisplayConfig;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

public class SimpleDisplayer implements Displayer{
	
	public void loadCompletedisplay(View imageView,Bitmap bitmap,BitmapDisplayConfig config){
		switch (config.getAnimationType()) {
		case BitmapDisplayConfig.AnimationType.fadeIn:
			fadeInDisplay(imageView,bitmap);
			break;
		case BitmapDisplayConfig.AnimationType.userDefined:
			animationDisplay(imageView,bitmap,config.getAnimation());
			break;
		default:
			break;
		}
	}
	
	
	public void loadFailDisplay(View imageView,Bitmap bitmap){
		if(imageView instanceof ImageView){
			((ImageView)imageView).setImageBitmap(bitmap);
		}else{
			imageView.setBackgroundDrawable(new BitmapDrawable(bitmap));
		}
	}
	
	
	
	private void fadeInDisplay(View imageView,Bitmap bitmap){
		final TransitionDrawable td =
                new TransitionDrawable(new Drawable[] {
                        new ColorDrawable(android.R.color.transparent),
                        new BitmapDrawable(imageView.getResources(), bitmap)
                });
        if(imageView instanceof ImageView){
			((ImageView)imageView).setImageDrawable(td);
		}else{
			imageView.setBackgroundDrawable(td);
		}
        td.startTransition(300);
	}
	
	
	private void animationDisplay(View imageView,Bitmap bitmap,Animation animation){
		animation.setStartTime(AnimationUtils.currentAnimationTimeMillis());		
        if(imageView instanceof ImageView){
			((ImageView)imageView).setImageBitmap(bitmap);
		}else{
			imageView.setBackgroundDrawable(new BitmapDrawable(bitmap));
		}
        imageView.startAnimation(animation);
	}

}
