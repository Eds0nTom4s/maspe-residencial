package com.restaurante.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Utilitário para geração de QR Codes usando ZXing
 */
@Component
@Slf4j
public class QrCodeGenerator {

    private static final int DEFAULT_WIDTH = 300;
    private static final int DEFAULT_HEIGHT = 300;
    private static final String DEFAULT_IMAGE_FORMAT = "PNG";

    /**
     * Gera QR Code como array de bytes (PNG)
     *
     * @param content Conteúdo do QR Code (URL, texto, etc)
     * @return Array de bytes da imagem PNG
     */
    public byte[] generateQrCodeImage(String content) throws WriterException, IOException {
        return generateQrCodeImage(content, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * Gera QR Code com dimensões customizadas
     *
     * @param content Conteúdo do QR Code
     * @param width Largura da imagem
     * @param height Altura da imagem
     * @return Array de bytes da imagem PNG
     */
    public byte[] generateQrCodeImage(String content, int width, int height) 
            throws WriterException, IOException {
        
        log.debug("Gerando QR Code: {} ({}x{})", content, width, height);

        // Configurações do QR Code
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H); // Alta correção de erros
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 1); // Margem mínima

        // Gera matriz de bits
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);

        // Converte para imagem PNG
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, DEFAULT_IMAGE_FORMAT, outputStream);

        log.debug("QR Code gerado com sucesso: {} bytes", outputStream.size());
        
        return outputStream.toByteArray();
    }

    /**
     * Gera QR Code pequeno (150x150) para preview
     */
    public byte[] generateQrCodePreview(String content) throws WriterException, IOException {
        return generateQrCodeImage(content, 150, 150);
    }

    /**
     * Gera QR Code grande (500x500) para impressão
     */
    public byte[] generateQrCodePrint(String content) throws WriterException, IOException {
        return generateQrCodeImage(content, 500, 500);
    }

    /**
     * Valida se o conteúdo pode ser codificado em QR Code
     *
     * @param content Conteúdo a validar
     * @return true se válido
     */
    public boolean isValidContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        // QR Code suporta até ~4296 caracteres alfanuméricos
        // Limitamos a 2000 para garantir boa legibilidade
        return content.length() <= 2000;
    }

    /**
     * Calcula tamanho recomendado baseado no comprimento do conteúdo
     *
     * @param contentLength Tamanho do conteúdo
     * @return Tamanho recomendado (width = height)
     */
    public int calculateRecommendedSize(int contentLength) {
        if (contentLength < 50) {
            return 200;
        } else if (contentLength < 100) {
            return 300;
        } else if (contentLength < 200) {
            return 400;
        } else {
            return 500;
        }
    }
}
