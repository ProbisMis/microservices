package com.demo.programming.inventory_service.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.demo.programming.events.order.OrderLineItemEvent;
import com.demo.programming.exceptions.DuplicateResourceException;
import com.demo.programming.exceptions.ResourceNotFoundException;
import com.demo.programming.inventory_service.dto.InventoryRequest;
import com.demo.programming.inventory_service.dto.InventoryResponse;
import com.demo.programming.inventory_service.kafka.producer.InventoryEventProducer;
import com.demo.programming.inventory_service.model.Inventory;
import com.demo.programming.inventory_service.model.InventoryReservation;
import com.demo.programming.inventory_service.repository.InventoryRepository;
import com.demo.programming.inventory_service.repository.InventoryReservationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final InventoryEventProducer inventoryEventProducer;

    @Transactional(readOnly = true)
    public List<InventoryResponse> isInStock(List<String> skuCodes) {
        return inventoryRepository.findBySkuCodeIn(skuCodes).stream()
                .map(inventory -> InventoryResponse.builder()
                        .skuCode(inventory.getSkuCode())
                        .isInStock(inventory.getQuantity() > 0).build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> getAllInventory() {
        return inventoryRepository.findAll().stream()
                .map(inventory -> InventoryResponse.builder()
                        .skuCode(inventory.getSkuCode())
                        .isInStock(inventory.getQuantity() > 0)
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventoryBySkuCode(String skuCode) {
        Inventory inventory = inventoryRepository.findBySkuCode(skuCode);
        if (inventory == null) {
            throw new ResourceNotFoundException("Inventory", "skuCode", skuCode);
        }
        return InventoryResponse.builder()
                .skuCode(inventory.getSkuCode())
                .isInStock(inventory.getQuantity() > 0)
                .build();
    }

    @Transactional
    public InventoryResponse addInventory(InventoryRequest inventoryRequest) {
        Inventory existingInventory = inventoryRepository.findBySkuCode(inventoryRequest.getSkuCode());
        if (existingInventory != null) {
            throw new DuplicateResourceException("Inventory", "skuCode", inventoryRequest.getSkuCode());
        }

        Inventory inventory = new Inventory();
        inventory.setSkuCode(inventoryRequest.getSkuCode());
        inventory.setQuantity(inventoryRequest.getQuantity());

        Inventory savedInventory = inventoryRepository.save(inventory);
        log.info("Inventory created for skuCode: {}", savedInventory.getSkuCode());

        return InventoryResponse.builder()
                .skuCode(savedInventory.getSkuCode())
                .isInStock(savedInventory.getQuantity() > 0)
                .build();
    }

    @Transactional
    public InventoryResponse updateStock(String skuCode, Integer quantity) {
        Inventory inventory = inventoryRepository.findBySkuCode(skuCode);
        if (inventory == null) {
            throw new ResourceNotFoundException("Inventory", "skuCode", skuCode);
        }

        inventory.setQuantity(quantity);
        Inventory updatedInventory = inventoryRepository.save(inventory);
        log.info("Inventory updated for skuCode: {}, new quantity: {}", skuCode, quantity);

        return InventoryResponse.builder()
                .skuCode(updatedInventory.getSkuCode())
                .isInStock(updatedInventory.getQuantity() > 0)
                .build();
    }

    @Transactional
    public void deleteInventory(String skuCode) {
        Inventory inventory = inventoryRepository.findBySkuCode(skuCode);
        if (inventory == null) {
            throw new ResourceNotFoundException("Inventory", "skuCode", skuCode);
        }
        inventoryRepository.delete(inventory);
        log.info("Inventory deleted for skuCode: {}", skuCode);
    }

    @Transactional
    public void reserveInventory(String orderNumber, List<OrderLineItemEvent> orderLineItems) {
        List<String> failedSkuCodes = new ArrayList<>();

        for (OrderLineItemEvent lineItem : orderLineItems) {
            Inventory inventory = inventoryRepository.findBySkuCode(lineItem.getSkuCode());

            if (inventory == null) {
                log.warn("Inventory not found for skuCode: {}", lineItem.getSkuCode());
                failedSkuCodes.add(lineItem.getSkuCode());
                continue;
            }

            if (inventory.getAvailableQuantity() < lineItem.getQuantity()) {
                log.warn("Insufficient stock for skuCode: {}, available: {}, requested: {}",
                        lineItem.getSkuCode(), inventory.getAvailableQuantity(), lineItem.getQuantity());
                failedSkuCodes.add(lineItem.getSkuCode());
                continue;
            }
        }

        if (!failedSkuCodes.isEmpty()) {
            String reason = "Insufficient inventory for SKUs: " + String.join(", ", failedSkuCodes);
            inventoryEventProducer.publishInventoryFailed(orderNumber, reason, failedSkuCodes);
            return;
        }

        // All items available - create reservations
        for (OrderLineItemEvent lineItem : orderLineItems) {
            Inventory inventory = inventoryRepository.findBySkuCode(lineItem.getSkuCode());

            // Update reserved quantity
            inventory.setReservedQuantity(
                    (inventory.getReservedQuantity() != null ? inventory.getReservedQuantity() : 0)
                            + lineItem.getQuantity());
            inventoryRepository.save(inventory);

            // Create reservation record
            InventoryReservation reservation = new InventoryReservation();
            reservation.setOrderNumber(orderNumber);
            reservation.setSkuCode(lineItem.getSkuCode());
            reservation.setQuantity(lineItem.getQuantity());
            inventoryReservationRepository.save(reservation);

            log.info("Reserved {} units of {} for order {}", lineItem.getQuantity(), lineItem.getSkuCode(),
                    orderNumber);
        }

        inventoryEventProducer.publishInventoryReserved(orderNumber);
    }

    @Transactional
    public void releaseReservation(String orderNumber) {
        List<InventoryReservation> reservations = inventoryReservationRepository.findByOrderNumber(orderNumber);

        for (InventoryReservation reservation : reservations) {
            Inventory inventory = inventoryRepository.findBySkuCode(reservation.getSkuCode());
            if (inventory != null) {
                inventory.setReservedQuantity(
                        Math.max(0, (inventory.getReservedQuantity() != null ? inventory.getReservedQuantity() : 0)
                                - reservation.getQuantity()));
                inventoryRepository.save(inventory);
                log.info("Released {} units of {} for order {}", reservation.getQuantity(), reservation.getSkuCode(),
                        orderNumber);
            }
        }

        inventoryReservationRepository.deleteByOrderNumber(orderNumber);
    }

    @Transactional
    public void confirmReservation(String orderNumber) {
        List<InventoryReservation> reservations = inventoryReservationRepository.findByOrderNumber(orderNumber);

        for (InventoryReservation reservation : reservations) {
            Inventory inventory = inventoryRepository.findBySkuCode(reservation.getSkuCode());
            if (inventory != null) {
                // Deduct from both quantity and reserved
                inventory.setQuantity(inventory.getQuantity() - reservation.getQuantity());
                inventory.setReservedQuantity(
                        Math.max(0, (inventory.getReservedQuantity() != null ? inventory.getReservedQuantity() : 0)
                                - reservation.getQuantity()));
                inventoryRepository.save(inventory);
                log.info("Confirmed reservation: deducted {} units of {} for order {}",
                        reservation.getQuantity(), reservation.getSkuCode(), orderNumber);
            }
        }

        inventoryReservationRepository.deleteByOrderNumber(orderNumber);
    }
}
