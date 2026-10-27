package com.workshop.orders.lambda;

import com.workshop.orders.application.InMemoryOrderRepository;
import com.workshop.orders.application.OrderService;
import com.workshop.orders.domain.CreateOrderRequest;
import com.workshop.orders.domain.Order;

public class CreateOrderFunction {

    // static: se crea una vez por entorno de ejecución y se reutiliza mientras la Lambda esté "caliente"
    private static final OrderService SERVICE =
            new OrderService(InMemoryOrderRepository.withSampleData());

    public Order handleRequest(CreateOrderRequest request) {
        return SERVICE.createOrder(request);
    }
}