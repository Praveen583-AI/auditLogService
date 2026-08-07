
package com.praveen.auditlog.retention;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/** Prototype immutable archive adapter. Production should use write-once object storage. */
public final class FileArchiveStore implements RetentionService.ArchiveStore {
    private static final String VERSION = "1";
    private final Path root;
    private final ObjectMapper objectMapper;

    public FileArchiveStore(Path root, ObjectMapper objectMapper) {
        this.root = Objects.requireNonNull(root).toAbsolutePath().normalize();
        this.objectMapper = Objects.requireNonNull(objectMapper);
        try {
            Files.createDirectories(this.root);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot initialize archive directory", error);
        }
    }

    @Override
    public RetentionService.StoredObject putIfAbsent(
            String location,
            RetentionService.ArchiveBundle bundle
    ) {
        Path target = resolve(location);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, objectMapper.writeValueAsBytes(bundle),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return new RetentionService.StoredObject(location, VERSION);
        } catch (java.nio.file.FileAlreadyExistsException replay) {
            return new RetentionService.StoredObject(location, VERSION);
        } catch (IOException error) {
            throw new RetentionService.ArchiveFailure(
                    RetentionService.ArchiveFailureReason.ARCHIVE_WRITE_FAILED
            );
        }
    }

    @Override
    public RetentionService.ArchiveBundle read(String location, String version) {
        if (!VERSION.equals(version)) {
            throw new IllegalArgumentException("Unsupported archive object version");
        }
        try {
            return objectMapper.readValue(resolve(location).toFile(),
                    RetentionService.ArchiveBundle.class);
        } catch (IOException error) {
            throw new IllegalStateException("Archive object is unavailable", error);
        }
    }

    public Path path(String location) {
        return resolve(location);
    }

    private Path resolve(String location) {
        Path resolved = root.resolve(location + ".json").normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Archive location escapes configured root");
        }
        return resolved;
    }
}

