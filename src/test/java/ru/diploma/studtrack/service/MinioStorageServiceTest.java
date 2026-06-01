package ru.diploma.studtrack.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.diploma.studtrack.config.MinioProperties;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinioStorageServiceTest {

    @Mock private S3Client s3Client;
    @Mock private S3Presigner s3Presigner;
    @Mock private PresignedGetObjectRequest presignedRequest;

    private MinioStorageService service;

    @BeforeEach
    void setUp() {
        MinioProperties props = new MinioProperties("http://localhost:9000", "http://localhost:9000", "ak", "sk", "bucket");
        service = new MinioStorageService(s3Client, s3Presigner, props);
    }

    @Test
    void uploadShouldUseConfiguredBucketAndKey() {
        service.upload("k", new ByteArrayInputStream("abc".getBytes()), "text/plain", 3);
        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void getPresignedDownloadUrlShouldReturnUrlString() throws Exception {
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);
        when(presignedRequest.url()).thenReturn(new URL("https://example.com/file"));

        String result = service.getPresignedDownloadUrl("k", Duration.ofMinutes(15));
        assertEquals("https://example.com/file", result);
    }

    @Test
    void deleteShouldCallS3Delete() {
        service.delete("k");
        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }
}

