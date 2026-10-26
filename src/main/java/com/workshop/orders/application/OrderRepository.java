package com.workshop.orders.application;

import com.workshop.orders.domain.Order;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    String nextId();
    Order save(Order order);
    Optional<Order> findById(String id);
    List<Order> findByCustomerId(String customerId);
}