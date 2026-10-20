package com.order.presentation.controller;

import com.common.auth.AuthUser;
import com.common.auth.web.CurrentUser;
import com.order.application.dto.OrderResult;
import com.order.application.service.OrderService;
import com.order.presentation.dto.CreateOrderRequest;
import com.order.presentation.dto.OrderResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @CurrentUser AuthUser authUser,
            @RequestHeader("Idempotency-Key") @NotNull @Size(max = 120) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {

        OrderResult result = orderService.createOrder(request.toCommand(authUser.id(), idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(result));
    }

}
