package com.gamelog.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 缓存配置
 * 使用 Caffeine 本地缓存，统计数据缓存 10 秒
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("stats");
        // 显式指定泛型类型，并用 Objects.requireNonNull 消除 @NonNull 警告
        manager.setCaffeine(Objects.requireNonNull(Caffeine.<Object, Object>newBuilder()
                .expireAfterWrite(10, TimeUnit.SECONDS)
                .maximumSize(5000)
                .recordStats()));
        return manager;
    }
}