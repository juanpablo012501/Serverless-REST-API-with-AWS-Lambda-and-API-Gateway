package com.workshop.orders.application;

import com.workshop.orders.domain.Order;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class InMemoryOrderRepository implements OrderRepository {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger(0);

    /** Repositorio con datos predefinidos (útil en Lambda, donde la memoria es efímera). */
    public static InMemoryOrderRepository withSampleData() {
        InMemoryOrderRepository repo = new InMemoryOrderRepository();
        repo.save(new Order(repo.nextId(), "CUST-42", "MacBook Air", 1));
        repo.save(new Order(repo.nextId(), "CUST-42", "Mouse", 2));
        repo.save(new Order(repo.nextId(), "CUST-7", "Keyboard", 2));
        return repo;
    }

    @Override
    public String nextId() {
        return String.format("ORD-%03d", sequence.incrementAndGet());
    }

    @Override
    public Order save(Order order) {
        orders.put(order.getId(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(String id) {
        return Optional.ofNullable(orders.get(id));
    }

    @Override
    public List<Order> findByCustomerId(String customerId) {
        return orders.values().stream()
                .filter(o -> o.getCustomerId().equals(customerId))
                .sorted(Comparator.comparing(Order::getId))
                .toList();
    }

    @Override
    public boolean deleteById(String id) {
        return orders.remove(id) != null;
    }
}