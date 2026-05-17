package com.restaurante.service.device;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Service
public class DeviceSyncEtagService {

    public String etagFor(String seed) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(seed.getBytes(StandardCharsets.UTF_8));
            String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            return "\"" + b64 + "\"";
        } catch (Exception e) {
            // fallback: ainda retorna algo estável o bastante para o request, sem vazar seed
            return "\"" + Integer.toHexString(seed.hashCode()) + "\"";
        }
    }
}

