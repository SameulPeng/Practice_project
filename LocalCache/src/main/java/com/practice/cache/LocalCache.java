package com.practice.cache;

import com.practice.policy.CacheWrapper;
import com.practice.policy.EvictionPolicy;

import java.util.Iterator;
import java.util.Map;

/**
 * 本地缓存抽象类
 * @param <T> 缓存key的类型
 */
public abstract class LocalCache<T> {
    protected final Map<String, CacheWrapper<T>> map; // Map结构，存储缓存数据
    protected final EvictionPolicy<T> policy; // 缓存淘汰策略控制器

    public LocalCache(Map<String, CacheWrapper<T>> map, EvictionPolicy<T> policy) {
        this.map = map;
        this.policy = policy;
    }

    /**
     * 查询缓存
     * @param key 缓存key
     * @return 缓存value
     */
    public abstract T get(String key);

    /**
     * 新增缓存
     * @param key 缓存key
     * @param value 缓存value
     */
    public abstract void put(String key, T value);

    /**
     * 移除缓存
     * @param key 缓存key
     */
    public abstract void remove(String key);

    /**
     * 获取迭代器
     * @return 迭代器
     */
    public Iterator<CacheWrapper<T>> iterator() {
        return null;
    }
}
