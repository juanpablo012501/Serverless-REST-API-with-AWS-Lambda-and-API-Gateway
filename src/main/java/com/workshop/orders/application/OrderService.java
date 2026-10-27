package com.workshop.orders.application;

import com.workshop.orders.domain.*;
import java.util.List;

public class OrderService {

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public Order createOrder(CreateOrderRequest request) {
        validate(request);
        Order order = new Order(
                repository.nextId(),
                request.getCustomerId(),
                request.getProduct(),
                request.getQuantity());
        return repository.save(order);
    }

    public Order getOrder(String id) {
        if (isBlank(id)) {
            throw new InvalidOrderException("Order id is required");
        }
        return repository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    public List<Order> getOrdersByCustomer(String customerId) {
        if (isBlank(customerId)) {
            throw new InvalidOrderException("customerId is required");
        }
        return repository.findByCustomerId(customerId);
    }

    private void validate(CreateOrderRequest request) {
        if (request == null) {
            throw new InvalidOrderException("Request body is required");
        }
        if (isBlank(request.getCustomerId())) {
            throw new InvalidOrderException("customerId is required");
        }
        if (isBlank(request.getProduct())) {
            throw new InvalidOrderException("product is required");
        }
        if (request.getQuantity() == null || request.getQuantity() < 1) {
            throw new InvalidOrderException("quantity must be greater than 0");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public void deleteOrder(String id) {
        if (isBlank(id)) {
            throw new InvalidOrderException("Order id is required");
        }
        if (!repository.deleteById(id)) {
            throw new OrderNotFoundException(id);
        }
    }
}