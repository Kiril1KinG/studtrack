package ru.diploma.studtrack.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
/**
 * Конфигурирует S3-клиенты для работы с MinIO.
 */
public class StorageConfig {

    @Bean
    /**
     * Создаёт S3-клиент для операций чтения и записи объектов.
     *
     * @param properties настройки MinIO
     * @return bean S3Client
     */
    public S3Client s3Client(MinioProperties properties) {
        return S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())
                ))
                .forcePathStyle(true)
                .build();
    }

    @Bean
    /**
     * Создаёт presigner для генерации временных URL доступа к объектам.
     *
     * @param properties настройки MinIO
     * @return bean S3Presigner
     */
    public S3Presigner s3Presigner(MinioProperties properties) {
        String presignEndpoint = properties.publicEndpoint() != null && !properties.publicEndpoint().isBlank()
                ? properties.publicEndpoint()
                : properties.endpoint();
        return S3Presigner.builder()
                .endpointOverride(URI.create(presignEndpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())
                ))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}
