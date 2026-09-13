package com.paymentguard.payment.service;

import com.paymentguard.payment.entity.Payment;
import com.paymentguard.payment.entity.PaymentStatus;
import com.paymentguard.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final PaymentProvider paymentProvider;

    public void reconcile() {

        List<Payment> payments =
                paymentRepository.findAll()
                        .stream()
                        .filter(payment ->
                                payment.getStatus() == PaymentStatus.IN_PROGRESS)
                        .toList();

        for (Payment payment : payments) {

            if (payment.getProviderReference() == null) {
                continue;
            }

            PaymentProvider.ProviderResult result =
                    paymentProvider.getStatus(
                            payment.getProviderReference()
                    );

            paymentService.completePayment(
                    payment.getId(),
                    result.success(),
                    result.reference()
            );
        }
    }
}