package nl.jordi.homeautomation.stickyeventbus;

import java.util.concurrent.ExecutionException;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public class StickyEventBusRegistry {

    private static LoadingCache<String, StickyEventBus> cache = CacheBuilder.newBuilder().build(createCacheLoader());

    private static CacheLoader<String, StickyEventBus> createCacheLoader() {
        return new CacheLoader<String, StickyEventBus>() {

            @Override
            public StickyEventBus load(final String name) throws Exception {
                return new StickyEventBus(name);
            }
        };
    }

    private StickyEventBusRegistry() {
    }

    public static StickyEventBus get(final String name) {
        try {
            return StickyEventBusRegistry.cache.get(name);
        } catch (ExecutionException e) {
//            Logger.w(e, "Could not get EventBus from Cache");
            return null;
        }
    }

    public static void clear() {
        StickyEventBusRegistry.cache = CacheBuilder.newBuilder().build(createCacheLoader());
    }

}
