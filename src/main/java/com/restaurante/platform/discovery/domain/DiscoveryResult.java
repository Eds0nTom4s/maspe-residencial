package com.restaurante.platform.discovery.domain;

import java.util.function.Function;

public sealed interface DiscoveryResult<T>
        permits DiscoveryResult.Success, DiscoveryResult.Empty, DiscoveryResult.Error {

    default <R> DiscoveryResult<R> map(Function<T, R> mapper) {
        if (this instanceof Success<T> success) {
            return new Success<>(mapper.apply(success.data()));
        }
        if (this instanceof Empty<T> empty) {
            return new Empty<>(empty.data() == null ? null : mapper.apply(empty.data()));
        }
        Error<T> error = (Error<T>) this;
        return new Error<>(error.reason(), error.message());
    }

    record Success<T>(T data) implements DiscoveryResult<T> {}

    record Empty<T>(T data) implements DiscoveryResult<T> {}

    record Error<T>(DiscoveryError reason, String message) implements DiscoveryResult<T> {}
}
