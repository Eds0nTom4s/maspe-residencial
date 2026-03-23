package com.restaurante.service.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Interface genérica para serviços de armazenamento de arquivos
 */
public interface StorageService {
    
    /**
     * Faz o upload de um arquivo e retorna a URL pública
     * @param file Arquivo binário
     * @param directory Diretório no bucket (ex: "produtos/")
     * @return URL final do arquivo
     */
    String uploadFile(MultipartFile file, String directory);
    
    /**
     * Remove um arquivo pelo seu caminho/nome
     * @param fileName Nome do arquivo no bucket
     */
    void deleteFile(String fileName);
}
