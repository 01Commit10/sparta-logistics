package com.order.presentation.controller;

import com.common.auth.AuthUser;
import com.common.auth.web.CurrentUser;
import com.common.page.PagePolicy;
import com.common.page.PageResponse;
import com.order.application.dto.OrderResult;
import com.order.application.service.OrderCancelService;
import com.order.application.service.OrderDeleteService;
import com.order.application.service.OrderQueryService;
import com.order.application.service.OrderCreateFacade;
import com.order.presentation.dto.CreateOrderRequest;
import com.order.presentation.dto.OrderResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderCreateFacade orderCreateFacade;
    private final OrderQueryService orderQueryService;
    private final OrderCancelService orderCancelService;
    private final OrderDeleteService orderDeleteService;

    // cratedAt은 PagePolicy가 기본으로 허용, 추가로 총액 정렬만 허용
    private static final PagePolicy PAGE_POLICY = PagePolicy.of("totalAmount");

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @CurrentUser AuthUser authUser,
            @RequestHeader("Idempotency-Key") @NotNull @Size(max = 120) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {

        OrderResult result = orderCreateFacade.createOrder(request.toCommand(authUser.id(), idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(result));
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@CurrentUser AuthUser authUser, @PathVariable UUID orderId) {
        OrderResult result = orderQueryService.getOrder(orderId, authUser.id(), authUser.isMaster());
        return OrderResponse.from(result);
    }

    @GetMapping
    public PageResponse<OrderResponse> getOrders(@CurrentUser AuthUser authUser, Pageable pageable) {
        Page<OrderResult> orders = orderQueryService.getOrders(
                authUser.id(), authUser.isMaster(), PAGE_POLICY.apply(pageable));
        return PageResponse.from(orders, OrderResponse::from);
    }

    @PostMapping("/{orderId}/cancel")
    public OrderResponse cancelOrder(@CurrentUser AuthUser authUser, @PathVariable UUID orderId) {
        OrderResult result = orderCancelService.cancelOrder(orderId, authUser.id(), authUser.isMaster());
        return OrderResponse.from(result);
    }

    @DeleteMapping("/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOrder(@CurrentUser AuthUser authUser, @PathVariable UUID orderId) {
        authUser.requireMaster();   // MASTER가 아니면 403 (common)
        orderDeleteService.deleteOrder(orderId, authUser.id());
    }

}
