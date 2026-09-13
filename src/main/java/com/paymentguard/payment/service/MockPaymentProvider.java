package com.paymentguard.payment.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MockPaymentProvider implements PaymentProvider {

    private final Map<Long, ProviderResult> transactions =
            new ConcurrentHashMap<>();

    @Override
    public ProviderResult charge(
            Long paymentId,
            BigDecimal amount
    ) {

        /*
         * Provider-level idempotency.
         *
         * Same paymentId = same provider transaction.
         */
        return transactions.computeIfAbsent(
                paymentId,
                id -> new ProviderResult(
                        "PROVIDER-" + id,
                        true
                )
        );
    }

    @Override
    public ProviderResult getStatus(
            Long paymentId,
            String providerReference
    ) {

        return transactions.getOrDefault(
                paymentId,
                new ProviderResult(
                        providerReference,
                        false
                )
        );
    }
}