package org.mewx.lightnovellibrary.component;

import android.app.Application;
import android.content.Context;

public class MyApp extends Application {
	private static Context context;
	
	@Override
	public void onCreate( ) {
		context = getApplicationContext( );
		return;
	}
	
	public static Context getContext( ) {
		return context;
	}
}
