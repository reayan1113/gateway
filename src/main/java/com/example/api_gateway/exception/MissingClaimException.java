package com.example.api_gateway.exception;

public class MissingClaimException extends RuntimeException {
    public MissingClaimException(String message) {
        super(message);
    }
}

