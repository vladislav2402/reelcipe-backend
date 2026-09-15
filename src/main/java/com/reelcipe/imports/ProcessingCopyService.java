package com.reelcipe.imports;

import com.reelcipe.imports.domain.*;
import com.reelcipe.storage.ObjectStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class ProcessingCopyService {
    private final MediaAssetRepository assets;
    private final ObjectStorage storage;
    private final ProcessingCopyPersistence persistence;
    private final Path tempDirectory;
    private final long maxBytes;

    public ProcessingCopyService(
            MediaAssetRepository assets,
            ObjectStorage storage,
            ProcessingCopyPersistence persistence,
            @Value("${app.worker.processing-copy-temp-directory:${java.io.tmpdir}/reelcipe-processing}")
            String tempDirectory,
            @Value("${app.worker.processing-copy-max-bytes:104857600}") long maxBytes) {
        this.assets = assets;
        this.storage = storage;
        this.persistence = persistence;
        this.tempDirectory = Path.of(tempDirectory).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    public void copy(ImportLease lease, ImportLeaseControl control) {
        if (lease.stage() != com.reelcipe.imports.domain.ImportStage.RESOLVING) {
            return;
        }
        MediaAsset source = assets.findByImportIdAndAssetType(
                        lease.importId(), MediaAssetType.SOURCE)
                .orElseThrow(() -> permanent("SOURCE_ASSET_NOT_FOUND"));
        if (source.getProcessingKey() != null) {
            persistence.checkpoint(
                    lease,
                    source.getProcessingKey(),
                    source.getSha256(),
                    source.getSizeBytes());
            return;
        }
        if (!control.isValid()) {
            throw new LeaseLostException(lease);
        }
        if (source.getSizeBytes() > maxBytes) {
            throw permanent("SOURCE_SIZE_LIMIT_EXCEEDED");
        }

        ObjectStorage.ObjectMetadata before = head(source.getStagingKey());
        validateDeclaredSize(source, before.sizeBytes());
        Path localFile = createTempFile();
        String processingKey = processingKey(lease, source);
        try {
            CopyResult copied = download(source.getStagingKey(), localFile);
            validateCopiedSize(source, copied.sizeBytes());
            validateUnchanged(source.getStagingKey(), before, copied.sizeBytes());
            upload(processingKey, localFile, copied.sizeBytes(), source.getContentType());
            persistence.checkpoint(
                    lease,
                    processingKey,
                    copied.sha256(),
                    copied.sizeBytes());
        } finally {
            deleteTempFile(localFile);
        }
    }

    private ObjectStorage.ObjectMetadata head(String key) {
        try {
            return storage.head(key).orElseThrow(() -> transientError("SOURCE_OBJECT_NOT_FOUND"));
        } catch (ImportProcessingException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ImportProcessingException(
                    ImportFailure.transientError("SOURCE_HEAD_FAILED"), exception);
        }
    }

    private CopyResult download(String key, Path localFile) {
        try (InputStream input = storage.open(key);
                OutputStream output = Files.newOutputStream(localFile)) {
            MessageDigest digest = sha256();
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw permanent("SOURCE_SIZE_LIMIT_EXCEEDED");
                }
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
            }
            return new CopyResult(total, HexFormat.of().formatHex(digest.digest()));
        } catch (ImportProcessingException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ImportProcessingException(
                    ImportFailure.transientError("SOURCE_DOWNLOAD_FAILED"), exception);
        }
    }

    private void upload(String key, Path localFile, long sizeBytes, String contentType) {
        try (InputStream input = Files.newInputStream(localFile)) {
            storage.put(key, input, sizeBytes, contentType);
        } catch (IOException | RuntimeException exception) {
            throw new ImportProcessingException(
                    ImportFailure.transientError("PROCESSING_COPY_PUT_FAILED"), exception);
        }
    }

    private void validateDeclaredSize(MediaAsset source, long actualSize) {
        validateSize(source.getSizeBytes(), actualSize, "SOURCE_SIZE_MISMATCH");
    }

    private void validateCopiedSize(MediaAsset source, long actualSize) {
        validateSize(source.getSizeBytes(), actualSize, "SOURCE_BYTES_CHANGED");
    }

    private void validateSize(long expectedSize, long actualSize, String errorCode) {
        if (expectedSize != actualSize) {
            throw permanent(errorCode);
        }
    }

    private void validateUnchanged(
            String key,
            ObjectStorage.ObjectMetadata before,
            long copiedSize) {
        ObjectStorage.ObjectMetadata after = head(key);
        if (after.sizeBytes() != copiedSize
                || !sameEtag(before.etag(), after.etag())) {
            throw transientError("SOURCE_CHANGED_DURING_COPY");
        }
    }

    private boolean sameEtag(String before, String after) {
        return before == null || after == null || before.equals(after);
    }

    private Path createTempFile() {
        try {
            Files.createDirectories(tempDirectory);
            return Files.createTempFile(tempDirectory, "reelcipe-source-", ".media");
        } catch (IOException exception) {
            throw new ImportProcessingException(
                    ImportFailure.transientError("PROCESSING_TEMP_FILE_FAILED"), exception);
        }
    }

    private void deleteTempFile(Path localFile) {
        try {
            Files.deleteIfExists(localFile);
        } catch (IOException ignored) {
            // The bounded temporary file can be removed by the next worker cleanup pass.
        }
    }

    private String processingKey(ImportLease lease, MediaAsset source) {
        return "processing/" + lease.userId()
                + "/" + lease.importId()
                + "/" + source.getId()
                + "/lease-" + lease.leaseVersion()
                + "/source";
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private ImportProcessingException permanent(String errorCode) {
        return new ImportProcessingException(ImportFailure.permanent(errorCode));
    }

    private ImportProcessingException transientError(String errorCode) {
        return new ImportProcessingException(ImportFailure.transientError(errorCode));
    }

    private record CopyResult(long sizeBytes, String sha256) {
    }
}
