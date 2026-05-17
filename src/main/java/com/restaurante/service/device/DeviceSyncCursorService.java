package com.restaurante.service.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurante.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class DeviceSyncCursorService {

    private final ObjectMapper objectMapper;

    public String encode(Object cursor) {
        try {
            String json = objectMapper.writeValueAsString(cursor);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new BusinessException("Cursor inválido.");
        }
    }

    public <T> T decode(String cursor, Class<T> type) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(cursor);
            String json = new String(bytes, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new BusinessException("Cursor inválido.");
        }
    }
}

