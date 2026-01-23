package com.demo.programming.order_service.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.demo.programming.events.order.OrderLineItemEvent;
import com.demo.programming.order_service.client.InventoryClient;
import com.demo.programming.order_service.dto.InventoryResponse;
import com.demo.programming.order_service.dto.OrderLineItemsDto;
import com.demo.programming.order_service.dto.OrderRequest;
import com.demo.programming.order_service.dto.OrderResponse;
import com.demo.programming.order_service.kafka.producer.OrderEventProducer;
import com.demo.programming.order_service.model.Order;
import com.demo.programming.order_service.model.OrderLineItems;
import com.demo.programming.order_service.model.OrderStatus;
import com.demo.programming.order_service.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final OrderEventProducer orderEventProducer;

    /**
     * Synchronous order placement (fallback method using Feign client)
     */
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

        if (result != null && !result.isEmpty() && result.stream().allMatch(InventoryResponse::isInStock)) {
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);
        } else {
            throw new IllegalArgumentException("Product is not in stock, please try again later");
        }
    }

    /**
     * Asynchronous order placement using Kafka events
     */
    @Transactional
    public OrderResponse placeOrderAsync(OrderRequest orderRequest) {
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setStatus(OrderStatus.PENDING);

        orderRequest.getOrderLineItemsDtoList()
            .stream()
            .map(this::mapToEntity)
            .forEach(orderLineItems -> order.getOrderLineItemsList().add(orderLineItems));

        Order savedOrder = orderRepository.save(order);
        log.info("Order {} saved with status PENDING", savedOrder.getOrderNumber());

        // Publish event to Kafka
        List<OrderLineItemEvent> orderLineItemEvents = savedOrder.getOrderLineItemsList().stream()
                .map(this::mapToOrderLineItemEvent)
                .toList();

        orderEventProducer.publishOrderPlaced(savedOrder.getOrderNumber(), orderLineItemEvents);

        return mapToResponse(savedOrder);
    }

    /**
     * Confirm order after inventory reservation success
     */
    @Transactional
    public void confirmOrder(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderNumber));

        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        log.info("Order {} confirmed", orderNumber);

        orderEventProducer.publishOrderConfirmed(orderNumber);
    }

    /**
     * Reject order after inventory reservation failure
     */
    @Transactional
    public void rejectOrder(String orderNumber, String reason) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderNumber));

        order.setStatus(OrderStatus.REJECTED);
        order.setFailureReason(reason);
        orderRepository.save(order);
        log.info("Order {} rejected: {}", orderNumber, reason);

        orderEventProducer.publishOrderRejected(orderNumber, reason);
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

    private OrderLineItemEvent mapToOrderLineItemEvent(OrderLineItems orderLineItems) {
        return OrderLineItemEvent.builder()
                .skuCode(orderLineItems.getSkuCode())
                .price(orderLineItems.getPrice() != null ? BigDecimal.valueOf(orderLineItems.getPrice()) : null)
                .quantity(orderLineItems.getQuantity())
                .build();
    }

    private OrderResponse mapToResponse(Order order) {
        List<OrderLineItemsDto> lineItemsDtos = order.getOrderLineItemsList().stream()
                .map(this::mapToDto)
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus() != null ? order.getStatus().name() : null)
                .failureReason(order.getFailureReason())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
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
