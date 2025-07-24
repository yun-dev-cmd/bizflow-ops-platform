package com.company.batchmonitor.domain;

public enum Role {
    ROLE_ADMIN,      // 전체 관리자 (재처리 기동, 파일 관리, 모니터링 전체 가능)
    ROLE_OPERATOR,   // 운영자 (모니터링 및 수동 재처리 가능)
    ROLE_USER        // 일반 사용자 (단순 모니터링 조회만 가능)
}
