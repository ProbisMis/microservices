package com.demo.programming.order_service.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.demo.programming.exceptions.InsufficientStockException;
import com.demo.programming.exceptions.ResourceNotFoundException;
import com.demo.programming.order_service.client.InventoryClient;
import com.demo.programming.order_service.dto.InventoryResponse;
import com.demo.programming.order_service.dto.OrderLineItemsDto;
import com.demo.programming.order_service.dto.OrderRequest;
import com.demo.programming.order_service.dto.OrderResponse;
import com.demo.programming.order_service.model.Order;
import com.demo.programming.order_service.model.OrderLineItems;
import com.demo.programming.order_service.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;

    @Transactional
    public OrderResponse placeOrder(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        List<OrderLineItems> lineItems = orderRequest.getOrderLineItemsDtoList()
            .stream()
            .map(this::mapToEntity)
            .toList();
        order.getOrderLineItemsList().addAll(lineItems);

        List<String> skuCodes = lineItems.stream()
            .map(OrderLineItems::getSkuCode)
            .toList();

        log.info("Checking inventory for SKU codes: {}", skuCodes);
        List<InventoryResponse> inventoryResponses = inventoryClient.isInStock(skuCodes);

        if (inventoryResponses == null || inventoryResponses.isEmpty()) {
            log.warn("Inventory service returned empty response for SKU codes: {}", skuCodes);
            throw new InsufficientStockException(skuCodes);
        }

        boolean allInStock = inventoryResponses.stream().allMatch(InventoryResponse::isInStock);
        if (!allInStock) {
            List<String> outOfStockSkuCodes = inventoryResponses.stream()
                    .filter(r -> !r.isInStock())
                    .map(InventoryResponse::getSkuCode)
                    .toList();
            log.warn("Items out of stock: {}", outOfStockSkuCodes);
            throw new InsufficientStockException(outOfStockSkuCodes);
        }

        Order savedOrder = orderRepository.save(order);
        log.info("Order placed successfully with order number: {}", savedOrder.getOrderNumber());
        return mapToResponse(savedOrder);
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
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));
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
