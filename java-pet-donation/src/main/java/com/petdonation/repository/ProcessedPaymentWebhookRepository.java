package com.petdonation.repository;

import com.petdonation.model.ProcessedPaymentWebhook;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedPaymentWebhookRepository extends JpaRepository<ProcessedPaymentWebhook, String> { }
