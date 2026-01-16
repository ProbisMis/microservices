package com.demo.programming.order_service.service;

import java.util.Arrays;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import com.demo.programming.order_service.dto.InventoryResponse;
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
    private final WebClient.Builder webClientBuilder;

    @Transactional
    public void placeOrder(OrderRequest orderRequest) {
        // Implementation for placing an order
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());

        orderRequest.getOrderLineItemsDtoList()
            .stream()
            .map(this::mapToDto)
            .forEach(orderLineItems -> order.getOrderLineItemsList().add(orderLineItems));
        
        
        //Check inventory service here!
        //By default asynchronous, but we are making it synchronous by using block() method
        //TODO: Change URL to save memory address or use service discovery
        InventoryResponse[] result = webClientBuilder.build().get()
            .uri("http://localhost:8082/api/inventory",
                 uriBuilder -> uriBuilder.queryParam("skuCode",
                     order.getOrderLineItemsList()
                         .stream()
                         .map(OrderLineItems::getSkuCode)
                         .toList()
                 ).build()
            )
            .retrieve()
            .bodyToMono(InventoryResponse[].class)
            .block();
        
        if (result.length > 0 && Arrays.stream(result).allMatch(InventoryResponse::isInStock)) 
            orderRepository.save(order);
        else
            throw new IllegalArgumentException("Product is not in stock, please try again later");
    }

    private OrderLineItems mapToDto(OrderLineItemsDto orderLineItemsDto) {
        OrderLineItems orderLineItems = new OrderLineItems();
        orderLineItems.setPrice(orderLineItemsDto.getPrice());
        orderLineItems.setQuantity(orderLineItemsDto.getQuantity());
        orderLineItems.setSkuCode(orderLineItemsDto.getSkuCode());
        return orderLineItems;
    }

}
