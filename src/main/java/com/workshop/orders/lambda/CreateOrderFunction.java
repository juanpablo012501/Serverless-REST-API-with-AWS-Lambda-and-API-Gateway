package com.workshop.orders.lambda;

import com.workshop.orders.domain.CreateOrderRequest;
import com.workshop.orders.domain.Order;

public class CreateOrderFunction {
    public Order handleRequest(CreateOrderRequest request) {
        return LambdaErrors.translate(() -> OrderServiceProvider.get().createOrder(request));
    }
}