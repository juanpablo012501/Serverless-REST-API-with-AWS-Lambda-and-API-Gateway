package com.workshop.orders.lambda;

import com.workshop.orders.domain.CreateOrderRequest;
import com.workshop.orders.domain.Order;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LambdaFunctionsTest {

    @Test
    void getReturnsPreloadedOrder() {
        Order order = new GetOrderFunction().handleRequest(new GetOrderInput("ORD-001"));
        assertEquals("CUST-42", order.getCustomerId());
    }

    @Test
    void getMissingOrderUsesNotFoundPrefix() {
        OperationFailedException ex = assertThrows(OperationFailedException.class,
                () -> new GetOrderFunction().handleRequest(new GetOrderInput("ORD-999")));
        assertEquals("ORDER_NOT_FOUND: Order ORD-999 does not exist", ex.getMessage());
    }

    @Test
    void invalidCreateUsesInvalidRequestPrefix() {
        OperationFailedException ex = assertThrows(OperationFailedException.class,
                () -> new CreateOrderFunction().handleRequest(new CreateOrderRequest("CUST-42", "Mouse", 0)));
        assertTrue(ex.getMessage().startsWith("INVALID_REQUEST: "));
    }

    @Test
    void searchWithoutCustomerUsesInvalidRequestPrefix() {
        OperationFailedException ex = assertThrows(OperationFailedException.class,
                () -> new SearchOrdersFunction().handleRequest(new SearchOrdersInput("")));
        assertTrue(ex.getMessage().startsWith("INVALID_REQUEST: "));
    }

    @Test
    void searchReturnsPreloadedOrdersOfCustomer() {
        List<Order> orders = new SearchOrdersFunction().handleRequest(new SearchOrdersInput("CUST-7"));
        assertFalse(orders.isEmpty());
    }
}