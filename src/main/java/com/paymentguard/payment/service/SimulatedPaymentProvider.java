package com.paymentguard.payment.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class SimulatedPaymentProvider implements PaymentProvider {
    public ProviderResult charge(Long paymentId, BigDecimal amount) {
        return new ProviderResult("PROV-" + UUID.randomUUID(), true);
    }

    public ProviderResult getStatus(String ref) {
        return new ProviderResult(ref, true);
    }
}
