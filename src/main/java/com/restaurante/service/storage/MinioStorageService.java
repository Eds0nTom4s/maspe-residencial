package com.restaurante.service.storage;

import com.restaurante.config.storage.MinioProperties;
import com.restaurante.exception.BusinessException;
import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * Implementação do StorageService usando MinIO
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MinioStorageService implements StorageService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @Override
    public String uploadFile(MultipartFile file, String directory) {
        if (file.isEmpty()) {
            throw new BusinessException("Arquivo vazio para upload");
        }

        try {
            // Garante que o diretório termina com /
            if (directory != null && !directory.endsWith("/")) {
                directory += "/";
            } else if (directory == null) {
                directory = "";
            }

            // Garante que o bucket existe
            boolean bucketExists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(minioProperties.getBucketName()).build()
            );
            
            if (!bucketExists) {
                log.info("Criando bucket MinIO: {}", minioProperties.getBucketName());
                minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(minioProperties.getBucketName()).build()
                );
            }

            // Gera nome único para o arquivo
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".") 
                ? originalFilename.substring(originalFilename.lastIndexOf(".")) 
                : "";
            String fileName = directory + UUID.randomUUID().toString() + extension;

            // Upload para o MinIO
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(
                    PutObjectArgs.builder()
                        .bucket(minioProperties.getBucketName())
                        .object(fileName)
                        .stream(inputStream, file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
                );
            }

            // Retorna a URL pública (ajustar conf conforme necessidade)
            String publicUrl = minioProperties.getPublicUrl();
            if (!publicUrl.endsWith("/")) {
                publicUrl += "/";
            }
            
            return publicUrl + fileName;

        } catch (Exception e) {
            log.error("Erro no upload para o MinIO: {}", e.getMessage(), e);
            throw new BusinessException("Falha ao salvar arquivo no armazenamento: " + e.getMessage());
        }
    }

    @Override
    public void deleteFile(String fileName) {
        try {
            // Extrai o nome do objeto se for uma URL completa
            String objectName = fileName;
            if (fileName.contains(minioProperties.getBucketName())) {
                objectName = fileName.substring(fileName.indexOf(minioProperties.getBucketName()) + minioProperties.getBucketName().length() + 1);
            }

            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .object(objectName)
                    .build()
            );
            log.info("Arquivo removido do MinIO: {}", objectName);
        } catch (Exception e) {
            log.warn("Falha ao tentar remover arquivo do MinIO {}: {}", fileName, e.getMessage());
        }
    }
}
