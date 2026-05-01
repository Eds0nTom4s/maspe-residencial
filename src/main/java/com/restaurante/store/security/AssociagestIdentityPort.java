package com.restaurante.store.security;

import com.restaurante.store.dto.StoreSocioIdentityDTO;

public interface AssociagestIdentityPort {
    StoreSocioIdentityDTO validate(String token);
}
