/**
 * domain 계층: 경로 정책, 토큰 claim, 오류 코드, Port 인터페이스.
 * Spring·jjwt·Redis에 의존하지 않는다. Port 반환 타입으로 reactor Mono만 허용한다.
 * 의존: 없음 (JDK, common 공유 커널)
 */
package com.gateway.domain;
