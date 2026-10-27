package com.workshop.orders.http;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workshop.orders.application.InMemoryOrderRepository;
import com.workshop.orders.application.OrderService;
import com.workshop.orders.domain.CreateOrderRequest;
import com.workshop.orders.domain.InvalidOrderException;
import com.workshop.orders.domain.Order;
import com.workshop.orders.domain.OrderNotFoundException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptador HTTP entre API Gateway (proxy integration) y la lógica de aplicación.
 * Única clase que conoce HTTP: rutas, parámetros, status codes, headers y JSON.
 */
public class OrdersHttpHandler
        implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final OrderService DEFAULT_SERVICE =
            new OrderService(InMemoryOrderRepository.withSampleData());

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final OrderService service;

    /** Constructor usado por Lambda. */
    public OrdersHttpHandler() {
        this(DEFAULT_SERVICE);
    }

    /** Constructor usado por los tests, para inyectar un servicio controlado. */
    public OrdersHttpHandler(OrderService service) {
        this.service = service;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            return route(event);
        } catch (OrderNotFoundException e) {
            return error(404, "ORDER_NOT_FOUND", e.getMessage());
        } catch (InvalidOrderException e) {
            return error(400, "INVALID_REQUEST", e.getMessage());
        } catch (JsonProcessingException e) {
            return error(400, "INVALID_REQUEST", "Request body is not valid JSON or has invalid field types");
        } catch (Exception e) {
            if (context != null) {
                context.getLogger().log("Unexpected error: " + e);
            }
            return error(500, "INTERNAL_ERROR", "Unexpected error");
        }
    }

    // ---------- Enrutamiento ----------

    private APIGatewayProxyResponseEvent route(APIGatewayProxyRequestEvent event) throws JsonProcessingException {
        String method = event.getHttpMethod();
        String resource = event.getResource();

        if ("/orders".equals(resource)) {
            if ("POST".equals(method)) return createOrder(event);
            if ("GET".equals(method))  return searchOrders(event);
        }
        if ("/orders/{id}".equals(resource)) {
            if ("GET".equals(method))  return getOrder(event);
            if ("DELETE".equals(method)) return deleteOrder(event);
        }
        return error(405, "METHOD_NOT_ALLOWED", method + " " + resource + " is not supported");
    }

    // ---------- Operaciones ----------

    private APIGatewayProxyResponseEvent createOrder(APIGatewayProxyRequestEvent event) throws JsonProcessingException {
        String body = event.getBody();
        CreateOrderRequest request = (body == null || body.isBlank())
                ? null
                : JSON.readValue(body, CreateOrderRequest.class);

        Order created = service.createOrder(request);

        APIGatewayProxyResponseEvent response = json(201, created);
        response.getHeaders().put("Location", "/orders/" + created.getId());
        return response;
    }

    private APIGatewayProxyResponseEvent getOrder(APIGatewayProxyRequestEvent event) throws JsonProcessingException {
        String id = param(event.getPathParameters(), "id");
        return json(200, service.getOrder(id));
    }

    private APIGatewayProxyResponseEvent searchOrders(APIGatewayProxyRequestEvent event) throws JsonProcessingException {
        String customerId = param(event.getQueryStringParameters(), "customerId");
        List<Order> orders = service.getOrdersByCustomer(customerId);
        return json(200, orders);
    }

    private APIGatewayProxyResponseEvent deleteOrder(APIGatewayProxyRequestEvent event) {
        String id = param(event.getPathParameters(), "id");
        service.deleteOrder(id);
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(204)
                .withHeaders(new HashMap<>());
    }

    // ---------- Utilidades HTTP ----------

    private static String param(Map<String, String> params, String name) {
        return params == null ? null : params.get(name);
    }

    private static APIGatewayProxyResponseEvent json(int status, Object body) throws JsonProcessingException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withHeaders(headers)
                .withBody(JSON.writeValueAsString(body));
    }

    private static APIGatewayProxyResponseEvent error(int status, String code, String message) {
        try {
            return json(status, new ErrorResponse(code, message));
        } catch (JsonProcessingException e) {
            // Nunca debería ocurrir con un record de dos Strings
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"INTERNAL_ERROR\",\"message\":\"Unexpected error\"}");
        }
    }
}