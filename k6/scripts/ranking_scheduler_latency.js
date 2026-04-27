/**
 * Measures API latency while the ranking scheduler is running.
 *
 * Expected data shape:
 * - Many ranking-enabled chatrooms already exist.
 * - Chatrooms are filled with realistic weekly expense data.
 *
 * Flow:
 * 1. Measure baseline latency before scheduler starts.
 * 2. Trigger POST /ranking/test/trigger once.
 * 3. Measure latency again while scheduler is running.
 *
 * Run examples:
 *   k6 run k6/scripts/ranking_scheduler_latency.js
 *   BASE_URL=http://localhost:8080 k6 run k6/scripts/ranking_scheduler_latency.js
 *   BASELINE_SECONDS=30 DURING_SECONDS=180 DB_VUS=20 k6 run k6/scripts/ranking_scheduler_latency.js
 *   YEARLY_VUS=50 LOGIN_USER_COUNT=500 k6 run k6/scripts/ranking_scheduler_latency.js
 *   LOGIN_PASSWORD=<password> k6 run k6/scripts/ranking_scheduler_latency.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const JWT_TOKEN = __ENV.JWT_TOKEN || '';

const BASELINE_SECONDS = Number(__ENV.BASELINE_SECONDS || 30);
const DURING_SECONDS = Number(__ENV.DURING_SECONDS || 180);
const TRIGGER_BUFFER_SECONDS = Number(__ENV.TRIGGER_BUFFER_SECONDS || 2);
const DB_VUS = Number(__ENV.DB_VUS || 200);
const YEARLY_VUS = Number(__ENV.YEARLY_VUS || 50);
const HEALTH_VUS = Number(__ENV.HEALTH_VUS || 5);
const REQUEST_TIMEOUT = __ENV.REQUEST_TIMEOUT || '30s';
const RUN_ID = String(__ENV.RUN_ID || Date.now()).replace(/[^a-zA-Z0-9]/g, '').slice(-3);
const LOGIN_USER_COUNT = Number(__ENV.LOGIN_USER_COUNT || 500);
const LOGIN_USERNAME_PREFIX = __ENV.LOGIN_USERNAME_PREFIX || 'user_';
const LOGIN_PASSWORD = __ENV.LOGIN_PASSWORD || '';
const YEARLY_TOTAL_DATE = __ENV.YEARLY_TOTAL_DATE || '2025';

const triggerStart = BASELINE_SECONDS;
const duringStart = BASELINE_SECONDS + TRIGGER_BUFFER_SECONDS;

const usernameBaselineDuration = new Trend('username_baseline_duration', true);
const usernameDuringDuration = new Trend('username_during_duration', true);
const nicknameBaselineDuration = new Trend('nickname_baseline_duration', true);
const nicknameDuringDuration = new Trend('nickname_during_duration', true);
const yearlyBaselineDuration = new Trend('yearly_total_baseline_duration', true);
const yearlyDuringDuration = new Trend('yearly_total_during_duration', true);
const healthBaselineDuration = new Trend('health_baseline_duration', true);
const healthDuringDuration = new Trend('health_during_duration', true);

const usernameBaselineSuccess = new Counter('username_baseline_success');
const usernameBaselineFail = new Counter('username_baseline_fail');
const usernameDuringSuccess = new Counter('username_during_success');
const usernameDuringFail = new Counter('username_during_fail');
const nicknameBaselineSuccess = new Counter('nickname_baseline_success');
const nicknameBaselineFail = new Counter('nickname_baseline_fail');
const nicknameDuringSuccess = new Counter('nickname_during_success');
const nicknameDuringFail = new Counter('nickname_during_fail');
const yearlyBaselineSuccess = new Counter('yearly_total_baseline_success');
const yearlyBaselineFail = new Counter('yearly_total_baseline_fail');
const yearlyDuringSuccess = new Counter('yearly_total_during_success');
const yearlyDuringFail = new Counter('yearly_total_during_fail');
const loginSuccess = new Counter('login_success');
const loginFail = new Counter('login_fail');
const healthBaselineSuccess = new Counter('health_baseline_success');
const healthBaselineFail = new Counter('health_baseline_fail');
const healthDuringSuccess = new Counter('health_during_success');
const healthDuringFail = new Counter('health_during_fail');
const schedulerTriggerSuccess = new Counter('scheduler_trigger_success');
const schedulerTriggerFail = new Counter('scheduler_trigger_fail');

function seconds(value) {
    return `${value}s`;
}

export function setup() {
    if (!LOGIN_PASSWORD) {
        throw new Error('LOGIN_PASSWORD environment variable is required.');
    }

    const tokens = [];

    for (let i = 1; i <= LOGIN_USER_COUNT; i += 1) {
        const username = `${LOGIN_USERNAME_PREFIX}${i}`;
        const res = http.post(`${BASE_URL}/auth/login`, JSON.stringify({
            username,
            password: LOGIN_PASSWORD,
        }), {
            headers: { 'Content-Type': 'application/json' },
            timeout: REQUEST_TIMEOUT,
        });

        const token = extractAccessToken(res);
        if (res.status >= 200 && res.status < 300 && token) {
            tokens.push(token);
            loginSuccess.add(1);
        } else {
            loginFail.add(1);
            console.error(`[login failed] username=${username}, status=${res.status}, body=${res.body}`);
        }
    }

    if (tokens.length === 0) {
        throw new Error('No access tokens were collected. Check test users and LOGIN_PASSWORD.');
    }

    console.log(`[setup] collected ${tokens.length}/${LOGIN_USER_COUNT} access tokens`);
    return { tokens };
}

export const options = {
    scenarios: {
        username_baseline: {
            executor: 'constant-vus',
            vus: DB_VUS,
            duration: seconds(BASELINE_SECONDS),
            exec: 'checkUsernameBaseline',
            startTime: '0s',
        },
        nickname_baseline: {
            executor: 'constant-vus',
            vus: DB_VUS,
            duration: seconds(BASELINE_SECONDS),
            exec: 'checkNicknameBaseline',
            startTime: '0s',
        },
        yearly_total_baseline: {
            executor: 'constant-vus',
            vus: YEARLY_VUS,
            duration: seconds(BASELINE_SECONDS),
            exec: 'checkYearlyTotalBaseline',
            startTime: '0s',
        },
        health_baseline: {
            executor: 'constant-vus',
            vus: HEALTH_VUS,
            duration: seconds(BASELINE_SECONDS),
            exec: 'checkHealthBaseline',
            startTime: '0s',
        },
        trigger_scheduler: {
            executor: 'shared-iterations',
            vus: 1,
            iterations: 1,
            maxDuration: '30s',
            exec: 'triggerScheduler',
            startTime: seconds(triggerStart),
        },
        username_during: {
            executor: 'constant-vus',
            vus: DB_VUS,
            duration: seconds(DURING_SECONDS),
            exec: 'checkUsernameDuring',
            startTime: seconds(duringStart),
        },
        nickname_during: {
            executor: 'constant-vus',
            vus: DB_VUS,
            duration: seconds(DURING_SECONDS),
            exec: 'checkNicknameDuring',
            startTime: seconds(duringStart),
        },
        yearly_total_during: {
            executor: 'constant-vus',
            vus: YEARLY_VUS,
            duration: seconds(DURING_SECONDS),
            exec: 'checkYearlyTotalDuring',
            startTime: seconds(duringStart),
        },
        health_during: {
            executor: 'constant-vus',
            vus: HEALTH_VUS,
            duration: seconds(DURING_SECONDS),
            exec: 'checkHealthDuring',
            startTime: seconds(duringStart),
        },
    },
};

function extractAccessToken(res) {
    try {
        return res.json('data.accessToken');
    } catch (error) {
        return null;
    }
}

export function triggerScheduler() {
    const headers = { 'Content-Type': 'application/json' };
    if (JWT_TOKEN) {
        headers.Authorization = `Bearer ${JWT_TOKEN}`;
    }

    const res = http.post(`${BASE_URL}/ranking/test/trigger`, null, {
        headers,
        timeout: REQUEST_TIMEOUT,
    });

    const ok = check(res, {
        'scheduler trigger returned 2xx': (r) => r.status >= 200 && r.status < 300,
    });

    if (ok) {
        schedulerTriggerSuccess.add(1);
    } else {
        schedulerTriggerFail.add(1);
        console.error(`[scheduler trigger failed] status=${res.status}, body=${res.body}`);
    }
}

export function checkUsernameBaseline() {
    callUsername('ub', usernameBaselineDuration, usernameBaselineSuccess, usernameBaselineFail);
}

export function checkUsernameDuring() {
    callUsername('ud', usernameDuringDuration, usernameDuringSuccess, usernameDuringFail);
}

export function checkNicknameBaseline() {
    callNickname('nb', nicknameBaselineDuration, nicknameBaselineSuccess, nicknameBaselineFail);
}

export function checkNicknameDuring() {
    callNickname('nd', nicknameDuringDuration, nicknameDuringSuccess, nicknameDuringFail);
}

export function checkYearlyTotalBaseline(data) {
    callYearlyTotal(data.tokens, yearlyBaselineDuration, yearlyBaselineSuccess, yearlyBaselineFail);
}

export function checkYearlyTotalDuring(data) {
    callYearlyTotal(data.tokens, yearlyDuringDuration, yearlyDuringSuccess, yearlyDuringFail);
}

export function checkHealthBaseline() {
    callHealth(healthBaselineDuration, healthBaselineSuccess, healthBaselineFail);
}

export function checkHealthDuring() {
    callHealth(healthDuringDuration, healthDuringSuccess, healthDuringFail);
}

function callUsername(prefix, durationTrend, successCounter, failCounter) {
    const body = JSON.stringify({ username: uniqueValue(prefix) });
    const res = http.post(`${BASE_URL}/user/exists/username`, body, {
        headers: { 'Content-Type': 'application/json' },
        timeout: REQUEST_TIMEOUT,
    });

    durationTrend.add(res.timings.duration);
    recordResult(res, successCounter, failCounter);
    sleep(0.1);
}

function callNickname(prefix, durationTrend, successCounter, failCounter) {
    const body = JSON.stringify({ nickname: uniqueValue(prefix) });
    const res = http.post(`${BASE_URL}/user/exists/nickname`, body, {
        headers: { 'Content-Type': 'application/json' },
        timeout: REQUEST_TIMEOUT,
    });

    durationTrend.add(res.timings.duration);
    recordResult(res, successCounter, failCounter);
    sleep(0.1);
}

function callYearlyTotal(tokens, durationTrend, successCounter, failCounter) {
    const token = tokens[(__VU + __ITER) % tokens.length];
    const res = http.get(`${BASE_URL}/transactions/yearly/total?date=${YEARLY_TOTAL_DATE}`, {
        headers: { Authorization: `Bearer ${token}` },
        timeout: REQUEST_TIMEOUT,
    });

    durationTrend.add(res.timings.duration);
    recordResult(res, successCounter, failCounter);
    sleep(0.1);
}

function callHealth(durationTrend, successCounter, failCounter) {
    const res = http.get(`${BASE_URL}/auth/health`, { timeout: REQUEST_TIMEOUT });

    durationTrend.add(res.timings.duration);
    recordResult(res, successCounter, failCounter);
    sleep(0.1);
}

function recordResult(res, successCounter, failCounter) {
    if (res.status >= 200 && res.status < 300) {
        successCounter.add(1);
        return;
    }
    failCounter.add(1);
}

function uniqueValue(prefix) {
    const id = (__VU * 100000 + __ITER).toString(36);
    return `${prefix}${RUN_ID}${id}`.slice(0, 10);
}

function metricValues(data, metricName) {
    return data.metrics[metricName]?.values || {};
}

function count(data, metricName) {
    return metricValues(data, metricName).count || 0;
}

function avg(data, metricName) {
    const value = metricValues(data, metricName).avg;
    return value === undefined ? 'n/a' : `${value.toFixed(2)}ms`;
}

function p95(data, metricName) {
    const value = metricValues(data, metricName)['p(95)'];
    return value === undefined ? 'n/a' : `${value.toFixed(2)}ms`;
}

function line(data, label, baselineMetric, duringMetric, baselineSuccess, baselineFail, duringSuccess, duringFail) {
    return [
        label.padEnd(10),
        `baseline avg=${avg(data, baselineMetric)}, p95=${p95(data, baselineMetric)}, ok=${count(data, baselineSuccess)}, fail=${count(data, baselineFail)}`,
        `during avg=${avg(data, duringMetric)}, p95=${p95(data, duringMetric)}, ok=${count(data, duringSuccess)}, fail=${count(data, duringFail)}`,
    ].join(' | ');
}

export function handleSummary(data) {
    const summary = [
        '',
        '============================================================',
        '        Ranking scheduler API latency comparison (k6)',
        '============================================================',
        `BASE_URL=${BASE_URL}`,
        `baseline=${BASELINE_SECONDS}s, during=${DURING_SECONDS}s, db_vus=${DB_VUS}, yearly_vus=${YEARLY_VUS}, health_vus=${HEALTH_VUS}, timeout=${REQUEST_TIMEOUT}`,
        `login users=${LOGIN_USER_COUNT}, yearly_total_date=${YEARLY_TOTAL_DATE}, login ok=${count(data, 'login_success')}, fail=${count(data, 'login_fail')}`,
        `scheduler trigger ok=${count(data, 'scheduler_trigger_success')}, fail=${count(data, 'scheduler_trigger_fail')}`,
        '------------------------------------------------------------',
        line(
            data,
            'username',
            'username_baseline_duration',
            'username_during_duration',
            'username_baseline_success',
            'username_baseline_fail',
            'username_during_success',
            'username_during_fail'
        ),
        line(
            data,
            'nickname',
            'nickname_baseline_duration',
            'nickname_during_duration',
            'nickname_baseline_success',
            'nickname_baseline_fail',
            'nickname_during_success',
            'nickname_during_fail'
        ),
        line(
            data,
            'yearly',
            'yearly_total_baseline_duration',
            'yearly_total_during_duration',
            'yearly_total_baseline_success',
            'yearly_total_baseline_fail',
            'yearly_total_during_success',
            'yearly_total_during_fail'
        ),
        line(
            data,
            'health',
            'health_baseline_duration',
            'health_during_duration',
            'health_baseline_success',
            'health_baseline_fail',
            'health_during_success',
            'health_during_fail'
        ),
        '============================================================',
        '',
    ].join('\n');

    console.log(summary);
    return { 'ranking-scheduler-latency-summary.txt': summary };
}
