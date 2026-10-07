package com.ultras.claims.logging;

import com.ultras.claims.UltrasClaims;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** logs/claims.log - one line per important action, written off the main thread. */
public final class ClaimLog {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UltrasClaims plugin;
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "UltrasClaims-Log");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean enabled = true;
    private volatile Path file;

    public ClaimLog(UltrasClaims plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        enabled = plugin.getConfig().getBoolean("logging.enabled", true);
        file = plugin.getDataFolder().toPath().resolve(plugin.getConfig().getString("logging.file", "logs/claims.log"));
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
        } catch (IOException e) {
            plugin.getLogger().warning("cannot create log folder: " + e.getMessage());
        }
    }

    /** event, e.g. "CLAIM_CREATED", then a free text detail. */
    public void log(String event, String detail) {
        if (!enabled || exec.isShutdown()) {
            return;
        }
        final String line = LocalDateTime.now().format(TS) + " [" + event + "] " + detail + System.lineSeparator();
        final Path f = file;
        exec.execute(() -> {
            try (BufferedWriter w = Files.newBufferedWriter(f, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                w.write(line);
            } catch (IOException e) {
                // logging must never break gameplay
            }
        });
    }

    public void close() {
        exec.shutdown();
        try {
            exec.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
