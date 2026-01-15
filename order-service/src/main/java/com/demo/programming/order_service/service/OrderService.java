package com.demo.programming.order_service.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.demo.programming.order_service.dto.OrderLineItemsDto;
import com.demo.programming.order_service.dto.OrderRequest;
import com.demo.programming.order_service.model.Order;
import com.demo.programming.order_service.model.OrderLineItems;
import com.demo.programming.order_service.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    public void placeOrder(OrderRequest orderRequest) {
        // Implementation for placing an order
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        orderRequest.getOrderLineItemsDtoList()
            .stream()
            .map(this::mapToDto)
            .forEach(orderLineItems -> order.getOrderLineItemsList().add(orderLineItems));

        orderRepository.save(order);
    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }

}
