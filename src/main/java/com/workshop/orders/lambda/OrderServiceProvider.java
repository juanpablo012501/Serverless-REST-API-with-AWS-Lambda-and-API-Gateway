package com.workshop.orders.lambda;

import com.workshop.orders.application.InMemoryOrderRepository;
import com.workshop.orders.application.OrderService;

final class OrderServiceProvider {

    private static final OrderService SERVICE =
            new OrderService(InMemoryOrderRepository.withSampleData());

    private OrderServiceProvider() { }

    static OrderService get() {
        return SERVICE;
    }
}