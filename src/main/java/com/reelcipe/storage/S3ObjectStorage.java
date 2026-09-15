package com.reelcipe.storage;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3", matchIfMissing = true)
public class S3ObjectStorage implements ObjectStorage {
    private final String bucket;
    private final S3Client client;
    private final S3Presigner presigner;

    public S3ObjectStorage(
            @Value("${app.storage.endpoint:http://localhost:9090}") String endpoint,
            @Value("${app.storage.region:us-east-1}") String region,
            @Value("${app.storage.access-key:accessKey1}") String accessKey,
            @Value("${app.storage.secret-key:secretKey1}") String secretKey,
            @Value("${app.storage.bucket:reelcipe-local}") String bucket,
            @Value("${app.storage.path-style:true}") boolean pathStyle) {
        this.bucket = bucket;
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey));
        S3Configuration configuration = S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyle)
                .build();
        this.client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(credentials)
                .serviceConfiguration(configuration)
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(credentials)
                .serviceConfiguration(configuration)
                .build();
    }

    @Override
    public PresignedUpload presignPut(String objectKey, String contentType, Duration duration) {
        Duration signatureDuration = duration.compareTo(Duration.ofSeconds(1)) < 0
                ? Duration.ofSeconds(1)
                : duration;
        PutObjectRequest object = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(contentType)
                .build();
        PutObjectPresignRequest request = PutObjectPresignRequest.builder()
                .signatureDuration(signatureDuration)
                .putObjectRequest(object)
                .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(request);
        return new PresignedUpload(
                presigned.url().toExternalForm(),
                Instant.now().plus(signatureDuration));
    }

    @Override
    public Optional<ObjectMetadata> head(String objectKey) {
        try {
            var object = client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build());
            return Optional.of(new ObjectMetadata(
                    object.contentLength(), object.contentType(), object.eTag()));
        } catch (NoSuchKeyException exception) {
            return Optional.empty();
        } catch (SdkException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("404")) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    @Override
    public void delete(String objectKey) {
        client.deleteObject(request -> request.bucket(bucket).key(objectKey));
    }

    @PreDestroy
    void close() {
        presigner.close();
        client.close();
    }
}
