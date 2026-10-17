package com.company.fake;

import com.company.product.domain.entity.Product;
import com.company.product.domain.repository.ProductRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 테스트용 메모리 저장소. ID는 JPA(@GeneratedValue)가 만드는 것을 흉내 내 저장할 때 채운다. */
public class FakeProductRepository implements ProductRepository {

    private final Map<UUID, Product> store = new LinkedHashMap<>();

    @Override
    public Product save(Product product) {
        if (product.getId() == null) {
            ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
        }
        store.put(product.getId(), product);
        return product;
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    public Collection<Product> all() {
        return store.values();
    }
}
