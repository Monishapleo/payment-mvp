package com.paymentguard.payment.controller;

import com.paymentguard.payment.dto.*;
import com.paymentguard.payment.service.PaymentService;
import com.paymentguard.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final PaymentService service;

    public PaymentController(PaymentService s) {
        service = s;
    }


    @PostMapping
    public ResponseEntity<PaymentResponse> create(@CurrentUser Long userId, @RequestHeader(value = "Idempotency-Key", required = false) String key, @Valid @RequestBody CreatePaymentRequest r) {
        PaymentResponse x = service.create(userId, r, key);
        return ResponseEntity.status(x.status() == com.paymentguard.payment.entity.PaymentStatus.PENDING ? 202 : 201).body(x);
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/webhook")
    public PaymentResponse webhook(@Valid @RequestBody ProviderWebhookRequest r) {
        return service.webhook(r);
    }
}
