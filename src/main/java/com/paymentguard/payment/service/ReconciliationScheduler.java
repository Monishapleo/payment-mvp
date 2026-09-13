package com.paymentguard.payment.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReconciliationScheduler {

    private final ReconciliationService reconciliationService;

    @Scheduled(fixedDelay = 60_000)
    public void run() {
        reconciliationService.reconcile();
    }
}