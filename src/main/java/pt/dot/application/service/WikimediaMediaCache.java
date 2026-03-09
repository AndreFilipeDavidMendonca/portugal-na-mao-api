package pt.dot.application.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WikimediaMediaCache {

    private static final long TTL_MS = Duration.ofHours(24).toMillis();

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<List<String>>> inflight = new ConcurrentHashMap<>();

    public List<String> getFresh(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) return null;

        if (System.currentTimeMillis() - entry.updatedAt() > TTL_MS) {
            cache.remove(key);
            return null;
        }

        return entry.urls();
    }

    public List<String> getOrFetch(String key, Callable<List<String>> fetcher) {
        List<String> cached = getFresh(key);
        if (cached != null) return cached;

        CompletableFuture<List<String>> existing = inflight.get(key);
        if (existing != null) return existing.join();

        CompletableFuture<List<String>> future = CompletableFuture.supplyAsync(() -> {
            try {
                List<String> urls = fetcher.call();
                cache.put(key, new CacheEntry(urls, System.currentTimeMillis()));
                return urls;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        });

        inflight.put(key, future);

        try {
            return future.join();
        } finally {
            inflight.remove(key);
        }
    }

    private record CacheEntry(List<String> urls, long updatedAt) {}
}