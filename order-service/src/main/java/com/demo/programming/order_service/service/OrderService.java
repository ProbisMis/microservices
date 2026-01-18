package com.demo.programming.order_service.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.demo.programming.order_service.client.InventoryClient;
import com.demo.programming.order_service.dto.InventoryResponse;
import com.demo.programming.order_service.dto.OrderLineItemsDto;
import com.demo.programming.order_service.dto.OrderRequest;
import com.demo.programming.order_service.dto.OrderResponse;
import com.demo.programming.order_service.model.Order;
import com.demo.programming.order_service.model.OrderLineItems;
import com.demo.programming.order_service.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;

    @Transactional
    public void placeOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        orderRequest.getOrderLineItemsDtoList()
            .stream()
            .map(this::mapToEntity)
            .forEach(orderLineItems -> order.getOrderLineItemsList().add(orderLineItems));

        var skuCodes = order.getOrderLineItemsList()
            .stream()
            .map(OrderLineItems::getSkuCode)
            .toList();

        var result = inventoryClient.isInStock(skuCodes);

        if (result != null && !result.isEmpty() && result.stream().allMatch(InventoryResponse::isInStock))
            orderRepository.save(order);
        else
            throw new IllegalArgumentException("Product is not in stock, please try again later");
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByOrderNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .map(this::mapToResponse)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with orderNumber: " + orderNumber));
    }

    private OrderLineItems mapToEntity(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderLineItemsDto> lineItemsDtos = order.getOrderLineItemsList().stream()
                .map(this::mapToDto)
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .orderLineItemsDtoList(lineItemsDtos)
                .build();
    }

    private OrderLineItemsDto mapToDto(OrderLineItems orderLineItems) {
        OrderLineItemsDto dto = new OrderLineItemsDto();
        dto.setId(orderLineItems.getId());
        dto.setSkuCode(orderLineItems.getSkuCode());
        dto.setPrice(orderLineItems.getPrice());
        dto.setQuantity(orderLineItems.getQuantity());
        return dto;
    }
}
