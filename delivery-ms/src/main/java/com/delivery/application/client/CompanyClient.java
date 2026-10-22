package com.delivery.application.client;

import com.delivery.domain.vo.CompanyInfo;

import java.util.Optional;
import java.util.UUID;

public interface CompanyClient {

    Optional<CompanyInfo> findCompany(UUID companyId);
}
