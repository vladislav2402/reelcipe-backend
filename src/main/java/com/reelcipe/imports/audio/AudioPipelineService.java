package com.reelcipe.imports.audio;

import com.reelcipe.common.UuidV7;
import com.reelcipe.imports.AudioCheckpointPersistence;
import com.reelcipe.imports.ImportLeaseControl;
import com.reelcipe.imports.ImportProcessingException;
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
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Semaphore;

@Service
@ConditionalOnProperty(name = "app.role", havingValue = "worker")
public class AudioPipelineService {
    private final MediaAssetRepository assets;
    private final ObjectStorage storage;
    private final AudioExtractor extractor;
    private final AudioCheckpointPersistence checkpoints;
    private final Path workDirectory;
    private final long maxInputBytes;
    private final long maxOutputBytes;
    private final Duration maxDuration;
    private final Semaphore concurrency;

    public AudioPipelineService(
            MediaAssetRepository assets,
            ObjectStorage storage,
            AudioExtractor extractor,
            AudioCheckpointPersistence checkpoints,
            @Value("${app.media-tools.work-directory:${user.dir}/.local/media-work}")
            String workDirectory,
            @Value("${app.limits.max-video-size-bytes:104857600}") long maxInputBytes,
            @Value("${app.limits.max-audio-size-bytes:20971520}") long maxOutputBytes,
            @Value("${app.limits.max-media-duration-seconds:180}") long maxDurationSeconds,
            @Value("${app.worker.media-concurrency:1}") int mediaConcurrency) {
        this.assets = assets;
        this.storage = storage;
        this.extractor = extractor;
        this.checkpoints = checkpoints;
        this.workDirectory = Path.of(workDirectory).toAbsolutePath().normalize();
        this.maxInputBytes = maxInputBytes;
        this.maxOutputBytes = maxOutputBytes;
        this.maxDuration = Duration.ofSeconds(maxDurationSeconds);
        this.concurrency = new Semaphore(mediaConcurrency);
    }

    public AudioExtractionResult extract(ImportLease lease, ImportLeaseControl control) {
        if (!concurrency.tryAcquire()) {
            throw transientError("AUDIO_CONCURRENCY_LIMIT");
        }
        Path input = null;
        Path output = null;
        try {
            if (!control.isValid()) {
                throw new LeaseLostException(lease);
            }
            MediaAsset source = sourceAsset(lease.importId());
            if (source == null || source.getProcessingKey() == null) {
                throw transientError("PROCESSING_SOURCE_NOT_READY");
            }
            input = tempFile("reelcipe-audio-source-", ".media");
            download(source.getProcessingKey(), input, source.getSizeBytes());
            AudioExtractor.Probe inputProbe = extractor.probe(input);
            validateDuration(inputProbe.duration(), "MEDIA_DURATION_LIMIT_EXCEEDED");
            if (!inputProbe.hasAudio()) {
                checkpoints.descriptionOnly(lease);
                return AudioExtractionResult.descriptionOnly();
            }
            output = tempFile("reelcipe-audio-output-", ".flac");
            extractor.extract(input, output);
            AudioExtractor.Probe outputProbe = extractor.probe(output);
            validateOutput(outputProbe);
            long sizeBytes = Files.size(output);
            if (sizeBytes > maxOutputBytes) {
                throw permanent("AUDIO_SIZE_LIMIT_EXCEEDED");
            }
            String sha256 = sha256(output);
            UUID audioAssetId = UuidV7.randomUuid();
            String processingKey = audioKey(lease, audioAssetId);
            upload(processingKey, output, sizeBytes);
            checkpoints.checkpoint(
                    lease,
                    audioAssetId,
                    processingKey,
                    sha256,
                    sizeBytes,
                    durationSeconds(outputProbe.duration()));
            return AudioExtractionResult.audioReady(audioAssetId);
        } catch (AudioExtractionException exception) {
            throw map(exception);
        } catch (IOException exception) {
            throw transientError("AUDIO_LOCAL_FILE_FAILED", exception);
        } finally {
            delete(input);
            delete(output);
            concurrency.release();
        }
    }

    private MediaAsset sourceAsset(UUID importId) {
        return assets.findByImportIdAndAssetType(importId, MediaAssetType.SOURCE)
                .orElse(null);
    }

    private void validateDuration(Duration duration, String errorCode) {
        if (duration.isZero() || duration.compareTo(maxDuration) > 0) {
            throw permanent(errorCode);
        }
    }

    private void validateOutput(AudioExtractor.Probe probe) {
        if (probe.audioStreams() != 1
                || probe.sampleRate() != 16000
                || probe.channels() != 1
                || !"flac".equalsIgnoreCase(probe.codec())) {
            throw permanent("NORMALIZED_AUDIO_INVALID");
        }
        validateDuration(probe.duration(), "AUDIO_DURATION_LIMIT_EXCEEDED");
    }

    private void download(String key, Path output, long expectedSize) {
        try (InputStream input = storage.open(key);
                OutputStream destination = Files.newOutputStream(output)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxInputBytes || total > expectedSize) {
                    throw permanent("MEDIA_INPUT_SIZE_LIMIT_EXCEEDED");
                }
                destination.write(buffer, 0, read);
            }
            if (total != expectedSize) {
                throw permanent("MEDIA_INPUT_SIZE_MISMATCH");
            }
        } catch (ImportProcessingException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw transientError("MEDIA_INPUT_READ_FAILED", exception);
        }
    }

    private void upload(String key, Path file, long sizeBytes) {
        try (InputStream input = Files.newInputStream(file)) {
            storage.put(key, input, sizeBytes, "audio/flac");
        } catch (IOException | RuntimeException exception) {
            throw transientError("AUDIO_UPLOAD_FAILED", exception);
        }
    }

    private Path tempFile(String prefix, String suffix) throws IOException {
        Files.createDirectories(workDirectory);
        return Files.createTempFile(workDirectory, prefix, suffix);
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private int durationSeconds(Duration duration) {
        long seconds = (duration.toMillis() + 999) / 1000;
        return Math.toIntExact(seconds);
    }

    private String audioKey(ImportLease lease, UUID assetId) {
        return "processing/" + lease.userId()
                + "/" + lease.importId()
                + "/audio-" + assetId
                + "/lease-" + lease.leaseVersion()
                + "/normalized.flac";
    }

    private ImportProcessingException map(AudioExtractionException exception) {
        return switch (exception.kind()) {
            case TIMEOUT, TOOL_UNAVAILABLE -> transientError(
                    "AUDIO_TOOL_UNAVAILABLE", exception);
            case INVALID_MEDIA, OUTPUT_INVALID -> permanent(
                    "AUDIO_MEDIA_INVALID", exception);
        };
    }

    private ImportProcessingException permanent(String code) {
        return new ImportProcessingException(ImportFailure.permanent(code));
    }

    private ImportProcessingException permanent(String code, Throwable cause) {
        return new ImportProcessingException(ImportFailure.permanent(code), cause);
    }

    private ImportProcessingException transientError(String code) {
        return new ImportProcessingException(ImportFailure.transientError(code));
    }

    private ImportProcessingException transientError(String code, Throwable cause) {
        return new ImportProcessingException(ImportFailure.transientError(code), cause);
    }

    private void delete(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Temporary media files are bounded and can be cleaned by the next worker pass.
        }
    }
}
