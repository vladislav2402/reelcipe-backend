package com.reelcipe.imports.source;

import com.reelcipe.imports.ImportProcessingException;
import com.reelcipe.imports.domain.ImportFailure;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.*;

public class ManagedHttpDownloader {
    private final UrlSafetyPolicy safetyPolicy;
    private final HttpClient client;
    private final Duration requestTimeout;
    private final long maxBytes;
    private final int maxRedirects;
    private final Path tempDirectory;
    private final ExecutorService readExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "reelcipe-source-read");
        thread.setDaemon(true);
        return thread;
    });

    public ManagedHttpDownloader(
            UrlSafetyPolicy safetyPolicy,
            Duration connectTimeout,
            Duration requestTimeout,
            long maxBytes,
            int maxRedirects,
            Path tempDirectory) {
        this.safetyPolicy = safetyPolicy;
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.requestTimeout = requestTimeout;
        this.maxBytes = maxBytes;
        this.maxRedirects = maxRedirects;
        this.tempDirectory = tempDirectory.toAbsolutePath().normalize();
    }

    public DownloadedSource download(URI initialUrl) {
        URI current = safetyPolicy.validate(initialUrl);
        for (int redirect = 0; redirect <= maxRedirects; redirect++) {
            HttpResponse<InputStream> response = send(current);
            int status = response.statusCode();
            if (isRedirect(status)) {
                close(response.body());
                if (redirect == maxRedirects || response.headers().firstValue("location").isEmpty()) {
                    throw permanent("SOURCE_REDIRECT_LIMIT_EXCEEDED");
                }
                URI next = current.resolve(response.headers().firstValue("location").orElseThrow());
                current = safetyPolicy.validate(next);
                continue;
            }
            if (status < 200 || status >= 300) {
                close(response.body());
                throw unavailable(status);
            }
            String contentType = response.headers().firstValue("content-type")
                    .map(this::mediaType)
                    .orElse("");
            if (!isMediaType(contentType)) {
                close(response.body());
                throw permanent("SOURCE_CONTENT_TYPE_UNSUPPORTED");
            }
            long declaredSize = response.headers().firstValueAsLong("content-length").orElse(-1L);
            if (declaredSize > maxBytes) {
                close(response.body());
                throw permanent("SOURCE_SIZE_LIMIT_EXCEEDED");
            }
            return save(current, response.body(), contentType);
        }
        throw permanent("SOURCE_REDIRECT_LIMIT_EXCEEDED");
    }

    private HttpResponse<InputStream> send(URI uri) {
        safetyPolicy.validate(uri);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "audio/*, video/*")
                .GET()
                .build();
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new ImportProcessingException(
                    ImportFailure.transientError("SOURCE_DOWNLOAD_TIMEOUT"), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ImportProcessingException(
                    ImportFailure.transientError("SOURCE_DOWNLOAD_INTERRUPTED"), exception);
        } catch (IOException exception) {
            throw new ImportProcessingException(
                    ImportFailure.transientError("SOURCE_DOWNLOAD_FAILED"), exception);
        }
    }

    private DownloadedSource save(URI url, InputStream input, String contentType) {
        Path file = null;
        try {
            Files.createDirectories(tempDirectory);
            file = Files.createTempFile(tempDirectory, "reelcipe-source-", ".download");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0;
            long deadline = System.nanoTime() + requestTimeout.toNanos();
            try (input; OutputStream output = Files.newOutputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = read(input, buffer, deadline)) != -1) {
                    total += read;
                    if (total > maxBytes) {
                        throw permanent("SOURCE_SIZE_LIMIT_EXCEEDED");
                    }
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
            }
            return new DownloadedSource(
                    url,
                    file,
                    total,
                    contentType,
                    HexFormat.of().formatHex(digest.digest()),
                    contentType.startsWith("audio/")
                            ? SourceVariant.FULL_AUDIO
                            : SourceVariant.VIDEO_FOR_AUDIO);
        } catch (ImportProcessingException exception) {
            delete(file);
            throw exception;
        } catch (IOException exception) {
            delete(file);
            throw new ImportProcessingException(
                    ImportFailure.transientError("SOURCE_DOWNLOAD_FAILED"), exception);
        } catch (NoSuchAlgorithmException exception) {
            delete(file);
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private int read(InputStream input, byte[] buffer, long deadline) {
        long remainingNanos = deadline - System.nanoTime();
        if (remainingNanos <= 0) {
            throw timeout();
        }
        Future<Integer> read = readExecutor.submit(() -> input.read(buffer));
        try {
            return read.get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            read.cancel(true);
            close(input);
            throw timeout();
        } catch (InterruptedException exception) {
            read.cancel(true);
            Thread.currentThread().interrupt();
            throw new ImportProcessingException(
                    ImportFailure.transientError("SOURCE_DOWNLOAD_INTERRUPTED"), exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw new ImportProcessingException(
                        ImportFailure.transientError("SOURCE_DOWNLOAD_FAILED"), ioException);
            }
            throw new ImportProcessingException(
                    ImportFailure.transientError("SOURCE_DOWNLOAD_FAILED"), cause);
        }
    }

    private ImportProcessingException timeout() {
        return new ImportProcessingException(
                ImportFailure.transientError("SOURCE_DOWNLOAD_TIMEOUT"));
    }

    private boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private boolean isMediaType(String contentType) {
        return contentType.startsWith("audio/") || contentType.startsWith("video/");
    }

    private String mediaType(String value) {
        return value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private ImportProcessingException unavailable(int status) {
        if (status == 404 || status == 410 || status == 401 || status == 403) {
            return new ImportProcessingException(ImportFailure.needsInput("SOURCE_UNAVAILABLE"));
        }
        return new ImportProcessingException(ImportFailure.transientError("SOURCE_HTTP_" + status));
    }

    private ImportProcessingException permanent(String errorCode) {
        return new ImportProcessingException(ImportFailure.permanent(errorCode));
    }

    private void close(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
            // The response body is already unusable after an early response decision.
        }
    }

    private void delete(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // A bounded temporary file can be removed by the next cleanup pass.
        }
    }
}
