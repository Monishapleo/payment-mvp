package com.paymentguard.order.controller;

import com.paymentguard.order.dto.*;
import com.paymentguard.order.service.OrderService;
import com.paymentguard.common.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService s) {
        service = s;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@CurrentUser Long userId, @Valid @RequestBody CreateOrderRequest r) {
        return ResponseEntity.status(201).body(service.create(userId, r));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id) {
        return service.response(service.get(id));
    }
}
