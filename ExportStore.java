package org.example.democolauam;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ExportStore {

    public record StoredFile(String filename, byte[] bytes, Instant expiresAt) {}

    private final Map<String, StoredFile> store = new ConcurrentHashMap<>();
    private final long ttlSeconds = 30 * 60; // 30 minutes

    public String put(byte[] bytes, String filename) {
        String token = UUID.randomUUID().toString().replace("-", "");
        store.put(token, new StoredFile(filename, bytes, Instant.now().plusSeconds(ttlSeconds)));
        return token;
    }

    public StoredFile get(String token) {
        if (token == null) return null;
        StoredFile f = store.get(token);
        if (f == null) return null;
        if (Instant.now().isAfter(f.expiresAt())) {
            store.remove(token);
            return null;
        }
        return f;
    }
}
