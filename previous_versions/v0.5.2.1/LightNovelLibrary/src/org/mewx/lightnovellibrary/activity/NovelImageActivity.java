package org.mewx.lightnovellibrary.activity;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import me.imid.swipebacklayout.lib.SwipeBackLayout;
import me.imid.swipebacklayout.lib.app.SwipeBackActivity;

import org.mewx.lightnovellibrary.R;
import org.mewx.lightnovellibrary.util.LightCache;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import com.umeng.analytics.MobclickAgent;

import uk.co.senab.photoview.PhotoView;

public class NovelImageActivity extends SwipeBackActivity {

	String imgPath;
	byte[] imgContent = null;
	Bitmap bm = null;

	// slide back
	private SwipeBackLayout mSwipeBackLayout;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.activity_image);

		mSwipeBackLayout = getSwipeBackLayout();
		mSwipeBackLayout.setScrimColor(Color.TRANSPARENT);
		mSwipeBackLayout.setEdgeTrackingEnabled(SwipeBackLayout.EDGE_LEFT);

		imgPath = getIntent().getStringExtra("path");
		if (imgPath == null || imgPath.length() == 0) {
			Toast.makeText(this, "No image path error", Toast.LENGTH_LONG)
					.show();
			return;
		}

		if (imgPath == null || imgPath.length() == 0)
			return;

		// load image file first
		imgContent = LightCache.loadFile(imgPath);
		bm = BitmapFactory.decodeByteArray(imgContent, 0,
				imgContent.length);

		// Native memory try
		// BitmapFactory.Options options = new BitmapFactory.Options();
		// options.inPreferredConfig = Config.ARGB_8888;
		// options.inPurgeable = true;//允许可清除
		// options.inInputShareable = true;// 以上options的两个属性必须联合使用才会有效果
		// InputStream is = new ByteArrayInputStream(imgContent);
		// Bitmap bitmap = BitmapFactory.decodeStream(is,null,options);
		// ((PhotoView)
		// this.findViewById(R.id.image_photoview)).setImageBitmap(bitmap);

		// show in View
		SubsamplingScaleImageView iv = (SubsamplingScaleImageView) this.findViewById(R.id.image_photoview);
		iv.setImageFile(imgPath);
		return;
	}

	@Override
	protected void onDestroy() {
		// save memory
		bm.recycle();
		imgContent = null;

		super.onDestroy();
		return;
	}

	@Override
	public void onBackPressed() {
		scrollToFinishActivity();
	}


	public void onResume() {
		super.onResume();
		MobclickAgent.onResume(this);
	}

	public void onPause() {
		super.onPause();
		MobclickAgent.onPause(this);
	}
}
