package com.restaurante.store.security;

import com.restaurante.exception.BusinessException;
import com.restaurante.store.dto.StoreSocioIdentityDTO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAssociagestIdentityAdapter implements AssociagestIdentityPort {

    @Value("${store.socio.jwt.secret}")
    private String socioJwtSecret;

    @Value("${store.socio.jwt.issuer:associagest}")
    private String expectedIssuer;

    @Override
    public StoreSocioIdentityDTO validate(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getChaveAssinatura())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (StringUtils.hasText(expectedIssuer)
                && StringUtils.hasText(claims.getIssuer())
                && !expectedIssuer.equals(claims.getIssuer())) {
            throw new BusinessException("Issuer do token de sócio inválido");
        }

        String socioId = claims.getSubject();
        String phone = claims.get("telefone", String.class);
        if (!StringUtils.hasText(socioId) || !StringUtils.hasText(phone)) {
            throw new BusinessException("Token de sócio sem sub ou telefone");
        }

        return new StoreSocioIdentityDTO(
                socioId,
                claims.get("nome", String.class),
                phone,
                claims.get("email", String.class));
    }

    private SecretKey getChaveAssinatura() {
        byte[] keyBytes = socioJwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
