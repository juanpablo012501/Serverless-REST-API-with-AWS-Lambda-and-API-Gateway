package com.workshop.orders.application;

import com.workshop.orders.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OrderServiceTest {

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(new InMemoryOrderRepository());
    }

    @Test
    void createsValidOrder() {
        Order order = service.createOrder(new CreateOrderRequest("CUST-42", "MacBook Air", 1));

        assertNotNull(order.getId());
        assertEquals("CUST-42", order.getCustomerId());
        assertEquals("MacBook Air", order.getProduct());
        assertEquals(1, order.getQuantity());
    }

    @Test
    void retrievesExistingOrder() {
        Order created = service.createOrder(new CreateOrderRequest("CUST-7", "Keyboard", 2));

        Order found = service.getOrder(created.getId());

        assertEquals(created.getId(), found.getId());
        assertEquals("Keyboard", found.getProduct());
    }

    @Test
    void throwsWhenOrderDoesNotExist() {
        OrderNotFoundException ex = assertThrows(OrderNotFoundException.class,
                () -> service.getOrder("ORD-999"));
        assertEquals("Order ORD-999 does not exist", ex.getMessage());
    }

    @Test
    void findsOrdersByCustomer() {
        service.createOrder(new CreateOrderRequest("CUST-42", "MacBook Air", 1));
        service.createOrder(new CreateOrderRequest("CUST-42", "Mouse", 3));
        service.createOrder(new CreateOrderRequest("CUST-100", "Monitor", 1));

        List<Order> orders = service.getOrdersByCustomer("CUST-42");

        assertEquals(2, orders.size());
        assertTrue(orders.stream().allMatch(o -> o.getCustomerId().equals("CUST-42")));
    }

    @Test
    void returnsEmptyListForCustomerWithoutOrders() {
        assertTrue(service.getOrdersByCustomer("CUST-NOBODY").isEmpty());
    }

    @Test
    void rejectsMissingCustomerId() {
        assertThrows(InvalidOrderException.class,
                () -> service.createOrder(new CreateOrderRequest(null, "Mouse", 1)));
    }

    @Test
    void rejectsMissingProduct() {
        assertThrows(InvalidOrderException.class,
                () -> service.createOrder(new CreateOrderRequest("CUST-42", " ", 1)));
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThrows(InvalidOrderException.class,
                () -> service.createOrder(new CreateOrderRequest("CUST-42", "Mouse", 0)));
    }

    @Test
    void rejectsNullRequest() {
        assertThrows(InvalidOrderException.class, () -> service.createOrder(null));
    }

    @Test
    void rejectsBlankCustomerIdInSearch() {
        assertThrows(InvalidOrderException.class, () -> service.getOrdersByCustomer(""));
    }

    @Test
    void deletesExistingOrder() {
        Order created = service.createOrder(new CreateOrderRequest("CUST-42", "Mouse", 1));

        service.deleteOrder(created.getId());

        assertThrows(OrderNotFoundException.class, () -> service.getOrder(created.getId()));
    }

    @Test
    void deletingMissingOrderThrowsNotFound() {
        assertThrows(OrderNotFoundException.class, () -> service.deleteOrder("ORD-999"));
    }

    @Test
    void deletingWithBlankIdIsInvalid() {
        assertThrows(InvalidOrderException.class, () -> service.deleteOrder(" "));
    }
}