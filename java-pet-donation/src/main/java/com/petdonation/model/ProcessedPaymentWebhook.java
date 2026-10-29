package com.petdonation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "processed_payment_webhooks")
public class ProcessedPaymentWebhook {
    @Id
    @Column(name = "event_id", length = 100)
    private String eventId;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    protected ProcessedPaymentWebhook() { }

    public ProcessedPaymentWebhook(String eventId, LocalDateTime receivedAt) {
        this.eventId = eventId;
        this.receivedAt = receivedAt;
    }
}
