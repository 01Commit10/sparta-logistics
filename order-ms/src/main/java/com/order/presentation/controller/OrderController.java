package com.order.presentation.controller;

import com.common.auth.AuthUser;
import com.common.auth.web.CurrentUser;
import com.order.application.dto.OrderResult;
import com.order.application.service.OrderQueryService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;
    private final OrderQueryService orderQueryService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @CurrentUser AuthUser authUser,
            @RequestHeader("Idempotency-Key") @NotNull @Size(max = 120) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {

        OrderResult result = orderService.createOrder(request.toCommand(authUser.id(), idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(result));


    }

    // 주문 단건 조회 (부하 실험 P02: 주문 생성과 같은 커넥션 풀을 쓰는 읽기)
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(OrderResponse.from(orderQueryService.getOrder(orderId)));
    }
}
