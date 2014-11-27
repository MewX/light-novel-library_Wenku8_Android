package org.mewx.lightnovellibrary.component;

import java.io.File;

import android.os.Environment;
import cn.wenku8.api.Wenku8Interface;

public class GlobalConfig {
	GlobalConfig() {
		return;
	}

	public static String getFirstStoragePath() {
		return Environment.getExternalStorageDirectory() + File.separator
				+ "wenku8" + File.separator;
	}

	public static String getSecondStoragePath() {
		return MyApp.getContext().getFilesDir() + File.separator;
	}

	public static Wenku8Interface.LANG getFetchLanguage() {
		return Wenku8Interface.LANG.SC;
	}
}
