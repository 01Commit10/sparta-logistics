package com.delivery.presentation.controller;

import com.delivery.application.dto.command.DeliveryCreateCommand;
import com.delivery.application.dto.result.DeliveryCreateResult;
import com.delivery.application.service.DeliveryCommandService;
import com.delivery.presentation.dto.request.DeliveryCreateRequest;
import com.delivery.presentation.dto.response.DeliveryCreateResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
public class InternalDeliveryController {

    private final DeliveryCommandService deliveryCommandService;


    @PostMapping("/deliveries")
    public ResponseEntity<DeliveryCreateResponse> createDelivery(@RequestBody @Valid DeliveryCreateRequest request) {
        DeliveryCreateCommand command = request.toCommand();
        DeliveryCreateResult result = deliveryCommandService.createDelivery(command);
        DeliveryCreateResponse response = DeliveryCreateResponse.from(result);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
