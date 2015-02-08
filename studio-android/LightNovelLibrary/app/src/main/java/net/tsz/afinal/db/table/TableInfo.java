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
import java.util.HashMap;
import java.util.List;
import net.tsz.afinal.exception.DbException;
import net.tsz.afinal.utils.ClassUtils;
import net.tsz.afinal.utils.FieldUtils;


public class TableInfo {

	private String className;
	private String tableName;
	
	private Id id;
	
	public final HashMap<String, Property> propertyMap = new HashMap<String, Property>();
	public final HashMap<String, OneToMany> oneToManyMap = new HashMap<String, OneToMany>();
	public final HashMap<String, ManyToOne> manyToOneMap = new HashMap<String, ManyToOne>();
	
	private boolean checkDatabese;//在对实体进行数据库操作的时候查询是否已经有表了，只需查询一遍，用此标示
	
	
	private static final HashMap<String, TableInfo> tableInfoMap = new HashMap<String, TableInfo>();
	
	private TableInfo(){}
	
	@SuppressWarnings("unused")
	public static  TableInfo get(Class<?> clazz){
		if(clazz == null) 
			throw new DbException("table info get error,because the clazz is null");
		
		TableInfo tableInfo = tableInfoMap.get(clazz.getName());
		if( tableInfo == null ){
			tableInfo = new TableInfo();
			
			tableInfo.setTableName(ClassUtils.getTableName(clazz));
			tableInfo.setClassName(clazz.getName());
			
			Field idField = ClassUtils.getPrimaryKeyField(clazz);
			if(idField != null){
				Id id = new Id();
				id.setColumn(FieldUtils.getColumnByField(idField));
				id.setFieldName(idField.getName());
				id.setSet(FieldUtils.getFieldSetMethod(clazz, idField));
				id.setGet(FieldUtils.getFieldGetMethod(clazz, idField));
				id.setDataType(idField.getType());
				
				tableInfo.setId(id);
			}else{
				throw new DbException("the class["+clazz+"]'s idField is null , \n you can define _id,id property or use annotation @id to solution this exception");
			}
			
			List<Property> pList = ClassUtils.getPropertyList(clazz);
			if(pList!=null){
				for(Property p : pList){
					if(p!=null)
						tableInfo.propertyMap.put(p.getColumn(), p);
				}
			}
			
			List<ManyToOne> mList = ClassUtils.getManyToOneList(clazz);
			if(mList!=null){
				for(ManyToOne m : mList){
					if(m!=null)
						tableInfo.manyToOneMap.put(m.getColumn(), m);
				}
			}
			
			List<OneToMany> oList = ClassUtils.getOneToManyList(clazz);
			if(oList!=null){
				for(OneToMany o : oList){
					if(o!=null)
						tableInfo.oneToManyMap.put(o.getColumn(), o);
				}
			}
			
			
			tableInfoMap.put(clazz.getName(), tableInfo);
		}
		
		if(tableInfo == null ) 
			throw new DbException("the class["+clazz+"]'s table is null");
		
		return tableInfo;
	}
	
	
	public static TableInfo get(String className){
		try {
			return get(Class.forName(className));
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}
	

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public String getTableName() {
		return tableName;
	}

	public void setTableName(String tableName) {
		this.tableName = tableName;
	}

	public Id getId() {
		return id;
	}

	public void setId(Id id) {
		this.id = id;
	}

	public boolean isCheckDatabese() {
		return checkDatabese;
	}

	public void setCheckDatabese(boolean checkDatabese) {
		this.checkDatabese = checkDatabese;
	}

	
	
}
