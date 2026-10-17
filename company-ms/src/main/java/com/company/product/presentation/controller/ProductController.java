package com.company.product.presentation.controller;

import com.common.auth.AuthUser;
import com.common.auth.web.CurrentUser;
import com.common.page.PagePolicy;
import com.common.page.PageQuery;
import com.common.page.PageResponse;
import com.company.product.application.dto.DeleteProductCommand;
import com.company.product.application.service.ProductService;
import com.company.product.presentation.dto.CreateProductRequest;
import com.company.product.presentation.dto.ProductResponse;
import com.company.product.presentation.dto.UpdateProductRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ProductController {

    private final ProductService productService;
    private static final PagePolicy PRODUCTS_BY_COMPANY_PAGE_POLICY = PagePolicy.of("name", "unitPrice");

    // MASTER·업체 관리자 본인 (본인 여부는 업체 데이터를 봐야 해서 ProductService가 판단)
    @PostMapping("/companies/{companyId}/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(@CurrentUser AuthUser authUser,
                                         @PathVariable UUID companyId,
                                         @Valid @RequestBody CreateProductRequest request) {
        return ProductResponse.from(productService.createProduct(
                request.toCommand(companyId, authUser.id(), authUser.isMaster())));
    }

    @GetMapping("/products/{productId}")
    public ProductResponse getProduct(@PathVariable UUID productId) {
        return ProductResponse.from(productService.getProduct(productId));
    }

    // MASTER·업체 관리자 본인. 보낸 필드만 수정 (PATCH)
    @PatchMapping("/products/{productId}")
    public ProductResponse updateProduct(@CurrentUser AuthUser authUser,
                                         @PathVariable UUID productId,
                                         @Valid @RequestBody UpdateProductRequest request) {
        return ProductResponse.from(productService.updateProduct(
                request.toCommand(productId, authUser.id(), authUser.isMaster())));
    }

    // MASTER·업체 관리자 본인. 논리 삭제
    @DeleteMapping("/products/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@CurrentUser AuthUser authUser, @PathVariable UUID productId) {
        productService.deleteProduct(new DeleteProductCommand(productId, authUser.id(), authUser.isMaster()));
    }

    @GetMapping("/companies/{companyId}/products")
    public PageResponse<ProductResponse> getProductsByCompany(@CurrentUser AuthUser authUser,
                                                              @PathVariable UUID companyId,
                                                              Pageable pageable) {
        PageQuery query = PRODUCTS_BY_COMPANY_PAGE_POLICY.toQuery(pageable);
        return PageResponse.from(productService.getProductsByCompany(companyId, query), query, ProductResponse::from);
    }

}
