package com.practice.policy;

import java.util.Iterator;
import java.util.Map;

/**
 * 缓存淘汰策略控制器接口
 * @param <T> 缓存key的类型
 */
public interface EvictionPolicy<T> {
    /**
     * 查询缓存
     * @param wrapper 缓存项
     */
    T get(CacheWrapper<T> wrapper);

    /**
     * 新增缓存
     * @param map 缓存存储的Map结构
     * @param key 缓存key
     * @param value 缓存value
     * @return 缓存项
     */
    CacheWrapper<T> put(Map<String, CacheWrapper<T>> map, String key, T value);

    /**
     * 移除缓存
     * @param wrapper 缓存项
     */
    void remove(CacheWrapper<T> wrapper);

    /**
     * 获取迭代器
     * @return 迭代器
     */
    default Iterator<CacheWrapper<T>> iterator() {
        return null;
    }
}
