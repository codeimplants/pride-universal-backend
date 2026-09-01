package com.prideuniversal.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * In-process caching via Caffeine.
 *
 * <p>Only one thing is cached today: {@code wing_settings}. It is read on
 * nearly every maintenance-bill operation and written maybe twice a year, which
 * is the textbook shape for a cache. Everything else is either write-heavy or
 * small enough that a Postgres index lookup is already cheap - caching it would
 * add staleness for no measurable gain.
 *
 * <p>Caffeine is in-process, so a multi-instance deployment would see each node
 * hold its own copy. At society scale we run one instance; if that changes,
 * this is the seam where Redis would go.
 */
@Configuration
public class CacheConfig {

    public static final String WING_SETTINGS = "wingSettings";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(WING_SETTINGS);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(64)
                .expireAfterWrite(Duration.ofMinutes(30))
                .recordStats());
        return manager;
    }
}
