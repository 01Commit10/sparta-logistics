package com.hub.hub.infrastructure.persistence;

import com.hub.hub.domain.entity.Hub;
import com.hub.hub.domain.repository.HubRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class HubRepositoryImpl implements HubRepository {
    private final HubJpaRepository hubJpaRepository;

    public HubRepositoryImpl(HubJpaRepository hubJpaRepository) {
        this.hubJpaRepository = hubJpaRepository;
    }

    @Override
    public List<Hub> findAll() {
        return hubJpaRepository.findAllByOrderByIdAsc();
    }

    @Override
    public Optional<Hub> findById(UUID id) {
        return hubJpaRepository.findById(id);
    }

}
