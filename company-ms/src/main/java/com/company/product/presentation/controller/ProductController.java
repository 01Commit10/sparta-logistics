package com.company.product.presentation.controller;

import com.common.auth.AuthUser;
import com.common.auth.web.CurrentUser;
import com.company.product.application.service.ProductService;
import com.company.product.presentation.dto.CreateProductRequest;
import com.company.product.presentation.dto.ProductResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ProductController {

    private final ProductService productService;

    // MASTER·업체 관리자 본인 (본인 여부는 업체 데이터를 봐야 해서 ProductService가 판단)
    @PostMapping("/companies/{companyId}/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@CurrentUser AuthUser authUser,
                                         @PathVariable UUID companyId,
                                         @Valid @RequestBody CreateProductRequest request) {
        return ProductResponse.from(productService.createProduct(
                request.toCommand(companyId, authUser.id(), authUser.isMaster())));
    }
}
