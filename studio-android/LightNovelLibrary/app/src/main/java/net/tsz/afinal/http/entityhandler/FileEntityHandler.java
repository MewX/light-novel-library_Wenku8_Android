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
package net.tsz.afinal.http.entityhandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.http.HttpEntity;

import android.text.TextUtils;

public class FileEntityHandler {
	
	private boolean mStop = false;
	
	

	public boolean isStop() {
		return mStop;
	}



	public void setStop(boolean stop) {
		this.mStop = stop;
	}


	public Object handleEntity(HttpEntity entity, EntityCallBack callback,String target,boolean isResume) throws IOException {
		if (TextUtils.isEmpty(target) || target.trim().length() == 0)
			return null;

		File targetFile = new File(target);

		if (!targetFile.exists()) {
			targetFile.createNewFile();
		}

		if(mStop){
			return targetFile;
		}
			
		
		long current = 0;
		FileOutputStream os = null;
		if(isResume){
			current = targetFile.length();
			os = new FileOutputStream(target, true);
		}else{
			os = new FileOutputStream(target);
		}
		
		if(mStop){
			return targetFile;
		}
			
		InputStream input = entity.getContent();
		long count = entity.getContentLength() + current;
		
		if(current >= count || mStop){
			return targetFile;
		}
		
		int readLen = 0;
		byte[] buffer = new byte[1024];
		while (!mStop && !(current >= count) && ((readLen = input.read(buffer,0,1024)) > 0) ) {//未全部读取
			os.write(buffer, 0, readLen);
			current += readLen;
			callback.callBack(count, current,false);
		}
		callback.callBack(count, current,true);
		
		if(mStop && current < count){ //用户主动停止
			throw new IOException("user stop download thread");
		}
		
		return targetFile;
	}

}
