package com.restaurante.config.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Propriedades de configuração do MinIO
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.storage.minio")
public class MinioProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName;
    private String publicUrl;
}
