import { describe, it, before, after } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { server, verifyUserAuth, verifyAdminAuth, checkRateLimit } = require('../serve_web.js');

const USER_ID_A = '11111111-1111-4111-8111-111111111111';
const USER_ID_B = '22222222-2222-4222-8222-222222222222';
const ADMIN_ID = '33333333-3333-4333-8333-333333333333';
const FAKE_JWT_USER = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMTExMTExMS0xMTExLTQxMTEtODExMS0xMTExMTExMTExMTEifQ.sig';
const FAKE_JWT_ADMIN = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIzMzMzMzMzMy0zMzMzLTQzMzMtODMzMy0zMzMzMzMzMzMzMzMifQ.sig';

let baseUrl = '';
const originalFetch = globalThis.fetch;

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' }
  });
}

describe('VYBE Security Gateway Regression Tests', () => {
  before(async () => {
    await new Promise((resolve) => {
      server.listen(0, '127.0.0.1', () => {
        const addr = server.address();
        baseUrl = `http://127.0.0.1:${addr.port}`;
        resolve();
      });
    });
  });

  after(async () => {
    globalThis.fetch = originalFetch;
    await new Promise((resolve) => server.close(resolve));
  });

  it('sets mandatory security response headers on health endpoint', async () => {
    const resp = await originalFetch(`${baseUrl}/api/v1/health`, {
      headers: { 'x-forwarded-for': '10.0.0.1' }
    });
    assert.equal(resp.status, 200);
    assert.equal(resp.headers.get('x-content-type-options'), 'nosniff');
    assert.equal(resp.headers.get('x-frame-options'), 'DENY');
    assert.equal(resp.headers.get('referrer-policy'), 'no-referrer');
  });

  it('rejects unauthenticated or spoofed-header requests to /api/v1/push/send and /api/v1/admin/*', async () => {
    const r1 = await originalFetch(`${baseUrl}/api/v1/push/send`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'x-forwarded-for': '10.0.0.2' },
      body: JSON.stringify({ recipientId: USER_ID_B, title: 'Hi', body: 'Test' })
    });
    assert.equal(r1.status, 401);

    const r2 = await originalFetch(`${baseUrl}/api/v1/admin/users`, {
      method: 'GET',
      headers: {
        'x-admin-role': 'SUPER_ADMIN',
        'x-admin-user-id': ADMIN_ID,
        'Authorization': 'Bearer not-a-jwt-token',
        'x-forwarded-for': '10.0.0.3'
      }
    });
    assert.equal(r2.status, 401);

    const r3 = await originalFetch(`${baseUrl}/api/v1/admin/assign-badge`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-admin-role': 'SUPER_ADMIN',
        'x-forwarded-for': '10.0.0.4'
      },
      body: JSON.stringify({ userId: USER_ID_A, badgeType: 'verified', value: true })
    });
    assert.equal(r3.status, 401);
  });

  it('returns 403 for non-admin authenticated users hitting /api/v1/admin/* endpoints', async () => {
    globalThis.fetch = async (url, init) => {
      const urlStr = String(url);
      if (urlStr.startsWith(baseUrl)) return originalFetch(url, init);
      if (urlStr.includes('/auth/v1/user')) {
        return jsonResponse({ id: USER_ID_A, email: 'user@example.com' });
      }
      if (urlStr.includes('/rest/v1/profiles')) {
        return jsonResponse([{ id: USER_ID_A, role: 'USER' }]);
      }
      if (urlStr.includes('/rest/v1/admin_users')) {
        return jsonResponse([]);
      }
      return jsonResponse({}, 404);
    };

    try {
      const [uRes, bRes, pRes] = await Promise.all([
        originalFetch(`${baseUrl}/api/v1/admin/users`, {
          headers: { Authorization: `Bearer ${FAKE_JWT_USER}`, 'x-forwarded-for': '10.0.0.5' }
        }),
        originalFetch(`${baseUrl}/api/v1/admin/assign-badge`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${FAKE_JWT_USER}`,
            'x-forwarded-for': '10.0.0.6'
          },
          body: JSON.stringify({ userId: USER_ID_B, badgeType: 'verified', value: true })
        }),
        originalFetch(`${baseUrl}/api/v1/admin/broadcast-push`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${FAKE_JWT_USER}`,
            'x-forwarded-for': '10.0.0.7'
          },
          body: JSON.stringify({ title: 'Hello', message: 'World' })
        })
      ]);

      assert.equal(uRes.status, 403);
      assert.equal(bRes.status, 403);
      assert.equal(pRes.status, 403);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('blocks admin self-grant and non-super-admin WORKER badge assignment with 403', async () => {
    globalThis.fetch = async (url, init) => {
      const urlStr = String(url);
      if (urlStr.startsWith(baseUrl)) return originalFetch(url, init);
      if (urlStr.includes('/auth/v1/user')) {
        return jsonResponse({ id: ADMIN_ID, email: 'admin@example.com' });
      }
      if (urlStr.includes('/rest/v1/profiles')) {
        return jsonResponse([{ id: ADMIN_ID, role: 'ADMIN' }]);
      }
      if (urlStr.includes('/rest/v1/admin_users')) {
        return jsonResponse([{ role: 'ADMIN', is_active: true }]);
      }
      return jsonResponse({}, 404);
    };

    try {
      const selfRes = await originalFetch(`${baseUrl}/api/v1/admin/assign-badge`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${FAKE_JWT_ADMIN}`,
          'x-forwarded-for': '10.0.0.8'
        },
        body: JSON.stringify({ userId: ADMIN_ID, badgeType: 'verified', value: true })
      });
      assert.equal(selfRes.status, 403);

      const workerRes = await originalFetch(`${baseUrl}/api/v1/admin/assign-badge`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${FAKE_JWT_ADMIN}`,
          'x-forwarded-for': '10.0.0.9'
        },
        body: JSON.stringify({ userId: USER_ID_A, badgeType: 'worker', value: true })
      });
      assert.equal(workerRes.status, 403);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('blocks runtime FCM disk credential writes unless super-admin and ALLOW_DISK_FCM_CONFIG=true', async () => {
    globalThis.fetch = async (url, init) => {
      const urlStr = String(url);
      if (urlStr.startsWith(baseUrl)) return originalFetch(url, init);
      if (urlStr.includes('/auth/v1/user')) {
        return jsonResponse({ id: ADMIN_ID, email: 'owner@example.com' });
      }
      if (urlStr.includes('/rest/v1/profiles')) {
        return jsonResponse([{ id: ADMIN_ID, role: 'OWNER_ADMIN' }]);
      }
      if (urlStr.includes('/rest/v1/admin_users')) {
        return jsonResponse([{ role: 'OWNER_ADMIN', is_active: true }]);
      }
      return jsonResponse({}, 404);
    };

    const prevFlag = process.env.ALLOW_DISK_FCM_CONFIG;
    delete process.env.ALLOW_DISK_FCM_CONFIG;
    try {
      const fcmRes = await originalFetch(`${baseUrl}/api/v1/admin/fcm-config`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${FAKE_JWT_ADMIN}`,
          'x-forwarded-for': '10.0.0.10'
        },
        body: JSON.stringify({
          type: 'service_account',
          project_id: 'fake-proj',
          client_email: 'x@fake.iam.gserviceaccount.com',
          private_key: '-----BEGIN PRIVATE KEY-----\nABC\n-----END PRIVATE KEY-----\n'
        })
      });
      assert.equal(fcmRes.status, 403);
    } finally {
      if (prevFlag !== undefined) process.env.ALLOW_DISK_FCM_CONFIG = prevFlag;
      globalThis.fetch = originalFetch;
    }
  });

  it('respects blocks and locks actor_id on authenticated /api/v1/push/send', async () => {
    let broadcastPayloads = [];
    globalThis.fetch = async (url, init) => {
      const urlStr = String(url);
      if (urlStr.startsWith(baseUrl)) return originalFetch(url, init);
      if (urlStr.includes('/auth/v1/user')) {
        return jsonResponse({ id: USER_ID_A, email: 'usera@example.com' });
      }
      if (urlStr.includes('/rest/v1/blocks')) {
        if (urlStr.includes(USER_ID_B)) {
          return jsonResponse([{ blocker_id: USER_ID_B }]);
        }
        return jsonResponse([]);
      }
      if (urlStr.includes('/rest/v1/user_device_tokens')) {
        return jsonResponse([]);
      }
      if (urlStr.includes('/realtime/v1/api/broadcast')) {
        broadcastPayloads.push(JSON.parse(init.body));
        return jsonResponse({ ok: true });
      }
      return jsonResponse({}, 404);
    };

    try {
      // Blocked recipient -> skipped
      const blockedRes = await originalFetch(`${baseUrl}/api/v1/push/send`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${FAKE_JWT_USER}`,
          'x-forwarded-for': '10.0.0.11'
        },
        body: JSON.stringify({
          recipientId: USER_ID_B,
          title: 'Hey',
          body: 'Blocked test',
          data: { actor_id: ADMIN_ID }
        })
      });
      assert.equal(blockedRes.status, 200);
      const blockedJson = await blockedRes.json();
      assert.equal(blockedJson.skipped, 'blocked');
      assert.equal(broadcastPayloads.length, 0);

      // Allowed recipient -> actor_id overwritten to authenticated caller (USER_ID_A)
      const ALLOWED_RECIPIENT = '44444444-4444-4444-8444-444444444444';
      const okRes = await originalFetch(`${baseUrl}/api/v1/push/send`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${FAKE_JWT_USER}`,
          'x-forwarded-for': '10.0.0.12'
        },
        body: JSON.stringify({
          recipientId: ALLOWED_RECIPIENT,
          title: 'Hi',
          body: 'Legit push',
          data: { actor_id: ADMIN_ID }
        })
      });
      assert.equal(okRes.status, 200);
      assert.equal(broadcastPayloads.length, 1);
      assert.equal(broadcastPayloads[0].messages[0].payload.actor_id, USER_ID_A);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('enforces sliding-window rate limiting on privileged admin endpoints (429)', async () => {
    const fakeReq = { headers: { 'x-forwarded-for': '10.99.99.99' }, socket: {} };
    for (let i = 0; i < 60; i++) {
      assert.equal(checkRateLimit(fakeReq, true), true);
    }
    assert.equal(checkRateLimit(fakeReq, true), false);

    const rateRes = await originalFetch(`${baseUrl}/api/v1/admin/users`, {
      headers: { 'x-forwarded-for': '10.99.99.99' }
    });
    assert.equal(rateRes.status, 429);
  });

  it('exports verifyUserAuth and verifyAdminAuth that fail closed on missing tokens', async () => {
    const u = await verifyUserAuth({ headers: {} });
    assert.equal(u.ok, false);
    assert.equal(u.status, 401);

    const a = await verifyAdminAuth({ headers: { authorization: 'Bearer invalid' } });
    assert.equal(a.ok, false);
    assert.equal(a.status, 401);
  });

  it('enforces super-admin, self-mutation, DB sync, and audit log on /api/v1/admin/set-role', async () => {
    let callerRole = 'MODERATOR';
    const patchedProfiles = [];
    const syncedAdminUsers = [];
    const auditLogs = [];

    globalThis.fetch = async (url, init = {}) => {
      const urlStr = String(url);
      if (urlStr.startsWith(baseUrl)) return originalFetch(url, init);
      if (urlStr.includes('/auth/v1/user')) {
        return jsonResponse({ id: ADMIN_ID, email: 'owner@vybe.app' });
      }
      if (urlStr.includes('/rest/v1/profiles') && (!init.method || init.method === 'GET')) {
        return jsonResponse([{ id: ADMIN_ID, role: callerRole }]);
      }
      if (urlStr.includes('/rest/v1/admin_users') && (!init.method || init.method === 'GET')) {
        return jsonResponse([{ role: callerRole, is_active: true }]);
      }
      if (urlStr.includes('/rest/v1/profiles') && init.method === 'PATCH') {
        patchedProfiles.push(JSON.parse(init.body));
        return jsonResponse([{ id: USER_ID_A, ...JSON.parse(init.body) }]);
      }
      if (urlStr.includes('/rest/v1/admin_users') && (init.method === 'POST' || init.method === 'PATCH')) {
        syncedAdminUsers.push({ method: init.method, body: JSON.parse(init.body) });
        return jsonResponse({}, 201);
      }
      if (urlStr.includes('/rest/v1/admin_audit_logs') && init.method === 'POST') {
        auditLogs.push(JSON.parse(init.body));
        return jsonResponse({}, 201);
      }
      if (urlStr.includes('/realtime/v1/api/broadcast') || urlStr.includes('/rest/v1/user_device_tokens')) {
        return jsonResponse([]);
      }
      return jsonResponse({}, 404);
    };

    try {
      // Non-super-admin rejected with 403
      const modRes = await originalFetch(`${baseUrl}/api/v1/admin/set-role`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${FAKE_JWT_ADMIN}`,
          'x-forwarded-for': '10.0.0.21'
        },
        body: JSON.stringify({ userId: USER_ID_A, role: 'MODERATOR' })
      });
      assert.equal(modRes.status, 403);

      // Super-admin self-mutation rejected with 403
      callerRole = 'OWNER_ADMIN';
      const selfRes = await originalFetch(`${baseUrl}/api/v1/admin/set-role`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${FAKE_JWT_ADMIN}`,
          'x-forwarded-for': '10.0.0.22'
        },
        body: JSON.stringify({ userId: ADMIN_ID, role: 'USER' })
      });
      assert.equal(selfRes.status, 403);

      // Valid super-admin role grant updates DB, admin_users, and audit logs
      const okRes = await originalFetch(`${baseUrl}/api/v1/admin/set-role`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${FAKE_JWT_ADMIN}`,
          'x-forwarded-for': '10.0.0.23'
        },
        body: JSON.stringify({ userId: USER_ID_A, role: 'SUPPORT_ADMIN', username: 'support_dev' })
      });
      assert.equal(okRes.status, 200);
      assert.equal(patchedProfiles.length, 1);
      assert.equal(patchedProfiles[0].role, 'MODERATOR');
      assert.equal(syncedAdminUsers.length, 1);
      assert.equal(syncedAdminUsers[0].body.role, 'SUPPORT_ADMIN');
      assert.equal(auditLogs.length, 1);
      assert.equal(auditLogs[0].action, 'UPDATE_ROLE');
      assert.equal(auditLogs[0].admin_email, 'owner@vybe.app');
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('persists user-status, report-action, verification-requests/review, and /api/v1/push/broadcast with audit logging', async () => {
    const modActions = [];
    const reportPatches = [];
    const rpcCalls = [];
    const auditLogs = [];
    const REQ_ID = '55555555-5555-4555-8555-555555555555';
    const REP_ID = '66666666-6666-4666-8666-666666666666';

    globalThis.fetch = async (url, init = {}) => {
      const urlStr = String(url);
      if (urlStr.startsWith(baseUrl)) return originalFetch(url, init);
      if (urlStr.includes('/auth/v1/user')) {
        return jsonResponse({ id: ADMIN_ID, email: 'lead_admin@vybe.app' });
      }
      if (urlStr.includes('/rest/v1/profiles') && (!init.method || init.method === 'GET')) {
        return jsonResponse([{ id: ADMIN_ID, role: 'ADMIN' }]);
      }
      if (urlStr.includes('/rest/v1/admin_users') && (!init.method || init.method === 'GET')) {
        return jsonResponse([{ role: 'ADMIN', is_active: true }]);
      }
      if (urlStr.includes('/rest/v1/profiles') && init.method === 'PATCH') {
        return jsonResponse([{ id: USER_ID_A }]);
      }
      if (urlStr.includes('/rest/v1/user_moderation_actions') && init.method === 'POST') {
        modActions.push(JSON.parse(init.body));
        return jsonResponse({}, 201);
      }
      if (urlStr.includes('/rest/v1/reports') && init.method === 'PATCH') {
        reportPatches.push(JSON.parse(init.body));
        return jsonResponse([{ id: REP_ID, ...JSON.parse(init.body) }]);
      }
      if (urlStr.includes('/rest/v1/rpc/admin_review_verification_request')) {
        rpcCalls.push(JSON.parse(init.body));
        return jsonResponse({ success: true, status: 'APPROVED' });
      }
      if (urlStr.includes('/rest/v1/admin_audit_logs') && init.method === 'POST') {
        auditLogs.push(JSON.parse(init.body));
        return jsonResponse({}, 201);
      }
      if (urlStr.includes('/realtime/v1/api/broadcast') || urlStr.includes('/rest/v1/user_device_tokens')) {
        return jsonResponse([]);
      }
      return jsonResponse({}, 404);
    };

    try {
      const statusRes = await originalFetch(`${baseUrl}/api/v1/admin/user-status`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${FAKE_JWT_ADMIN}`,
          'x-forwarded-for': '10.0.0.24'
        },
        body: JSON.stringify({ userId: USER_ID_A, action: 'SUSPEND', reason: 'Spam', adminEmail: 'spoof@evil.com' })
      });
      assert.equal(statusRes.status, 200);
      assert.equal(modActions.length, 1);
      assert.equal(modActions[0].action_type, 'SUSPEND');

      const reportRes = await originalFetch(`${baseUrl}/api/v1/admin/report-action`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${FAKE_JWT_ADMIN}`,
          'x-forwarded-for': '10.0.0.25'
        },
        body: JSON.stringify({ reportId: REP_ID, action: 'RESOLVE', notes: 'Handled' })
      });
      assert.equal(reportRes.status, 200);
      assert.equal(reportPatches.length, 1);
      assert.equal(reportPatches[0].status, 'RESOLVED');

      const reviewRes = await originalFetch(`${baseUrl}/api/v1/admin/verification-requests/review`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${FAKE_JWT_ADMIN}`,
          'x-forwarded-for': '10.0.0.26'
        },
        body: JSON.stringify({ requestId: REQ_ID, decision: 'APPROVED', notes: 'Verified creator' })
      });
      assert.equal(reviewRes.status, 200);
      assert.equal(rpcCalls.length, 1);
      assert.equal(rpcCalls[0].p_request_id, REQ_ID);

      // Alias /api/v1/push/broadcast must require admin & log audit
      const unauthBcast = await originalFetch(`${baseUrl}/api/v1/push/broadcast`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'x-forwarded-for': '10.0.0.27' },
        body: JSON.stringify({ title: 'Test', body: 'No token' })
      });
      assert.equal(unauthBcast.status, 401);

      const okBcast = await originalFetch(`${baseUrl}/api/v1/push/broadcast`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${FAKE_JWT_ADMIN}`,
          'x-forwarded-for': '10.0.0.28'
        },
        body: JSON.stringify({ title: 'Release', body: 'VYBE 2.0 Live' })
      });
      assert.equal(okBcast.status, 200);

      assert.equal(auditLogs.some(l => l.action === 'USER_SUSPEND' && l.admin_email === 'lead_admin@vybe.app'), true);
      assert.equal(auditLogs.some(l => l.action === 'REPORT_RESOLVED' && l.admin_email === 'lead_admin@vybe.app'), true);
      assert.equal(auditLogs.some(l => l.action === 'SYSTEM_BROADCAST' && l.admin_email === 'lead_admin@vybe.app'), true);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('blocks static path traversal and prevents package.json / dotfile leaks', async () => {
    // 1. Fetch normalized path /../../package.json (never leak dependencies)
    const fetchResp = await originalFetch(`${baseUrl}/../../package.json`, {
      headers: { 'x-forwarded-for': '10.0.0.29' }
    });
    const fetchText = await fetchResp.text();
    assert.equal(fetchText.includes('"dependencies"'), false);
    assert.notEqual(fetchResp.headers.get('content-type'), 'application/json');

    // 2. Raw HTTP request with literal traversal path /../../package.json -> 403 Forbidden
    const rawResult = await new Promise((resolve, reject) => {
      const parsed = new URL(baseUrl);
      const req = http.request({
        hostname: parsed.hostname,
        port: Number(parsed.port),
        path: '/../../package.json',
        method: 'GET',
        headers: { 'x-forwarded-for': '10.0.0.30' }
      }, (res) => {
        let data = '';
        res.on('data', chunk => { data += chunk; });
        res.on('end', () => resolve({ status: res.statusCode, body: data }));
      });
      req.on('error', reject);
      req.end();
    });

    assert.equal(rawResult.status, 403);
    assert.equal(rawResult.body.includes('"dependencies"'), false);

    // 3. Direct manifest request /package.json -> 403 Forbidden
    const directManifest = await originalFetch(`${baseUrl}/package.json`, {
      headers: { 'x-forwarded-for': '10.0.0.31' }
    });
    assert.equal(directManifest.status, 403);
  });
});

