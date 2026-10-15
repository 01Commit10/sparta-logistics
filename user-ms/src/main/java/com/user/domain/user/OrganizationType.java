package com.user.domain.user;

/**
 * 소속 조직 유형. HQ(본사)는 업체·허브에 속하지 않는 관리자용이며 MASTER만 가질 수 있고 연결할 조직 ID가 없다.
 */
public enum OrganizationType {
    COMPANY,
    HUB,
    HQ;

    /** 실제 조직(업체·허브) ID를 연결할 수 있는 유형인지 */
    public boolean isLinkable() {
        return this != HQ;
    }
}
