package com.company.product.domain.repository;

import com.company.product.domain.entity.Product;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(UUID id);
}
