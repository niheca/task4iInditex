package com.hackathon.inditex.service;

import com.hackathon.inditex.Commons.dtos.request.CreateOrderRequest;
import com.hackathon.inditex.Commons.dtos.response.OrderCreatedResponse;
import com.hackathon.inditex.Commons.dtos.response.OrderResponse;
import com.hackathon.inditex.Commons.dtos.response.ProcessedOrder.ProcessedOrderResponseAssigned;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public interface OrderService {

    OrderCreatedResponse createOrder(CreateOrderRequest request);

    List<OrderResponse> getAllOrders();

    Map<String, LinkedList<Object>> centerAssignment();
}
