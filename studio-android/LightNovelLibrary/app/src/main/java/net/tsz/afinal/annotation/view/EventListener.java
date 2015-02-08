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
package net.tsz.afinal.annotation.view;

import java.lang.reflect.Method;

import net.tsz.afinal.exception.ViewException;

import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.AdapterView.OnItemSelectedListener;

public class EventListener implements OnClickListener, OnLongClickListener, OnItemClickListener, OnItemSelectedListener,OnItemLongClickListener {

	private Object handler;
	
	private String clickMethod;
	private String longClickMethod;
	private String itemClickMethod;
	private String itemSelectMethod;
	private String nothingSelectedMethod;
	private String itemLongClickMehtod;
	
	public EventListener(Object handler) {
		this.handler = handler;
	}
	
	public EventListener click(String method){
		this.clickMethod = method;
		return this;
	}
	
	public EventListener longClick(String method){
		this.longClickMethod = method;
		return this;
	}
	
	public EventListener itemLongClick(String method){
		this.itemLongClickMehtod = method;
		return this;
	}
	
	public EventListener itemClick(String method){
		this.itemClickMethod = method;
		return this;
	}
	
	public EventListener select(String method){
		this.itemSelectMethod = method;
		return this;
	}
	
	public EventListener noSelect(String method){
		this.nothingSelectedMethod = method;
		return this;
	}
	
	public boolean onLongClick(View v) {
		return invokeLongClickMethod(handler,longClickMethod,v);
	}
	
	public boolean onItemLongClick(AdapterView<?> arg0, View arg1, int arg2,long arg3) {
		return invokeItemLongClickMethod(handler,itemLongClickMehtod,arg0,arg1,arg2,arg3);
	}
	
	public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2,long arg3) {
		
		invokeItemSelectMethod(handler,itemSelectMethod,arg0,arg1,arg2,arg3);
	}
	
	public void onNothingSelected(AdapterView<?> arg0) {
		invokeNoSelectMethod(handler,nothingSelectedMethod,arg0);
	}
	
	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		
		invokeItemClickMethod(handler,itemClickMethod,arg0,arg1,arg2,arg3);
	}
	
	public void onClick(View v) {
		
		invokeClickMethod(handler, clickMethod, v);
	}
	
	
	private static Object invokeClickMethod(Object handler, String methodName,  Object... params){
		if(handler == null) return null;
		Method method = null;
		try{   
			method = handler.getClass().getDeclaredMethod(methodName,View.class);
			if(method!=null)
				return method.invoke(handler, params);	
			else
				throw new ViewException("no such method:"+methodName);
		}catch(Exception e){
			e.printStackTrace();
		}
		
		return null;
		
	}
	
	
	private static boolean invokeLongClickMethod(Object handler, String methodName,  Object... params){
		if(handler == null) return false;
		Method method = null;
		try{   
			//public boolean onLongClick(View v)
			method = handler.getClass().getDeclaredMethod(methodName,View.class);
			if(method!=null){
				Object obj = method.invoke(handler, params);
				return obj==null?false:Boolean.valueOf(obj.toString());	
			}
			else
				throw new ViewException("no such method:"+methodName);
		}catch(Exception e){
			e.printStackTrace();
		}
		
		return false;
		
	}
	
	
	
	private static Object invokeItemClickMethod(Object handler, String methodName,  Object... params){
		if(handler == null) return null;
		Method method = null;
		try{   
			///onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3)
			method = handler.getClass().getDeclaredMethod(methodName,AdapterView.class,View.class,int.class,long.class);
			if(method!=null)
				return method.invoke(handler, params);	
			else
				throw new ViewException("no such method:"+methodName);
		}catch(Exception e){
			e.printStackTrace();
		}
		
		return null;
	}
	
	
	private static boolean invokeItemLongClickMethod(Object handler, String methodName,  Object... params){
		if(handler == null) throw new ViewException("invokeItemLongClickMethod: handler is null :");
		Method method = null;
		try{   
			///onItemLongClick(AdapterView<?> arg0, View arg1, int arg2,long arg3)
			method = handler.getClass().getDeclaredMethod(methodName,AdapterView.class,View.class,int.class,long.class);
			if(method!=null){
				Object obj = method.invoke(handler, params);
				return Boolean.valueOf(obj==null?false:Boolean.valueOf(obj.toString()));	
			}
			else
				throw new ViewException("no such method:"+methodName);
		}catch(Exception e){
			e.printStackTrace();
		}
		
		return false;
	}
	
	
	private static Object invokeItemSelectMethod(Object handler, String methodName,  Object... params){
		if(handler == null) return null;
		Method method = null;
		try{   
			///onItemSelected(AdapterView<?> arg0, View arg1, int arg2,long arg3)
			method = handler.getClass().getDeclaredMethod(methodName,AdapterView.class,View.class,int.class,long.class);
			if(method!=null)
				return method.invoke(handler, params);	
			else
				throw new ViewException("no such method:"+methodName);
		}catch(Exception e){
			e.printStackTrace();
		}
		
		return null;
	}
	
	private static Object invokeNoSelectMethod(Object handler, String methodName,  Object... params){
		if(handler == null) return null;
		Method method = null;
		try{   
			//onNothingSelected(AdapterView<?> arg0)
			method = handler.getClass().getDeclaredMethod(methodName,AdapterView.class);
			if(method!=null)
				return method.invoke(handler, params);	
			else
				throw new ViewException("no such method:"+methodName);
		}catch(Exception e){
			e.printStackTrace();
		}
		
		return null;
	}

	
	
}
