package com.workshop.orders.lambda;

import com.workshop.orders.domain.Order;
import java.util.List;

public class SearchOrdersFunction {
    public List<Order> handleRequest(SearchOrdersInput input) {
        return LambdaErrors.translate(
                () -> OrderServiceProvider.get().getOrdersByCustomer(input.getCustomerId()));
    }
}