package org.mewx.lightnovellibrary.activity;

import me.imid.swipebacklayout.lib.SwipeBackLayout;
import me.imid.swipebacklayout.lib.app.SwipeBackActivity;

import org.mewx.lightnovellibrary.R;
import org.mewx.lightnovellibrary.util.LightCache;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import uk.co.senab.photoview.PhotoView;

public class NovelImageActivity extends SwipeBackActivity {

	String imgPath;

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
			Toast.makeText(this, "No image path error", Toast.LENGTH_LONG).show();
			return;
		}

		if (imgPath == null || imgPath.length() == 0)
			return;
		
		// load image file first
		byte[] imgContent = LightCache.loadFile(imgPath);
		Bitmap bm = BitmapFactory.decodeByteArray(imgContent, 0, imgContent.length);
		//BitmapDrawable bd= new BitmapDrawable(getResources(), bm);
		
		// show in View
		PhotoView iv = (PhotoView) this.findViewById(R.id.image_photoview);
		iv.setImageBitmap(bm);
		//ImageLoader.getInstance().displayImage("file://" + imgPath, iv);
		return;
	}

	@Override
	protected void onResume() {
		super.onResume();
		return;
	}

	@Override
	public void onBackPressed() {
		scrollToFinishActivity();
	}

}
