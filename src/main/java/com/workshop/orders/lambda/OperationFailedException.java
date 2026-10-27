package com.workshop.orders.lambda;

public class OperationFailedException extends RuntimeException {
    public OperationFailedException(String code, String message, Throwable cause) {
        super(code + ": " + message, cause);
    }
}