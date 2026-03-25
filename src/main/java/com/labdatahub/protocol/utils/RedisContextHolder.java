package com.labdatahub.protocol.utils;

import org.springframework.data.redis.core.RedisTemplate;

/**
 * @Description: Redis工具类
 * @Author: labdatahub
 * @CreateTime: 2026-01-06
 */
public class RedisContextHolder {
    private static volatile RedisTemplate<String, Object> redisTemplate;

    public static void setRedisTemplate(RedisTemplate<String, Object> template) {
        redisTemplate = template;
    }

    public static RedisTemplate<String, Object> getRedisTemplate() {
        if (redisTemplate == null) {
            throw new IllegalStateException("RedisTemplate 未初始化，请先调用 setRedisTemplate");
        }
        return redisTemplate;
    }

    public static void clear() {
        redisTemplate = null;
    }
}