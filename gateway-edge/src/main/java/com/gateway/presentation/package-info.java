/**
 * presentation 계층: ServerWebExchange를 다루는 유일한 계층 (GlobalFilter, 오류 응답, fallback).
 * 요청에서 필요한 값만 꺼내 application에 넘기고, 결과를 응답·헤더에 적용한다. 판단하지 않는다.
 * 의존: application
 */
package com.gateway.presentation;
