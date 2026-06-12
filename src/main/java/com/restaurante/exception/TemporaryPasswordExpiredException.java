package com.restaurante.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class TemporaryPasswordExpiredException extends RuntimeException {
    public TemporaryPasswordExpiredException(String message) {
        super(message);
    }
}
