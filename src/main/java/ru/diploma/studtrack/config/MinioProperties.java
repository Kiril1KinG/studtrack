package ru.diploma.studtrack.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "minio")
/**
 * Хранит настройки подключения к MinIO из конфигурации приложения.
 *
 * @param endpoint внутренний endpoint MinIO
 * @param publicEndpoint публичный endpoint для генерации ссылок
 * @param accessKey ключ доступа
 * @param secretKey секретный ключ
 * @param bucket имя бакета
 */
public record MinioProperties(
        String endpoint,
        String publicEndpoint,
        String accessKey,
        String secretKey,
        String bucket
) {}
