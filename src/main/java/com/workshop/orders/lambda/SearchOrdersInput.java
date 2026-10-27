package com.workshop.orders.lambda;

public class SearchOrdersInput {
    private String customerId;

    public SearchOrdersInput() { }
    public SearchOrdersInput(String customerId) { this.customerId = customerId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
}