package com.petdonation.api;

import com.petdonation.service.PayMongoWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PayMongoWebhookController {
    private final PayMongoWebhookService webhookService;

    public PayMongoWebhookController(PayMongoWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/webhooks/paymongo")
    public ResponseEntity<Void> receive(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "Paymongo-Signature", required = false) String signature) {
        webhookService.accept(rawBody, signature);
        return ResponseEntity.ok().build();
    }
}
