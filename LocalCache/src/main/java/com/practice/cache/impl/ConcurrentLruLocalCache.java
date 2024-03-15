package com.practice.cache.impl;

import com.practice.cache.LocalCache;
import com.practice.policy.CacheWrapper;
import com.practice.policy.EvictionPolicy;
import com.practice.policy.impl.ConcurrentLruPolicy;
import org.springframework.lang.NonNull;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * 并发LRU本地缓存
 * @param <T> 缓存key的类型
 */
public class ConcurrentLruLocalCache<T> extends LocalCache<T> {
    private final BiFunction<String, CacheWrapper<T>, CacheWrapper<T>> refreshCountAdder = // 用于增加缓存刷新计数的函数式接口实现类
            (key, value) -> {
                ((ConcurrentLruPolicy<T>.LruCacheNode) value).increaseRefreshCount();
                return value;
            };

    public ConcurrentLruLocalCache(int capacity) throws NoSuchFieldException, IllegalAccessException {
        this(
                // 加载因子固定为0.75，构造器设置的加载因子仅用于设定初始容量
                // 底层数组默认初始化长度为指定容量的2倍，因此扩容阈值为指定容量的1.5倍
                new ConcurrentHashMap<>(Math.max(capacity, 4)),
                new ConcurrentLruPolicy<>(capacity)
        );
    }

    private ConcurrentLruLocalCache(Map<String, CacheWrapper<T>> map, EvictionPolicy<T> policy) {
        super(map, policy);
    }

    /**
     * 查询缓存
     * @param key 缓存key
     * @return 缓存value
     */
    public T get(@NonNull String key) {
        // 增加刷新计数
        CacheWrapper<T> node = map.computeIfPresent(key, refreshCountAdder);
        // 刷新缓存项并获取缓存项中的数据后返回
        return policy.get(node);
    }

    /**
     * 新增缓存
     * @param key 缓存key
     * @param value 缓存value
     */
    public void put(@NonNull String key, T value) {
        // 可能存在链表节点泄漏问题，需要用户自行保证不重复新增同一个缓存项
        CacheWrapper<T> node = policy.put(map, key, value);
        map.put(key, node);
    }

    /**
     * 移除缓存
     * @param key 缓存key
     */
    public void remove(@NonNull String key) {
        CacheWrapper<T> node = map.remove(key);
        policy.remove(node);
    }

    /**
     * 获取迭代器
     * @return 迭代器
     */
    @Override
    public Iterator<CacheWrapper<T>> iterator() {
        return policy.iterator();
    }
}
