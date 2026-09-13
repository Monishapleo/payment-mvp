package com.paymentguard.payment.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MockPaymentProviderTest {

    private final MockPaymentProvider provider =
            new MockPaymentProvider();

    @Test
    void shouldReturnSameTransactionForRepeatedCharge() {

        PaymentProvider.ProviderResult first =
                provider.charge(
                        100L,
                        new BigDecimal("500.00")
                );

        PaymentProvider.ProviderResult second =
                provider.charge(
                        100L,
                        new BigDecimal("500.00")
                );

        assertEquals(
                first.reference(),
                second.reference()
        );

        assertEquals(
                first.success(),
                second.success()
        );
    }

    @Test
    void shouldCreateDifferentTransactionForDifferentPayment() {

        PaymentProvider.ProviderResult first =
                provider.charge(
                        100L,
                        new BigDecimal("500.00")
                );

        PaymentProvider.ProviderResult second =
                provider.charge(
                        101L,
                        new BigDecimal("500.00")
                );

        assertEquals(
                "PROVIDER-100",
                first.reference()
        );

        assertEquals(
                "PROVIDER-101",
                second.reference()
        );
    }

    @Test
    void shouldReturnExistingTransactionStatus() {

        provider.charge(
                100L,
                new BigDecimal("500.00")
        );

        PaymentProvider.ProviderResult result =
                provider.getStatus(
                        100L,
                        "PROVIDER-100"
                );

        assertEquals(
                "PROVIDER-100",
                result.reference()
        );

        assertEquals(
                true,
                result.success()
        );
    }
}