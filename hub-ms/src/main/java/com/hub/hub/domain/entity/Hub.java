package com.hub.hub.domain.entity;

import com.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor
@Table(name="p_hubs", schema = "hub")
public class Hub extends BaseEntity {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length=100)
    private String name;

    @Column(nullable = false, length = 255)
    private String address;
}
