/**
 *  Start Activity
 **
 *  This is the first activity of this application.
 *  Show LOGO, initial something here.
 *  After this, jump to MainActivity.
 **/

package org.mewx.lightnovellibrary.activity;

import org.mewx.lightnovellibrary.R;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.WindowManager;

public class StartActivity extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.activity_startpage);

		/* This is a delay template */
		new CountDownTimer(2000, 100) {
			@Override
			public void onTick(long millisUntilFinished) {
				// Animation can be here.
			}

			@Override
			public void onFinish() {
				// time-up, and jump
				Intent intent = new Intent();
				intent.setClass(StartActivity.this, MainActivity.class);
				startActivity(intent);
				finish();
				// destroy itself after jump
			}
		}.start();
	}

}
