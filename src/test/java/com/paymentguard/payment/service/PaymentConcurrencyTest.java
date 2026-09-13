package com.paymentguard.payment.service;

import com.paymentguard.order.entity.Order;
import com.paymentguard.order.entity.OrderStatus;
import com.paymentguard.order.repository.OrderRepository;
import com.paymentguard.payment.entity.Payment;
import com.paymentguard.payment.entity.PaymentMethod;
import com.paymentguard.payment.entity.PaymentStatus;
import com.paymentguard.payment.repository.PaymentRepository;
import com.paymentguard.payment.service.PaymentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class PaymentConcurrencyTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private com.paymentguard.payment.service.PaymentService paymentService;

    @MockitoBean
    private PaymentProvider paymentProvider;

    private Long orderId;

    @BeforeEach
    @Transactional
    void setup() {

        // Clean payments created by previous test runs
        paymentRepository.deleteAll();

        // Create a fresh order
        Order order = new Order();

        order.setUserId(1L);
        order.setAmount(new BigDecimal("500.00"));
        order.setStatus(OrderStatus.CREATED);

        order = orderRepository.saveAndFlush(order);

        orderId = order.getId();
    }

    @Test
    void shouldAllowOnlyOnePaymentWhenMultipleRequestsArriveConcurrently()
            throws Exception {

        // Provider succeeds
        when(paymentProvider.charge(anyLong(), any(BigDecimal.class)))
                .thenAnswer(invocation ->
                        new PaymentProvider.ProviderResult(
                                "PROVIDER-" + invocation.getArgument(0),
                                true
                        ));

        int requestCount = 10;

        ExecutorService executor =
                Executors.newFixedThreadPool(requestCount);

        CountDownLatch startGate =
                new CountDownLatch(1);

        List<Future<Result>> futures =
                new ArrayList<>();

        // Fire 10 requests at exactly the same time
        for (int i = 0; i < requestCount; i++) {

            String idempotencyKey = "key-" + i;

            futures.add(
                    executor.submit(() -> {

                        startGate.await();

                        try {

                            var response =
                                    paymentService.create(
                                            1L,
                                            new com.paymentguard.payment.dto.CreatePaymentRequest(
                                                    orderId,
                                                    PaymentMethod.UPI
                                            ),
                                            idempotencyKey
                                    );

                            return new Result(
                                    true,
                                    response.status(),
                                    null
                            );

                        } catch (Exception e) {

                            return new Result(
                                    false,
                                    null,
                                    e.getMessage()
                            );
                        }
                    })
            );
        }

        // Release all threads simultaneously
        startGate.countDown();

        List<Result> results = new ArrayList<>();

        for (Future<Result> future : futures) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }

        executor.shutdown();

        // Count successful requests
        long successCount =
                results.stream()
                        .filter(Result::success)
                        .count();

        // Count rejected requests
        long rejectedCount =
                results.stream()
                        .filter(result ->
                                !result.success())
                        .count();

        // Exactly one request should succeed
        assertEquals(
                1,
                successCount,
                "Exactly one payment request should succeed"
        );

        // Remaining requests should be rejected
        assertEquals(
                requestCount - 1,
                rejectedCount
        );

        // Database must contain exactly ONE payment
        List<Payment> payments =
                paymentRepository.findAll();

        assertEquals(
                1,
                payments.size(),
                "Database must contain exactly one payment"
        );

        Payment payment = payments.get(0);

        assertEquals(
                orderId,
                payment.getOrderId()
        );

        assertEquals(
                PaymentStatus.SUCCESS,
                payment.getStatus()
        );

        // Provider must be charged exactly once
        verify(paymentProvider, times(1))
                .charge(
                        anyLong(),
                        any(BigDecimal.class)
                );
    }

    private record Result(
            boolean success,
            PaymentStatus status,
            String error
    ) {
    }
}