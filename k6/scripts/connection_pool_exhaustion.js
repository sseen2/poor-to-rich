/**
 * 커넥션 풀 고갈 재현 테스트
 *
 * ConnectionPoolExhaustionTest (JUnit)와 동일한 시나리오를 k6로 구현
 *
 * 시나리오:
 *   1. POST /admin/ranking/trigger  → 랭킹 스케줄러 비동기 시작
 *   2. 1초 후, 3종류 API를 동시에 60건(각 20건) 호출
 *      - POST /user/exists/username  (DB 조회 — 커넥션 필요)
 *      - POST /user/exists/nickname  (DB 조회 — 커넥션 필요)
 *      - GET  /auth/health           (DB 미사용 — 비교 기준)
 *
 * 기대 결과:
 *   - DB 조회 API: 커넥션 타임아웃으로 실패 발생 (SQLTransientConnectionException)
 *   - health check: 항상 성공
 *
 * 실행 방법:
 *   docker-compose --profile k6 run --rm k6
 *
 * JWT 인증이 필요한 경우:
 *   K6_JWT_TOKEN=<token> docker-compose --profile k6 run --rm k6
 */

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JWT_TOKEN = __ENV.JWT_TOKEN || '';

// ── 커스텀 메트릭 ──────────────────────────────────────────────
const usernameSuccess = new Counter('username_check_success');
const usernameFail    = new Counter('username_check_fail');
const nicknameSuccess = new Counter('nickname_check_success');
const nicknameFail    = new Counter('nickname_check_fail');
const healthSuccess   = new Counter('health_check_success');
const healthFail      = new Counter('health_check_fail');

// ── 시나리오 설정 ──────────────────────────────────────────────
export const options = {
    scenarios: {
        // Phase 1: 스케줄러 트리거 (테스트 시작과 동시에 1회)
        trigger_scheduler: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 1,
            maxDuration: '30s',
            exec: 'triggerScheduler',
            startTime: '0s',
        },
        // Phase 2: DB 조회 API — username (1초 후 20 VU 동시 실행)
        username_check: {
            executor: 'shared-iterations',
            vus: 20,
            iterations: 20,
            maxDuration: '60s',
            exec: 'checkUsername',
            startTime: '1s',
        },
        // Phase 2: DB 조회 API — nickname (1초 후 20 VU 동시 실행)
        nickname_check: {
            executor: 'shared-iterations',
            vus: 20,
            iterations: 20,
            maxDuration: '60s',
            exec: 'checkNickname',
            startTime: '1s',
        },
        // Phase 2: 비-DB API — health check (1초 후 20 VU 동시 실행, 비교 기준)
        health_check: {
            executor: 'shared-iterations',
            vus: 20,
            iterations: 20,
            maxDuration: '60s',
            exec: 'checkHealth',
            startTime: '1s',
        },
    },
    thresholds: {
        // health check는 20건 모두 성공해야 함 (비-DB API는 영향 없음)
        'health_check_success': ['count==20'],
        // DB 조회 API는 최소 1건 이상 실패해야 커넥션 풀 고갈 증명
        'username_check_fail': ['count>0'],
        'nickname_check_fail': ['count>0'],
    },
};

// ── 테스트 시작 시간 기록 ──────────────────────────────────────
export function setup() {
    return { testStartTime: new Date().toISOString() };
}

// ── 테스트 종료 후 데이터 정리 ─────────────────────────────────
export function teardown(data) {
    const encodedSince = encodeURIComponent(data.testStartTime);
    const res = http.del(`${BASE_URL}/ranking/test/data?since=${encodedSince}`, null, {
        timeout: '15s',
    });

    if (res.status === 200) {
        console.log(`[cleanup] 테스트 데이터 정리 완료 (since: ${data.testStartTime})`);
    } else {
        console.error(`[cleanup] 테스트 데이터 정리 실패 — 상태코드: ${res.status}, 응답: ${res.body}`);
    }
}

// ── Phase 1: 스케줄러 트리거 ───────────────────────────────────
export function triggerScheduler() {
    const headers = { 'Content-Type': 'application/json' };
    if (JWT_TOKEN) {
        headers['Authorization'] = `Bearer ${JWT_TOKEN}`;
    }

    const res = http.post(`${BASE_URL}/ranking/test/trigger`, null, {
        headers,
        timeout: '10s',
    });

    const ok = check(res, {
        '스케줄러 트리거 성공 (200)': (r) => r.status === 200,
    });

    if (ok) {
        console.log(`[스케줄러 트리거] 성공 — 상태코드: ${res.status}`);
    } else {
        console.error(`[스케줄러 트리거] 실패 — 상태코드: ${res.status}, 응답: ${res.body}`);
    }
}

// ── Phase 2: DB 조회 API — username ───────────────────────────
export function checkUsername() {
    const body = JSON.stringify({ username: `k6user${__VU}` });
    const res = http.post(`${BASE_URL}/user/exists/username`, body, {
        headers: { 'Content-Type': 'application/json' },
        timeout: '5s',
    });

    if (res.status >= 200 && res.status < 300) {
        usernameSuccess.add(1);
    } else {
        usernameFail.add(1);
        console.log(`[username 실패] VU=${__VU}, 상태코드=${res.status}, 원인=${res.body}`);
    }
}

// ── Phase 2: DB 조회 API — nickname ───────────────────────────
export function checkNickname() {
    const body = JSON.stringify({ nickname: `k6nick${__VU}` });
    const res = http.post(`${BASE_URL}/user/exists/nickname`, body, {
        headers: { 'Content-Type': 'application/json' },
        timeout: '5s',
    });

    if (res.status >= 200 && res.status < 300) {
        nicknameSuccess.add(1);
    } else {
        nicknameFail.add(1);
        console.log(`[nickname 실패] VU=${__VU}, 상태코드=${res.status}, 원인=${res.body}`);
    }
}

// ── Phase 2: 비-DB API — health check ─────────────────────────
export function checkHealth() {
    const res = http.get(`${BASE_URL}/auth/health`, { timeout: '5s' });

    if (res.status >= 200 && res.status < 300) {
        healthSuccess.add(1);
    } else {
        healthFail.add(1);
        console.log(`[health 실패] VU=${__VU}, 상태코드=${res.status}`);
    }
}

// ── 테스트 종료 후 요약 출력 ───────────────────────────────────
export function handleSummary(data) {
    const uSuccess = data.metrics.username_check_success?.values?.count || 0;
    const uFail    = data.metrics.username_check_fail?.values?.count    || 0;
    const nSuccess = data.metrics.nickname_check_success?.values?.count || 0;
    const nFail    = data.metrics.nickname_check_fail?.values?.count    || 0;
    const hSuccess = data.metrics.health_check_success?.values?.count   || 0;
    const hFail    = data.metrics.health_check_fail?.values?.count      || 0;

    const totalDbFail    = uFail + nFail;
    const totalDbSuccess = uSuccess + nSuccess;
    const totalDb        = 40;
    const failRate       = (totalDbFail / totalDb * 100).toFixed(1);

    const summary = [
        '',
        '============================================',
        '         스케줄러 실행 중 API 결과 (k6)       ',
        '============================================',
        `[DB 조회 API] username 확인: 성공 ${uSuccess} / 실패 ${uFail}`,
        `[DB 조회 API] nickname 확인: 성공 ${nSuccess} / 실패 ${nFail}`,
        `[비교 기준   ] health 확인:  성공 ${hSuccess} / 실패 ${hFail}`,
        '--------------------------------------------',
        `DB 조회 API 전체: 요청 ${totalDb}건 / 성공 ${totalDbSuccess}건 / 실패 ${totalDbFail}건 / 실패율 ${failRate}%`,
        '============================================',
        '',
    ].join('\n');

    console.log(summary);

    return { 'k6-summary.txt': summary };
}
