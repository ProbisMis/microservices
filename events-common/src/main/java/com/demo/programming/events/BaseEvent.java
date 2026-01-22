package com.demo.programming.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class BaseEvent {
    private String eventId;
    private String eventType;
    private Instant timestamp;
    private String correlationId;

    protected void initializeBase(String eventType) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.timestamp = Instant.now();
    }
}
