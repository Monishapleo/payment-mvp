package com.paymentguard.payment.service;

import com.paymentguard.order.entity.Order;
import com.paymentguard.order.entity.OrderStatus;
import com.paymentguard.order.repository.OrderRepository;
import com.paymentguard.payment.entity.Payment;
import com.paymentguard.payment.entity.PaymentMethod;
import com.paymentguard.payment.entity.PaymentStatus;
import com.paymentguard.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@SpringBootTest
class PaymentConcurrencyTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentService paymentService;

    @MockitoBean
    private PaymentProvider paymentProvider;


    @BeforeEach
    void cleanDatabase() {
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
    }


    @Test
    void shouldAllowOnlyOnePaymentWhenDifferentKeysAreUsedConcurrently()
            throws Exception {

        Order order = new Order();
        order.setUserId(1L);
        order.setAmount(new BigDecimal("500.00"));
        order.setStatus(OrderStatus.CREATED);

        order = orderRepository.save(order);
        final Long orderId = order.getId();

        when(paymentProvider.charge(
                anyLong(),
                any(BigDecimal.class)
        )).thenReturn(
                new PaymentProvider.ProviderResult(
                        "PROVIDER-123",
                        true
                )
        );

        int threadCount = 10;

        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        List<Future<?>> futures =
                new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {

            String idempotencyKey =
                    "different-key-" + i;

            futures.add(
                    executor.submit(() -> {

                        try {
                            startLatch.await();

                            paymentService.create(
                                    1L,
                                    new com.paymentguard.payment.dto.CreatePaymentRequest(
                                            orderId,
                                            PaymentMethod.UPI
                                    ),
                                    idempotencyKey
                            );

                        } catch (Exception ignored) {
                            // Expected for rejected concurrent requests.
                        }

                        return null;
                    })
            );
        }

        startLatch.countDown();

        for (Future<?> future : futures) {
            future.get();
        }

        executor.shutdown();

        List<Payment> payments =
                paymentRepository.findAll();

        assertEquals(
                1,
                payments.size()
        );

        Payment payment = payments.get(0);

        assertEquals(
                PaymentStatus.SUCCESS,
                payment.getStatus()
        );

        verify(
                paymentProvider,
                times(1)
        ).charge(
                anyLong(),
                any(BigDecimal.class)
        );
    }


    @Test
    void shouldChargeOnlyOnceWhenSameKeyIsUsedConcurrently()
            throws Exception {

        Order order = new Order();
        order.setUserId(1L);
        order.setAmount(new BigDecimal("500.00"));
        order.setStatus(OrderStatus.CREATED);

        order = orderRepository.save(order);
        final Long orderId = order.getId();

        when(paymentProvider.charge(
                anyLong(),
                any(BigDecimal.class)
        )).thenReturn(
                new PaymentProvider.ProviderResult(
                        "PROVIDER-SAME-KEY",
                        true
                )
        );

        int threadCount = 10;

        String idempotencyKey =
                "same-payment-key";

        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        List<Future<?>> futures =
                new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {

            futures.add(
                    executor.submit(() -> {

                        try {
                            startLatch.await();

                            paymentService.create(
                                    1L,
                                    new com.paymentguard.payment.dto.CreatePaymentRequest(
                                            orderId,
                                            PaymentMethod.UPI
                                    ),
                                    idempotencyKey
                            );

                        } catch (Exception ignored) {
                            // Concurrent requests may race.
                        }

                        return null;
                    })
            );
        }

        startLatch.countDown();

        for (Future<?> future : futures) {
            future.get();
        }

        executor.shutdown();

        List<Payment> payments =
                paymentRepository.findAll();

        /*
         * Same idempotency key must produce
         * exactly one payment record.
         */
        assertEquals(
                1,
                payments.size()
        );

        Payment payment = payments.get(0);

        assertEquals(
                idempotencyKey,
                payment.getIdempotencyKey()
        );

        assertEquals(
                PaymentStatus.SUCCESS,
                payment.getStatus()
        );

        /*
         * Most important assertion:
         *
         * Provider must be called exactly once.
         */
        verify(
                paymentProvider,
                times(1)
        ).charge(
                anyLong(),
                any(BigDecimal.class)
        );
    }
}