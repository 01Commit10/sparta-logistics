package com.delivery.infrastructure.persistence;

import com.delivery.domain.entity.Delivery;
import com.delivery.domain.entity.DeliveryRoute;
import com.delivery.domain.repository.DeliverySearchCondition;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class DeliverySpecifications {

    private DeliverySpecifications() {
    }


    static Specification<Delivery> of(DeliverySearchCondition condition) {
        return new Specification<Delivery>() {
            @Override
            public Predicate toPredicate(Root<Delivery> root, @Nullable CriteriaQuery<?> query, CriteriaBuilder cb) {
                List<Predicate> predicates = new ArrayList<>();

                predicates.add(cb.isNull(root.get("deletedAt")));
                if (condition.status() != null) {
                    predicates.add(cb.equal(root.get("status"), condition.status()));
                }
                if (condition.deliveryManagerId() != null) {
                    predicates.add(hasRouteManager(root, query, cb, condition.deliveryManagerId()));
                }
                if (condition.viewerId() != null) {
                    predicates.add(isRelatedTo(root, query, cb, condition.viewerId()));
                }

                return cb.and(predicates.toArray(Predicate[]::new));
            }
        };
    }


    // 구간 중 하나라도 담당자가 userId인 배송
    /*
    exists (
            select r.id from p_delivery_routes r
                    where r.delivery_id = d.id
                    and r.delivery_manager_id = ?
    )
    */
    private static Predicate hasRouteManager(Root<Delivery> root, CriteriaQuery<?> query,
                                             CriteriaBuilder cb, UUID userId) {
        Subquery<UUID> subquery = query.subquery(UUID.class); // 서브쿼리가 UUID를 select한다
        Root<DeliveryRoute> route = subquery.from(DeliveryRoute.class);
        subquery.select(route.get("id"))
                .where(cb.equal(route.get("delivery"), root),
                        cb.equal(route.get("deliveryManagerId"), userId));
        return cb.exists(subquery);
    }

    // 관계자: 수령인, 요청·공급 업체 관리자, 구간 배송 담당자
    /*
    (d.recipient_user_id = ?
    or d.requester_company_manager_id = ?
    or d.supplier_company_manager_id = ?
    or exists (구간 배송 담당자 조건))
    */
    private static Predicate isRelatedTo(Root<Delivery> root, CriteriaQuery<?> query,
                                         CriteriaBuilder cb, UUID userId) {
        return cb.or(
                cb.equal(root.get("recipientUserId"), userId),
                cb.equal(root.get("supplierCompanyManagerId"), userId),
                cb.equal(root.get("requesterCompanyManagerId"), userId),
                hasRouteManager(root, query, cb, userId)
        );
    }
}
