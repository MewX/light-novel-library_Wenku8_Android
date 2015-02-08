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
package net.tsz.afinal.db.table;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Date;

import net.tsz.afinal.utils.FieldUtils;

/**
 * @title 属性
 * @description 【非主键】的【基本数据类型】 都是属性
 * @author michael Young (www.YangFuhai.com)
 * @version 1.0
 * @created 2012-10-10
 */
public class Property {
	
	private String fieldName;
	private String column;
	private String defaultValue;
	private Class<?> dataType;
	private Field field;
	
	private Method get;
	private Method set;
	
	public void setValue(Object receiver , Object value){
		if(set!=null && value!=null){
			try {
				if (dataType == String.class) {
					set.invoke(receiver, value.toString());
				} else if (dataType == int.class || dataType == Integer.class) {
					set.invoke(receiver, value == null ? (Integer) null : Integer.parseInt(value.toString()));
				} else if (dataType == float.class || dataType == Float.class) {
					set.invoke(receiver, value == null ? (Float) null: Float.parseFloat(value.toString()));
				} else if (dataType == double.class || dataType == Double.class) {
					set.invoke(receiver, value == null ? (Double) null: Double.parseDouble(value.toString()));
				} else if (dataType == long.class || dataType == Long.class) {
					set.invoke(receiver, value == null ? (Long) null: Long.parseLong(value.toString()));
				} else if (dataType == Date.class || dataType == java.sql.Date.class) {
					set.invoke(receiver, value == null ? (Date) null: FieldUtils.stringToDateTime(value.toString()));
				} else if (dataType == boolean.class || dataType == Boolean.class) {
					set.invoke(receiver, value == null ? (Boolean) null: "1".equals(value.toString()));
				} else {
					set.invoke(receiver, value);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}else{
			try {
				field.setAccessible(true);
				field.set(receiver, value);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * 获取某个实体执行某个方法的结果
	 * @param obj
	 * @param method
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public <T> T getValue(Object obj){
		if(obj != null && get != null) {
			try {
				return (T)get.invoke(obj);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return null;
	}
	
	
	public String getFieldName() {
		return fieldName;
	}
	public void setFieldName(String fieldName) {
		this.fieldName = fieldName;
	}
	public String getColumn() {
		return column;
	}
	public void setColumn(String column) {
		this.column = column;
	}
	public String getDefaultValue() {
		return defaultValue;
	}
	public void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}
	public Class<?> getDataType() {
		return dataType;
	}
	public void setDataType(Class<?> dataType) {
		this.dataType = dataType;
	}
	public Method getGet() {
		return get;
	}
	public void setGet(Method get) {
		this.get = get;
	}
	public Method getSet() {
		return set;
	}
	public void setSet(Method set) {
		this.set = set;
	}

	public Field getField() {
		return field;
	}

	public void setField(Field field) {
		this.field = field;
	}
	
	
}
