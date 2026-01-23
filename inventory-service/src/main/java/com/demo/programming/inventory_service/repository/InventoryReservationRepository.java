package com.demo.programming.inventory_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.demo.programming.inventory_service.model.InventoryReservation;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    List<InventoryReservation> findByOrderNumber(String orderNumber);

    void deleteByOrderNumber(String orderNumber);
}
