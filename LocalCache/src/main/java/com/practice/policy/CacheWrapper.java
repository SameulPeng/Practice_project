package com.practice.policy;

/**
 * 缓存数据包装类接口
 * @param <T> 缓存key的类型
 */
public interface CacheWrapper<T> {
    T get();
}
