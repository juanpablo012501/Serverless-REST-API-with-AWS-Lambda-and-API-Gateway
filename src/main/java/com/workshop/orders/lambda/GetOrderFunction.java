package com.workshop.orders.lambda;

import com.workshop.orders.domain.Order;

public class GetOrderFunction {
    public Order handleRequest(GetOrderInput input) {
        return LambdaErrors.translate(() -> OrderServiceProvider.get().getOrder(input.getId()));
    }
}