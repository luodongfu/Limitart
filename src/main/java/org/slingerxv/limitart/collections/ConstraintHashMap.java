/*
 * Copyright (c) 2016-present The Limitart Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.slingerxv.limitart.collections;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slingerxv.limitart.funcs.Test2;
import org.slingerxv.limitart.util.StringUtil;

/**
 * 约束型Map
 * 
 * @author hank
 *
 */
public class ConstraintHashMap<K> implements ConstraintMap<K> {
	private Map<K, Object> map;

	protected ConstraintHashMap(Map<K, Object> map) {
		this.map = Objects.requireNonNull(map, "map");
	}

	protected ConstraintHashMap() {
		map = new HashMap<>();
	}

	/**
	 * 清除
	 */
	@Override
	public void clear() {
		map.clear();
	}

	/**
	 * 大小
	 * 
	 * @return
	 */
	@Override
	public int size() {
		return map.size();
	}

	@Override
	public ConstraintMap<K> putByte(K key, byte value) {
		putObj(key, value);
		return this;
	}

	@Override
	public byte getByte(K key) {
		if (!containsKey(key)) {
			return 0;
		}
		return getObj(key);
	}

	@Override
	public ConstraintMap<K> putShort(K key, short value) {
		putObj(key, value);
		return this;
	}

	@Override
	public short getShort(K key) {
		if (!containsKey(key)) {
			return 0;
		}
		return getObj(key);
	}

	@Override
	public ConstraintMap<K> putInt(K key, int value) {
		putObj(key, value);
		return this;
	}

	@Override
	public int getInt(K key) {
		if (!containsKey(key)) {
			return 0;
		}
		return getObj(key);
	}

	@Override
	public ConstraintMap<K> putLong(K key, long value) {
		putObj(key, value);
		return this;
	}

	@Override
	public long getLong(K key) {
		if (!containsKey(key)) {
			return 0L;
		}
		return getObj(key);
	}

	@Override
	public ConstraintMap<K> putFloat(K key, float value) {
		putObj(key, value);
		return this;
	}

	@Override
	public float getFloat(K key) {
		if (!containsKey(key)) {
			return 0F;
		}
		return getObj(key);
	}

	@Override
	public ConstraintMap<K> putDouble(K key, double value) {
		putObj(key, value);
		return this;
	}

	@Override
	public double getDouble(K key) {
		if (!containsKey(key)) {
			return 0D;
		}
		return getObj(key);
	}

	@Override
	public ConstraintMap<K> putChar(K key, char value) {
		putObj(key, value);
		return this;
	}

	@Override
	public char getChar(K key) {
		if (!containsKey(key)) {
			return 0;
		}
		return getObj(key);
	}

	@Override
	public ConstraintMap<K> putBoolean(K key, boolean value) {
		putInt(key, value ? 1 : 0);
		return this;
	}

	@Override
	public boolean getBoolean(K key) {
		return getInt(key) == 1;
	}

	@Override
	public ConstraintMap<K> putString(K key, String value) {
		if (value == null) {
			putObj(key, "");
		} else {
			putObj(key, value);
		}
		return this;
	}

	@Override
	public String getString(K key) {
		if (!containsKey(key)) {
			return "";
		}
		return getObj(key);
	}

	/**
	 * 是否为空
	 * 
	 * @return
	 */
	@Override
	public boolean isEmpty() {
		return map.isEmpty();
	}

	/**
	 * 是否包含Key
	 * 
	 * @param key
	 * @return
	 */
	@Override
	public boolean containsKey(Object key) {
		return map.containsKey(Objects.requireNonNull(key, "key"));
	}

	/**
	 * 是否包含值
	 * 
	 * @param value
	 * @return
	 */
	@Override
	public boolean containsValue(Object value) {
		return map.containsValue(Objects.requireNonNull(value, "value"));
	}

	@Override
	public ConstraintMap<K> putObj(K key, Object value) {
		map.put(key, value);
		return this;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <V> V getObj(K key) {
		return (V) map.get(key);
	}

	@Override
	public ConstraintMap<K> remove(Test2<K, Object> filter) {
		Iterator<Entry<K, Object>> iterator = map.entrySet().iterator();
		for (; iterator.hasNext();) {
			Entry<K, Object> next = iterator.next();
			if (filter.test(next.getKey(), next.getValue())) {
				iterator.remove();
			}
		}
		return this;
	}

	@Override
	public Object get(Object key) {
		return map.get(key);
	}

	@Override
	public Object put(K key, Object value) {
		return map.put(key, value);
	}

	@Override
	public Object remove(Object value) {
		return map.remove(value);
	}

	@Override
	public void putAll(Map<? extends K, ? extends Object> m) {
		this.map.putAll(m);
	}

	@Override
	public Set<K> keySet() {
		return map.keySet();
	}

	@Override
	public Set<Entry<K, Object>> entrySet() {
		return map.entrySet();
	}

	@Override
	public Collection<Object> values() {
		return map.values();
	}

	/**
	 * 返回常规Json
	 * 
	 * @return
	 */
	public String toJSON() {
		return StringUtil.toJSON(this.map);
	}

	/**
	 * 返回带类信息的Json
	 * 
	 * @return
	 */
	public String toJSONWithClassInfo() {
		return StringUtil.toJSONWithClassInfo(this.map);
	}

	/**
	 * 构造一个空的对象
	 * 
	 * @return
	 */
	public static <K> ConstraintHashMap<K> empty() {
		return new ConstraintHashMap<K>();
	}

	/**
	 * 从一个Json中构造
	 * 
	 * @param jsonContent
	 * @return
	 */
	public static <K> ConstraintHashMap<K> fromJSON(String jsonContent) {
		ConstraintHashMap<K> map = ConstraintHashMap.empty();
		if (!StringUtil.isEmptyOrNull(jsonContent)) {
			@SuppressWarnings("unchecked")
			Map<K, Object> object = StringUtil.toObject(jsonContent, HashMap.class);
			map.putAll(object);
		}
		return map;
	}

	/**
	 * 通过键值对数组构造
	 * 
	 * @param kvs
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <K> ConstraintHashMap<K> just(Object... kvs) {
		Objects.requireNonNull(kvs, "kvs");
		ConstraintHashMap<K> empty = ConstraintHashMap.empty();
		for (int i = 0; i < kvs.length; i += 2) {
			Objects.requireNonNull(kvs[i], "key");
			Objects.requireNonNull(kvs[i + 1], "value");
			empty.putObj((K) kvs[i], kvs[i + 1]);
		}
		return empty;
	}

	/**
	 * 从一个Map构造出此对象
	 * 
	 * @param map
	 * @return
	 */
	public static <K> ConstraintHashMap<K> from(Map<? extends K, ? extends Object> map) {
		ConstraintHashMap<K> empty = ConstraintHashMap.empty();
		empty.putAll(map);
		return empty;
	}
}