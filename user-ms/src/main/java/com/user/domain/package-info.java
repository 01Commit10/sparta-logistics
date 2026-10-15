/**
 * domain 계층: 업무 규칙, 불변 조건, Port 인터페이스(UserRepository, TokenStore 등).
 * 허용: JPA 어노테이션, common 예외(공유 커널). 그 외 Spring·Redis·jjwt 의존 금지.
 */
package com.user.domain;
