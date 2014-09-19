package io.github.floto.core.proxy;

import com.google.common.base.Throwables;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.client.cache.HttpCacheUpdateCallback;
import org.apache.http.client.cache.HttpCacheUpdateException;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

public class PersistentHttpCacheStorage implements HttpCacheStorage, Closeable {
    Logger log = LoggerFactory.getLogger(PersistentHttpCacheStorage.class);
    private DB db;
    private HTreeMap<String, HttpCacheEntry> map;

    public PersistentHttpCacheStorage(File directory) {
        File cacheEntries = new File(directory, "cacheEntries");
        db = DBMaker.newFileDB(cacheEntries)
                .closeOnJvmShutdown()
                .make();
        map = db.createHashMap("entries").makeOrGet();
    }

    @Override
    public void putEntry(String key, HttpCacheEntry entry) throws IOException {
        log.debug("PUT {}->{}", key, entry);
        map.put(key, entry);
        db.commit();
    }

    @Override
    public HttpCacheEntry getEntry(String key) throws IOException {
        HttpCacheEntry entry = map.get(key);
        log.debug("GET {}->{}", key, entry);
        if(entry == null) {
            log.debug("Cache MISS: {}", key);
        } else {
            log.debug("Cache HIT: {}", key);
        }
        return entry;
    }

    @Override
    public void removeEntry(String key) throws IOException {
        log.debug("REMOVE {}", key);
        map.remove(key);
    }

    @Override
    public void updateEntry(String key, HttpCacheUpdateCallback callback) throws IOException, HttpCacheUpdateException {
        map.compute(key, (key2, oldEntry) -> {
            try {
                HttpCacheEntry newEntry = callback.update(oldEntry);
                log.debug("UPDATE {}->{} from {}", key, newEntry, oldEntry);
                return newEntry;
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        });
        db.commit();
    }

    @Override
    public void close() throws IOException {
        db.close();
        db = null;
        map = null;
    }
}
