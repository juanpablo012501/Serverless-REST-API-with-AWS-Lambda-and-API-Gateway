package com.workshop.orders.http;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.workshop.orders.application.InMemoryOrderRepository;
import com.workshop.orders.application.OrderService;
import com.workshop.orders.domain.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OrdersHttpHandlerTest {

    private OrdersHttpHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OrdersHttpHandler(new OrderService(InMemoryOrderRepository.withSampleData()));
    }

    // ---------- Helpers para construir eventos como los de API Gateway ----------

    private static APIGatewayProxyRequestEvent getById(String id) {
        return new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withResource("/orders/{id}")
                .withPath("/orders/" + id)
                .withPathParameters(Map.of("id", id));
    }

    private static APIGatewayProxyRequestEvent search(Map<String, String> query) {
        return new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withResource("/orders")
                .withPath("/orders")
                .withQueryStringParameters(query);
    }

    private static APIGatewayProxyRequestEvent post(String body) {
        return new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withResource("/orders")
                .withPath("/orders")
                .withBody(body);
    }

    private APIGatewayProxyResponseEvent call(APIGatewayProxyRequestEvent event) {
        return handler.handleRequest(event, null);
    }

    private static APIGatewayProxyRequestEvent deleteById(String id) {
        return new APIGatewayProxyRequestEvent()
                .withHttpMethod("DELETE")
                .withResource("/orders/{id}")
                .withPath("/orders/" + id)
                .withPathParameters(Map.of("id", id));
    }

    // ---------- GET /orders/{id} ----------

    @Test
    void getExistingOrderReturns200() {
        APIGatewayProxyResponseEvent response = call(getById("ORD-001"));
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("\"customerId\":\"CUST-42\""));
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
    }

    @Test
    void getMissingOrderReturns404WithErrorFormat() {
        APIGatewayProxyResponseEvent response = call(getById("ORD-999"));
        assertEquals(404, response.getStatusCode());
        assertEquals("{\"error\":\"ORDER_NOT_FOUND\",\"message\":\"Order ORD-999 does not exist\"}",
                response.getBody());
    }

    // ---------- GET /orders?customerId= ----------

    @Test
    void searchByCustomerReturns200() {
        APIGatewayProxyResponseEvent response = call(search(Map.of("customerId", "CUST-42")));
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().startsWith("["));
        assertTrue(response.getBody().contains("ORD-001"));
    }

    @Test
    void searchWithoutQueryStringReturns400() {
        APIGatewayProxyResponseEvent response = call(search(null));
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("INVALID_REQUEST"));
    }

    // ---------- POST /orders ----------

    @Test
    void postValidOrderReturns201WithLocation() {
        APIGatewayProxyResponseEvent response =
                call(post("{\"customerId\":\"CUST-42\",\"product\":\"MacBook Air\",\"quantity\":1}"));
        assertEquals(201, response.getStatusCode());
        assertEquals("/orders/ORD-004", response.getHeaders().get("Location"));
        assertTrue(response.getBody().contains("\"id\":\"ORD-004\""));
    }

    @Test
    void createdOrderCanBeRetrievedAfterwards() {
        call(post("{\"customerId\":\"CUST-100\",\"product\":\"Monitor\",\"quantity\":3}"));
        APIGatewayProxyResponseEvent response = call(getById("ORD-004"));
        assertEquals(200, response.getStatusCode());
    }

    @Test
    void postWithZeroQuantityReturns400() {
        APIGatewayProxyResponseEvent response =
                call(post("{\"customerId\":\"CUST-42\",\"product\":\"Mouse\",\"quantity\":0}"));
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("quantity must be greater than 0"));
    }

    @Test
    void postWithTextQuantityReturns400() {
        APIGatewayProxyResponseEvent response =
                call(post("{\"customerId\":\"CUST-42\",\"product\":\"Mouse\",\"quantity\":\"dos\"}"));
        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("INVALID_REQUEST"));
    }

    @Test
    void postWithMalformedJsonReturns400() {
        APIGatewayProxyResponseEvent response = call(post("{\"customerId\": "));
        assertEquals(400, response.getStatusCode());
    }

    @Test
    void postWithoutBodyReturns400() {
        APIGatewayProxyResponseEvent response = call(post(null));
        assertEquals(400, response.getStatusCode());
    }

    // ---------- Casos generales ----------

    @Test
    void unsupportedMethodReturns405() {
        APIGatewayProxyRequestEvent event = getById("ORD-001").withHttpMethod("PUT");
        assertEquals(405, call(event).getStatusCode());
    }

    @Test
    void unexpectedErrorReturns500WithoutInternalDetails() {
        InMemoryOrderRepository failing = new InMemoryOrderRepository() {
            @Override
            public Optional<Order> findById(String id) {
                throw new IllegalStateException("database connection lost");
            }
        };
        OrdersHttpHandler failingHandler = new OrdersHttpHandler(new OrderService(failing));

        APIGatewayProxyResponseEvent response = failingHandler.handleRequest(getById("ORD-001"), null);

        assertEquals(500, response.getStatusCode());
        assertEquals("{\"error\":\"INTERNAL_ERROR\",\"message\":\"Unexpected error\"}", response.getBody());
        assertFalse(response.getBody().contains("database"));
    }

    // ----------- Casos delete ---------
    // ---------- DELETE /orders/{id} ----------

    @Test
    void deleteExistingOrderReturns204WithoutBody() {
        APIGatewayProxyResponseEvent response = call(deleteById("ORD-001"));
        assertEquals(204, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void deleteMissingOrderReturns404WithErrorFormat() {
        APIGatewayProxyResponseEvent response = call(deleteById("ORD-999"));
        assertEquals(404, response.getStatusCode());
        assertEquals("{\"error\":\"ORDER_NOT_FOUND\",\"message\":\"Order ORD-999 does not exist\"}",
                response.getBody());
    }

    @Test
    void deletingTwiceReturns204ThenOrder404() {
        assertEquals(204, call(deleteById("ORD-001")).getStatusCode());
        assertEquals(404, call(deleteById("ORD-001")).getStatusCode());
    }

    @Test
    void getAfterDeleteReturns404() {
        call(deleteById("ORD-001"));
        assertEquals(404, call(getById("ORD-001")).getStatusCode());
    }
}