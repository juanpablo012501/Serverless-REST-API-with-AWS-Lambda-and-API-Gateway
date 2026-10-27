package com.workshop.orders.lambda;

import com.workshop.orders.domain.InvalidOrderException;
import com.workshop.orders.domain.OrderNotFoundException;
import java.util.function.Supplier;

final class LambdaErrors {

    private LambdaErrors() { }

    static <T> T translate(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (OrderNotFoundException e) {
            throw new OperationFailedException("ORDER_NOT_FOUND", e.getMessage(), e);
        } catch (InvalidOrderException e) {
            throw new OperationFailedException("INVALID_REQUEST", e.getMessage(), e);
        } catch (RuntimeException e) {
            // No exponemos detalles internos al cliente
            throw new OperationFailedException("INTERNAL_ERROR", "Unexpected error", e);
        }
    }
}