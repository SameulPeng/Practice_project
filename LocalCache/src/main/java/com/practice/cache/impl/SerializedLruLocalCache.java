package com.practice.cache.impl;

import com.practice.cache.LocalCache;
import com.practice.policy.CacheWrapper;
import com.practice.policy.EvictionPolicy;
import com.practice.policy.impl.SerializedLruPolicy;

import java.util.HashMap;
import java.util.Map;

/**
 * 串行化LRU本地缓存
 * @param <T> 缓存key的类型
 */
public class SerializedLruLocalCache<T> extends LocalCache<T> {
    public SerializedLruLocalCache(int capacity) {
        this(
                // 将加载因子设置为64，避免触发扩容
                new HashMap<>(Math.max(capacity, 4), 64.0f),
                new SerializedLruPolicy<>(capacity)
        );
    }

    private SerializedLruLocalCache(Map<String, CacheWrapper<T>> map, EvictionPolicy<T> policy) {
        super(map, policy);
    }

    /**
     * 查询缓存
     * @param key 缓存key
     * @return 缓存value
     */
    public T get(String key) {
        CacheWrapper<T> node = map.get(key);
        // 刷新缓存项并获取缓存项中的数据后返回
        return policy.get(node);
    }

    /**
     * 新增缓存
     * @param key 缓存key
     * @param value 缓存value
     */
    public void put(String key, T value) {
        CacheWrapper<T> node = policy.put(map, key, value);
        map.put(key, node);
    }

    /**
     * 移除缓存
     * @param key 缓存key
     */
    public void remove(String key) {
        CacheWrapper<T> node = map.remove(key);
        policy.remove(node);
    }
}
