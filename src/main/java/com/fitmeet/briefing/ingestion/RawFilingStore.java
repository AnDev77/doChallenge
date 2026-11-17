package com.fitmeet.briefing.ingestion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class RawFilingStore {
    public StoredRaw save(Path directory, byte[] raw) throws IOException {
        String hash;
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
        String key = hash.substring(0, 2) + "/" + hash + ".html";
        Path destination = directory.resolve(key);
        Files.createDirectories(destination.getParent());
        if (Files.exists(destination)) {
            if (!MessageDigest.isEqual(Files.readAllBytes(destination), raw)) {
                throw new IOException("Stored filing hash collision or corrupted raw file");
            }
            return new StoredRaw(hash, key);
        }
        Path staging = Files.createTempFile(destination.getParent(), "sec-", ".tmp");
        try {
            Files.write(staging, raw);
            Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(staging);
        }
        return new StoredRaw(hash, key);
    }

    public record StoredRaw(String sha256, String key) {}
}
