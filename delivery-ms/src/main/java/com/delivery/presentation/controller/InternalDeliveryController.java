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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
public class InternalDeliveryController {

    private final DeliveryCommandService deliveryCommandService;


    @PostMapping("/deliveries")
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryCreateResponse createDelivery(@RequestBody @Valid DeliveryCreateRequest request) {
        DeliveryCreateCommand command = request.toCommand();
        DeliveryCreateResult result = deliveryCommandService.createDelivery(command);

        return DeliveryCreateResponse.from(result);
    }
}
