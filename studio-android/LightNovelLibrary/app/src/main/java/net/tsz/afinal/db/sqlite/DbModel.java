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
package net.tsz.afinal.db.sqlite;

import java.util.HashMap;

public class DbModel {

	private HashMap<String, Object> dataMap = new HashMap<String, Object>();
	
	public Object get(String column){
		return dataMap.get(column);
	}
	
	public String getString(String column){
		return String.valueOf(get(column));
	}
	
	public int getInt(String column){
		return Integer.valueOf(getString(column));
	}
	
	public boolean getBoolean(String column){
		return Boolean.valueOf(getString(column));
	}
	
	public double getDouble(String column){
		return Double.valueOf(getString(column));
	}
	
	public float getFloat(String column){
		return Float.valueOf(getString(column));
	}
	
	public long getLong(String column){
		return Long.valueOf(getString(column));
	}
	
	public void set(String key,Object value){
		dataMap.put(key, value);
	}
	
	public HashMap<String, Object> getDataMap(){
		return dataMap;
	}
}
