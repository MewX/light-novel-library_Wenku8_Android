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
package net.tsz.afinal;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import net.tsz.afinal.bitmap.core.BitmapCache;
import net.tsz.afinal.bitmap.core.BitmapDisplayConfig;
import net.tsz.afinal.bitmap.core.BitmapProcess;
import net.tsz.afinal.bitmap.display.Displayer;
import net.tsz.afinal.bitmap.display.SimpleDisplayer;
import net.tsz.afinal.bitmap.download.Downloader;
import net.tsz.afinal.bitmap.download.SimpleDownloader;
import net.tsz.afinal.core.AsyncTask;
import net.tsz.afinal.utils.Utils;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ImageView;

public class FinalBitmap {
	private FinalBitmapConfig mConfig;
	private BitmapCache mImageCache;
	private BitmapProcess mBitmapProcess;
	private boolean mExitTasksEarly = false;
	private boolean mPauseWork = false;
	private final Object mPauseWorkLock = new Object();
	private Context mContext;
	private boolean mInit = false ;
	private ExecutorService bitmapLoadAndDisplayExecutor;

	private static FinalBitmap mFinalBitmap;
	
	////////////////////////// config method start////////////////////////////////////
	private FinalBitmap(Context context) {
		mContext = context;
		mConfig = new FinalBitmapConfig(context);
		configDiskCachePath(Utils.getDiskCacheDir(context, "afinalCache").getAbsolutePath());//配置缓存路径
		configDisplayer(new SimpleDisplayer());//配置显示器
		configDownlader(new SimpleDownloader());//配置下载器
	}
	
	/**
	 * 创建finalbitmap
	 * @param ctx
	 * @return
	 */
	public static synchronized FinalBitmap create(Context ctx){
		if(mFinalBitmap == null){
			mFinalBitmap = new FinalBitmap(ctx.getApplicationContext());
		}
		return mFinalBitmap;
	}
	
	
	
	/**
	 * 设置图片正在加载的时候显示的图片
	 * @param bitmap
	 */
	public FinalBitmap configLoadingImage(Bitmap bitmap) {
		mConfig.defaultDisplayConfig.setLoadingBitmap(bitmap);
		return this;
	}

	/**
	 * 设置图片正在加载的时候显示的图片
	 * @param bitmap
	 */
	public FinalBitmap configLoadingImage(int resId) {
		mConfig.defaultDisplayConfig.setLoadingBitmap(BitmapFactory.decodeResource(mContext.getResources(), resId));
		return this;
	}
	
	/**
	 * 设置图片加载失败时候显示的图片
	 * @param bitmap
	 */
	public FinalBitmap configLoadfailImage(Bitmap bitmap) {
		mConfig.defaultDisplayConfig.setLoadfailBitmap(bitmap);
		return this;
	}
	
	/**
	 * 设置图片加载失败时候显示的图片
	 * @param resId
	 */
	public FinalBitmap configLoadfailImage(int resId) {
		mConfig.defaultDisplayConfig.setLoadfailBitmap(BitmapFactory.decodeResource(mContext.getResources(), resId));
		return this;
	}
	
	
	/**
	 * 配置默认图片的小的高度
	 * @param bitmapHeight
	 */
	public FinalBitmap configBitmapMaxHeight(int bitmapHeight){
		mConfig.defaultDisplayConfig.setBitmapHeight(bitmapHeight);
		return this;
	}
	
	/**
	 * 配置默认图片的小的宽度
	 * @param bitmapHeight
	 */
	public FinalBitmap configBitmapMaxWidth(int bitmapWidth){
		mConfig.defaultDisplayConfig.setBitmapWidth(bitmapWidth);
		return this;
	}
	
	/**
	 * 设置下载器，比如通过ftp或者其他协议去网络读取图片的时候可以设置这项
	 * @param downlader
	 * @return
	 */
	public FinalBitmap configDownlader(Downloader downlader){
		mConfig.downloader = downlader;
		return this;
	}
	
	/**
	 * 设置显示器，比如在显示的过程中显示动画等
	 * @param displayer
	 * @return
	 */
	public FinalBitmap configDisplayer(Displayer displayer){
		mConfig.displayer = displayer;
		return this;
	}
	
	
	/**
	 * 配置磁盘缓存路径
	 * @param strPath
	 * @return
	 */
	public FinalBitmap configDiskCachePath(String strPath){
		if(!TextUtils.isEmpty(strPath)){
			mConfig.cachePath = strPath;
		}
		return this;
	}

	/**
	 * 配置内存缓存大小 大于2MB以上有效
	 * @param size 缓存大小
	 */
	public FinalBitmap configMemoryCacheSize(int size){
		mConfig.memCacheSize = size;
		return this;
	}
	
	/**
	 * 设置应缓存的在APK总内存的百分比，优先级大于configMemoryCacheSize
	 * @param percent 百分比，值的范围是在 0.05 到 0.8之间
	 */
	public FinalBitmap configMemoryCachePercent(float percent){
		mConfig.memCacheSizePercent = percent;
		return this;
	}
	
	/**
	 * 设置磁盘缓存大小 5MB 以上有效
	 * @param size
	 */
	public FinalBitmap configDiskCacheSize(int size){
		mConfig.diskCacheSize = size;
		return this;
	} 
	
	/**
	 * 设置加载图片的线程并发数量
	 * @param size
	 */
	public FinalBitmap configBitmapLoadThreadSize(int size){
		if(size >= 1)
			mConfig.poolSize = size;
		return this;
	}
	
	/**
	 * 配置是否立即回收图片资源
	 * @param recycleImmediately
	 * @return
	 */
	public FinalBitmap configRecycleImmediately(boolean recycleImmediately){
		mConfig.recycleImmediately = recycleImmediately;
		return this;
	} 
	
	/**
	 * 初始化finalBitmap
	 * @return
	 */
	private FinalBitmap init(){
		
		if( ! mInit ){
			
			BitmapCache.ImageCacheParams imageCacheParams = new BitmapCache.ImageCacheParams(mConfig.cachePath);
			if(mConfig.memCacheSizePercent>0.05 && mConfig.memCacheSizePercent<0.8){
				imageCacheParams.setMemCacheSizePercent(mContext, mConfig.memCacheSizePercent);
			}else{
				if(mConfig.memCacheSize > 1024 * 1024 * 2){
					imageCacheParams.setMemCacheSize(mConfig.memCacheSize);	
				}else{
					//设置默认的内存缓存大小
					imageCacheParams.setMemCacheSizePercent(mContext, 0.3f);
				}
			}
			if(mConfig.diskCacheSize > 1024 * 1024 * 5)
				imageCacheParams.setDiskCacheSize(mConfig.diskCacheSize);
			
			imageCacheParams.setRecycleImmediately(mConfig.recycleImmediately);
			//init Cache
			mImageCache = new BitmapCache(imageCacheParams);
			
			//init Executors
			bitmapLoadAndDisplayExecutor = Executors.newFixedThreadPool(mConfig.poolSize,new ThreadFactory() {
				@Override
	            public Thread newThread(Runnable r) {
					Thread t = new Thread(r);
					// 设置线程的优先级别，让线程先后顺序执行（级别越高，抢到cpu执行的时间越多）
					t.setPriority(Thread.NORM_PRIORITY - 1);
					return t;
				}
			});
			
			//init BitmapProcess
			mBitmapProcess = new BitmapProcess(mConfig.downloader,mImageCache);
			
			mInit = true ;
		}
		
		return this;
	}
	
	////////////////////////// config method end////////////////////////////////////
	
	public void display(View imageView,String uri){
		doDisplay(imageView,uri,null);
	}

	

	public void display(View imageView,String uri,int imageWidth,int imageHeight){
		BitmapDisplayConfig displayConfig = configMap.get(imageWidth+"_"+imageHeight);
		if(displayConfig==null){
			displayConfig = getDisplayConfig();
			displayConfig.setBitmapHeight(imageHeight);
			displayConfig.setBitmapWidth(imageWidth);
			configMap.put(imageWidth+"_"+imageHeight, displayConfig);
		}
		
		doDisplay(imageView,uri,displayConfig);
	}
	
	public void display(View imageView,String uri,Bitmap loadingBitmap){
		BitmapDisplayConfig displayConfig = configMap.get(String.valueOf(loadingBitmap));
		if(displayConfig==null){
			displayConfig = getDisplayConfig();
			displayConfig.setLoadingBitmap(loadingBitmap);
			configMap.put(String.valueOf(loadingBitmap), displayConfig);
		}
		
		doDisplay(imageView,uri,displayConfig);
	}
	
	
	public void display(View imageView,String uri,Bitmap loadingBitmap,Bitmap laodfailBitmap){
		BitmapDisplayConfig displayConfig = configMap.get(String.valueOf(loadingBitmap)+"_"+String.valueOf(laodfailBitmap));
		if(displayConfig==null){
			displayConfig = getDisplayConfig();
			displayConfig.setLoadingBitmap(loadingBitmap);
			displayConfig.setLoadfailBitmap(laodfailBitmap);
			configMap.put(String.valueOf(loadingBitmap)+"_"+String.valueOf(laodfailBitmap), displayConfig);
		}
		
		doDisplay(imageView,uri,displayConfig);
	}
	
	public void display(View imageView,String uri,int imageWidth,int imageHeight,Bitmap loadingBitmap,Bitmap laodfailBitmap){
		BitmapDisplayConfig displayConfig = configMap.get(imageWidth+"_"+imageHeight+"_"+String.valueOf(loadingBitmap)+"_"+String.valueOf(laodfailBitmap));
		if(displayConfig==null){
			displayConfig = getDisplayConfig();
			displayConfig.setBitmapHeight(imageHeight);
			displayConfig.setBitmapWidth(imageWidth);
			displayConfig.setLoadingBitmap(loadingBitmap);
			displayConfig.setLoadfailBitmap(laodfailBitmap);
			configMap.put(imageWidth+"_"+imageHeight+"_"+String.valueOf(loadingBitmap)+"_"+String.valueOf(laodfailBitmap), displayConfig);
		}
		
		doDisplay(imageView,uri,displayConfig);
	}
	
	
	public void display(View imageView,String uri,BitmapDisplayConfig config){
		doDisplay(imageView,uri,config);
	}
	
	
	private void doDisplay(View imageView, String uri, BitmapDisplayConfig displayConfig) {
		if(!mInit ){
			init();
		}
		
		if (TextUtils.isEmpty(uri) || imageView == null) {
			return;
		}
		
		if(displayConfig == null)
			displayConfig = mConfig.defaultDisplayConfig;
	
		Bitmap bitmap = null;
	
		if (mImageCache != null) {
			bitmap = mImageCache.getBitmapFromMemoryCache(uri);
		}
	
		if (bitmap != null) {
			if(imageView instanceof ImageView){
				((ImageView)imageView).setImageBitmap(bitmap);
			}else{
				imageView.setBackgroundDrawable(new BitmapDrawable(bitmap));
			}
			
			
		}else if (checkImageTask(uri, imageView)) {
			final BitmapLoadAndDisplayTask task = new BitmapLoadAndDisplayTask(imageView, displayConfig );
			//设置默认图片
			final AsyncDrawable asyncDrawable = new AsyncDrawable(mContext.getResources(), displayConfig.getLoadingBitmap(), task);
	       
			if(imageView instanceof ImageView){
				((ImageView)imageView).setImageDrawable(asyncDrawable);
			}else{
				imageView.setBackgroundDrawable(asyncDrawable);
			}
	        
	        task.executeOnExecutor(bitmapLoadAndDisplayExecutor, uri);
	    }
	}
	
	private HashMap<String, BitmapDisplayConfig> configMap = new HashMap<String, BitmapDisplayConfig>();
	
	
	private BitmapDisplayConfig getDisplayConfig(){
		BitmapDisplayConfig config = new BitmapDisplayConfig();
		config.setAnimation(mConfig.defaultDisplayConfig.getAnimation());
		config.setAnimationType(mConfig.defaultDisplayConfig.getAnimationType());
		config.setBitmapHeight(mConfig.defaultDisplayConfig.getBitmapHeight());
		config.setBitmapWidth(mConfig.defaultDisplayConfig.getBitmapWidth());
		config.setLoadfailBitmap(mConfig.defaultDisplayConfig.getLoadfailBitmap());
		config.setLoadingBitmap(mConfig.defaultDisplayConfig.getLoadingBitmap());
		return config;
	}
	

	private void clearCacheInternalInBackgroud() {
		if (mImageCache != null) {
			mImageCache.clearCache();
		}
	}
	
	
	
	private void clearDiskCacheInBackgroud(){
		if (mImageCache != null) {
			mImageCache.clearDiskCache();
		}
	}
	
	
	private void clearCacheInBackgroud(String key){
		if (mImageCache != null) {
			mImageCache.clearCache(key);
		}
	}
	
	private void clearDiskCacheInBackgroud(String key){
		if (mImageCache != null) {
			mImageCache.clearDiskCache(key);
		}
	}
	

    /**
     * 执行过此方法后,FinalBitmap的缓存已经失效,建议通过FinalBitmap.create()获取新的实例
     * @author fantouch
     */
	private void closeCacheInternalInBackgroud() {
		if (mImageCache != null) {
			mImageCache.close();
			mImageCache = null;
            mFinalBitmap = null;
		}
	}

	/**
	 * 网络加载bitmap
	 * @param data
	 * @return
	 */
	private Bitmap processBitmap(String uri,BitmapDisplayConfig config) {
		if (mBitmapProcess != null) {
			return mBitmapProcess.getBitmap(uri,config);
		}
		return null;
	}
	
	/**
	 * 从缓存（内存缓存和磁盘缓存）中直接获取bitmap，注意这里有io操作，最好不要放在ui线程执行
	 * @param key
	 * @return
	 */
	public Bitmap getBitmapFromCache(String key){
		Bitmap bitmap  = getBitmapFromMemoryCache(key);
		if(bitmap == null)
			bitmap = getBitmapFromDiskCache(key);
		
		return bitmap;
	}
	
	/**
	 * 从内存缓存中获取bitmap
	 * @param key
	 * @return
	 */
	public Bitmap getBitmapFromMemoryCache(String key){
		return mImageCache.getBitmapFromMemoryCache(key);
	}
	
	/**
	 * 从磁盘缓存中获取bitmap，，注意这里有io操作，最好不要放在ui线程执行
	 * @param key 
	 * @return
	 */
	public Bitmap getBitmapFromDiskCache(String key){
		return getBitmapFromDiskCache(key,null);
	}
	
	public Bitmap getBitmapFromDiskCache(String key,BitmapDisplayConfig config){
		return mBitmapProcess.getFromDisk(key, config);
	}
	
	public void setExitTasksEarly(boolean exitTasksEarly) {
		mExitTasksEarly = exitTasksEarly;
	}
	
	/**
     * activity onResume的时候调用这个方法，让加载图片线程继续
     */
    public void onResume(){
    	setExitTasksEarly(false);
    }
    
    /**
     * activity onPause的时候调用这个方法，让线程暂停
     */
    public void onPause() {
        setExitTasksEarly(true);
    }
    
    /**
     * activity onDestroy的时候调用这个方法，释放缓存
     * 执行过此方法后,FinalBitmap的缓存已经失效,建议通过FinalBitmap.create()获取新的实例
     * 
     * @author fantouch
     */
    public void onDestroy() {
        closeCache();
    }

	/**
	 * 清除所有缓存（磁盘和内存）
	 */
	public void clearCache() {
		new CacheExecutecTask().execute(CacheExecutecTask.MESSAGE_CLEAR);
	}
	
	/**
	 * 根据key清除指定的内存缓存
	 * @param key
	 */
	public void clearCache(String key) {
		new CacheExecutecTask().execute(CacheExecutecTask.MESSAGE_CLEAR_KEY,key);
	}
	
	/**
	 * 清除缓存
	 */
	public void clearMemoryCache() {
		if(mImageCache!=null)
			mImageCache.clearMemoryCache();
	}
	
	/**
	 * 根据key清除指定的内存缓存
	 * @param key
	 */
	public void clearMemoryCache(String key) {
		if(mImageCache!=null)
			mImageCache.clearMemoryCache(key);
	}
	
	
	/**
	 * 清除磁盘缓存
	 */
	public void clearDiskCache() {
		new CacheExecutecTask().execute(CacheExecutecTask.MESSAGE_CLEAR_DISK);
	}
	
	/**
	 * 根据key清除指定的内存缓存
	 * @param key
	 */
	public void clearDiskCache(String key) {
		new CacheExecutecTask().execute(CacheExecutecTask.MESSAGE_CLEAR_KEY_IN_DISK,key);
	}
	

    /**
     * 关闭缓存
     * 执行过此方法后,FinalBitmap的缓存已经失效,建议通过FinalBitmap.create()获取新的实例
     * @author fantouch
     */
	public void closeCache() {
		new CacheExecutecTask().execute(CacheExecutecTask.MESSAGE_CLOSE);
	}

	/**
	 * 退出正在加载的线程，程序退出的时候调用词方法
	 * @param exitTasksEarly
	 */
	public void exitTasksEarly(boolean exitTasksEarly) {
		mExitTasksEarly = exitTasksEarly;
		if(exitTasksEarly)
			pauseWork(false);//让暂停的线程结束
	}

	/**
	 * 暂停正在加载的线程，监听listview或者gridview正在滑动的时候条用词方法
	 * @param pauseWork true停止暂停线程，false继续线程
	 */
	public void pauseWork(boolean pauseWork) {
		synchronized (mPauseWorkLock) {
			mPauseWork = pauseWork;
			if (!mPauseWork) {
				mPauseWorkLock.notifyAll();
			}
		}
	}

	    
	private static BitmapLoadAndDisplayTask getBitmapTaskFromImageView(View imageView) {
		if (imageView != null) {
			Drawable drawable = null;
			if(imageView instanceof ImageView){
				drawable = ((ImageView)imageView).getDrawable();
			}else{
				drawable = imageView.getBackground();
			}
			
			if (drawable instanceof AsyncDrawable) {
				final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
				return asyncDrawable.getBitmapWorkerTask();
			}
		}
		return null;
	}


	    /**
	     * 检测 imageView中是否已经有线程在运行
	     * @param data
	     * @param imageView
	     * @return true 没有 false 有线程在运行了
	     */
	public static boolean checkImageTask(Object data, View imageView) {
			final BitmapLoadAndDisplayTask bitmapWorkerTask = getBitmapTaskFromImageView(imageView);

			if (bitmapWorkerTask != null) {
				final Object bitmapData = bitmapWorkerTask.data;
				if (bitmapData == null || !bitmapData.equals(data)) {
					bitmapWorkerTask.cancel(true);
				} else {
					// 同一个线程已经在执行
					return false;
				}
			}
			return true;
		}
		
		
		private static class AsyncDrawable extends BitmapDrawable {
			private final WeakReference<BitmapLoadAndDisplayTask> bitmapWorkerTaskReference;

			public AsyncDrawable(Resources res, Bitmap bitmap,BitmapLoadAndDisplayTask bitmapWorkerTask) {
				super(res, bitmap);
				bitmapWorkerTaskReference = new WeakReference<BitmapLoadAndDisplayTask>(
						bitmapWorkerTask);
			}

			public BitmapLoadAndDisplayTask getBitmapWorkerTask() {
				return bitmapWorkerTaskReference.get();
			}
		}


	private class CacheExecutecTask extends AsyncTask<Object, Void, Void> {
		public static final int MESSAGE_CLEAR = 1;
		public static final int MESSAGE_CLOSE = 2;
		public static final int MESSAGE_CLEAR_DISK = 3;
		public static final int MESSAGE_CLEAR_KEY = 4;
		public static final int MESSAGE_CLEAR_KEY_IN_DISK = 5;
		@Override
		protected Void doInBackground(Object... params) {
			switch ((Integer) params[0]) {
			case MESSAGE_CLEAR:
				clearCacheInternalInBackgroud();
				break;
			case MESSAGE_CLOSE:
				closeCacheInternalInBackgroud();
				break;
			case MESSAGE_CLEAR_DISK:
				clearDiskCacheInBackgroud();
				break;
			case MESSAGE_CLEAR_KEY:
				clearCacheInBackgroud(String.valueOf(params[1]));
				break;
			case MESSAGE_CLEAR_KEY_IN_DISK:
				clearDiskCacheInBackgroud(String.valueOf(params[1]));
				break;
			}
			return null;
		}
	}
	
	/**
	 * bitmap下载显示的线程
	 * @author michael yang
	 */
	private class BitmapLoadAndDisplayTask extends AsyncTask<Object, Void, Bitmap> {
		private Object data;
		private final WeakReference<View> imageViewReference;
		private final BitmapDisplayConfig displayConfig;

		public BitmapLoadAndDisplayTask(View imageView,BitmapDisplayConfig config) {
			imageViewReference = new WeakReference<View>(imageView);
			displayConfig = config;
		}

		@Override
		protected Bitmap doInBackground(Object... params) {
			data = params[0];
			final String dataString = String.valueOf(data);
			Bitmap bitmap = null;

			synchronized (mPauseWorkLock) {
				while (mPauseWork && !isCancelled()) {
					try {
						mPauseWorkLock.wait();
					} catch (InterruptedException e) {
					}
				}
			}

			if (bitmap == null && !isCancelled()&& getAttachedImageView() != null && !mExitTasksEarly) {
				bitmap = processBitmap(dataString,displayConfig);
			}
			
			if(bitmap!=null){
				mImageCache.addToMemoryCache(dataString, bitmap);
			}

			return bitmap;
		}

		@Override
		protected void onPostExecute(Bitmap bitmap) {
			if (isCancelled() || mExitTasksEarly) {
				bitmap = null;
			}

			// 判断线程和当前的imageview是否是匹配
			final View imageView = getAttachedImageView();
			if (bitmap != null && imageView != null) {
				mConfig.displayer.loadCompletedisplay(imageView,bitmap,displayConfig);			
			}else if(bitmap == null && imageView!=null ){
				mConfig.displayer.loadFailDisplay(imageView, displayConfig.getLoadfailBitmap());
			}
		}

		@Override
		protected void onCancelled(Bitmap bitmap) {
			super.onCancelled(bitmap);
			synchronized (mPauseWorkLock) {
				mPauseWorkLock.notifyAll();
			}
		}

		/**
		 * 获取线程匹配的imageView,防止出现闪动的现象
		 * @return
		 */
		private View getAttachedImageView() {
			final View imageView = imageViewReference.get();
			final BitmapLoadAndDisplayTask bitmapWorkerTask = getBitmapTaskFromImageView(imageView);

			if (this == bitmapWorkerTask) {
				return imageView;
			}

			return null;
		}
	}
	
	
	/**
	 * @title 配置信息
	 * @description FinalBitmap的配置信息
	 * @company 探索者网络工作室(www.tsz.net)
	 * @author michael Young (www.YangFuhai.com)
	 * @version 1.0
	 * @created 2012-10-28
	 */
	private class FinalBitmapConfig {
		public String cachePath;
		 public Displayer displayer;
		 public Downloader downloader;
		 public BitmapDisplayConfig defaultDisplayConfig;
		 public float memCacheSizePercent;//缓存百分比，android系统分配给每个apk内存的大小
		 public int memCacheSize;//内存缓存百分比
		 public int diskCacheSize;//磁盘百分比
		 public int poolSize = 3;//默认的线程池线程并发数量
		 public boolean recycleImmediately = true;//是否立即回收内存
		
		 public FinalBitmapConfig(Context context) {
				defaultDisplayConfig = new BitmapDisplayConfig();
				
				defaultDisplayConfig.setAnimation(null);
				defaultDisplayConfig.setAnimationType(BitmapDisplayConfig.AnimationType.fadeIn);
				
				//设置图片的显示最大尺寸（为屏幕的大小,默认为屏幕宽度的1/2）
				DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
				int defaultWidth = (int)Math.floor(displayMetrics.widthPixels/2);
				defaultDisplayConfig.setBitmapHeight(defaultWidth);
				defaultDisplayConfig.setBitmapWidth(defaultWidth);
				
		}
	}
	
}
