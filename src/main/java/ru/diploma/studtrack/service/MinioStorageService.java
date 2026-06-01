package ru.diploma.studtrack.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.diploma.studtrack.config.MinioProperties;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.time.Duration;

/**
 * Инкапсулирует работу с S3-совместимым хранилищем MinIO.
 */
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    /**
     * S3-клиент для выполнения операций чтения и записи объектов.
     */
    private final S3Client s3Client;
    /**
     * Presigner для генерации временных URL на скачивание.
     */
    private final S3Presigner s3Presigner;
    /**
     * Конфигурация подключения к MinIO.
     */
    private final MinioProperties properties;

    /**
     * Загружает объект в бакет MinIO.
     *
     * @param key ключ объекта
     * @param data поток данных
     * @param contentType MIME-тип контента
     * @param size размер объекта в байтах
     */
    public void upload(String key, InputStream data, String contentType, long size) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .build();
        s3Client.putObject(request, RequestBody.fromInputStream(data, size));
    }

    /**
     * Генерирует временный URL для скачивания объекта.
     *
     * @param key ключ объекта
     * @param expiry срок действия ссылки
     * @return presigned URL
     */
    public String getPresignedDownloadUrl(String key, Duration expiry) {
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .getObjectRequest(objectRequest)
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }

    /**
     * Удаляет объект из бакета MinIO.
     *
     * @param key ключ объекта
     */
    public void delete(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build());
    }
}
