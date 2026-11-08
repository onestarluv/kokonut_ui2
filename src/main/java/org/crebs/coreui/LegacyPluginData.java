package org.crebs.coreui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** Moves an old plugin data directory before any configuration or world data is loaded. */
public final class LegacyPluginData {
    private LegacyPluginData() { }

    public static void migrate(Path destination, String legacyName) throws IOException {
        Path target = destination.toAbsolutePath().normalize();
        Path parent = target.getParent();
        Path source = parent.resolve(legacyName).normalize();
        if (!parent.equals(source.getParent()) || source.equals(target)) {
            throw new IOException("Invalid legacy plugin data path");
        }
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) return;
        if (Files.isSymbolicLink(source) || Files.isSymbolicLink(target)
                || !Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Legacy plugin data must be a regular directory");
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Data destination is not a directory");
            try (var children = Files.list(target)) {
                if (children.findAny().isPresent()) throw new IOException("Both old and new plugin data directories contain data; refusing to overwrite");
            }
            Files.delete(target);
        }
        Files.move(source, target);
    }
}
