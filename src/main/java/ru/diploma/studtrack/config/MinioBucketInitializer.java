package ru.diploma.studtrack.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@Configuration
@RequiredArgsConstructor
/**
 * Проверяет наличие бакета MinIO при запуске и создаёт его при отсутствии.
 */
public class MinioBucketInitializer {

    /**
     * S3-клиент для проверки и создания бакета.
     */
    private final S3Client s3Client;
    /**
     * Настройки MinIO.
     */
    private final MinioProperties minioProperties;

    @Bean
    /**
     * Возвращает runner инициализации бакета MinIO.
     *
     * @return bean ApplicationRunner
     */
    public ApplicationRunner ensureMinioBucketExists() {
        return args -> {
            String bucket = minioProperties.bucket();
            if (bucket == null || bucket.isBlank()) {
                throw new IllegalStateException("minio.bucket must be configured");
            }

            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
                log.info("MinIO bucket '{}' already exists", bucket);
                return;
            } catch (NoSuchBucketException ignored) {
            } catch (S3Exception ex) {
                if (ex.statusCode() != 404) {
                    throw ex;
                }
            }

            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            log.info("MinIO bucket '{}' created", bucket);
        };
    }
}
