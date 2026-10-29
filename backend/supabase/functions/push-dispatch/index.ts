// Supabase Edge Function: push-dispatch
// Dispatches deduplicated high-priority data-only FCM HTTP v1 push notifications
// and persists in-app notification records in public.notifications.
// Privileged credentials (FIREBASE_SERVICE_ACCOUNT, SUPABASE_SERVICE_ROLE_KEY)
// remain strictly in Supabase Edge Function secrets — NEVER in Android client code.

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const b64 = pem
    .replace(/-----BEGIN PRIVATE KEY-----/g, '')
    .replace(/-----END PRIVATE KEY-----/g, '')
    .replace(/\\n/g, '')
    .replace(/\s+/g, '');
  const binary = atob(b64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

function base64UrlEncode(input: string | Uint8Array): string {
  const str = typeof input === 'string'
    ? btoa(input)
    : btoa(String.fromCharCode(...input));
  return str.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function getFcmAccessToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = base64UrlEncode(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const claim = base64UrlEncode(JSON.stringify({
    iss: sa.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  }));
  const signingInput = `${header}.${claim}`;
  const cryptoKey = await crypto.subtle.importKey(
    'pkcs8',
    pemToArrayBuffer(sa.private_key),
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign']
  );
  const signature = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    cryptoKey,
    new TextEncoder().encode(signingInput)
  );
  const assertion = `${signingInput}.${base64UrlEncode(new Uint8Array(signature))}`;

  const tokenResp = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: `grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=${assertion}`,
  });
  const tokenJson = await tokenResp.json();
  if (!tokenJson.access_token) {
    throw new Error(`FCM OAuth2 token exchange failed: ${JSON.stringify(tokenJson)}`);
  }
  return tokenJson.access_token as string;
}

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get('SUPABASE_URL') ?? '';
    const serviceRoleKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '';
    const anonKey = Deno.env.get('SUPABASE_ANON_KEY') ?? '';
    const saRaw = Deno.env.get('FIREBASE_SERVICE_ACCOUNT') ?? '';

    const authHeader = req.headers.get('Authorization') ?? '';
    const userClient = createClient(supabaseUrl, anonKey, {
      global: { headers: { Authorization: authHeader } },
    });
    const { data: authData, error: authErr } = await userClient.auth.getUser();
    if (authErr || !authData?.user) {
      return new Response(JSON.stringify({ error: 'Authentication required' }), {
        status: 401,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const callerId = authData.user.id;
    const supabaseAdmin = createClient(supabaseUrl, serviceRoleKey);
    const body = await req.json();

    const {
      recipientId,
      title,
      message,
      type = 'LIKE',
      targetId = null,
      eventKey,
      channelId = 'vybe_activity_high_v2',
      data = {},
    } = body;

    if (!recipientId || !title || !message) {
      return new Response(JSON.stringify({ error: 'recipientId, title, and message are required' }), {
        status: 400,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    if (recipientId === callerId && type !== 'ANNOUNCEMENT') {
      return new Response(JSON.stringify({ skipped: 'self_event' }), {
        status: 200,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    // Respect bidirectional block list
    const { data: blocks } = await supabaseAdmin
      .from('user_blocks')
      .select('blocker_id')
      .or(`and(blocker_id.eq.${callerId},blocked_id.eq.${recipientId}),and(blocker_id.eq.${recipientId},blocked_id.eq.${callerId})`)
      .limit(1);

    if (blocks && blocks.length > 0) {
      return new Response(JSON.stringify({ skipped: 'blocked' }), {
        status: 200,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      });
    }

    const canonicalEventKey = String(
      eventKey || data.event_key || `${String(type).toLowerCase()}_${callerId}_${targetId || recipientId}`
    );

    // 1. Persist deduplicated notification record in public.notifications
    let notificationId = String(data.notification_id || '');
    const { data: insertedNotif } = await supabaseAdmin
      .from('notifications')
      .upsert(
        {
          recipient_id: recipientId,
          actor_id: callerId,
          type,
          title,
          message,
          target_id: targetId,
          event_key: canonicalEventKey,
          is_read: false,
        },
        { onConflict: 'recipient_id,event_key', ignoreDuplicates: false }
      )
      .select('id')
      .maybeSingle();

    if (insertedNotif?.id) {
      notificationId = insertedNotif.id;
    }

    // 2. Fetch active FCM registration tokens for recipient
    const { data: tokenRows } = await supabaseAdmin
      .from('user_device_tokens')
      .select('fcm_token')
      .eq('user_id', recipientId)
      .eq('is_active', true);

    const tokens = Array.from(new Set((tokenRows || []).map((r: { fcm_token: string }) => r.fcm_token).filter(Boolean)));
    if (!saRaw || tokens.length === 0) {
      return new Response(
        JSON.stringify({
          success: true,
          notificationId,
          eventKey: canonicalEventKey,
          delivered: 0,
          fcmConfigured: Boolean(saRaw),
        }),
        { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const sa: ServiceAccount = JSON.parse(saRaw);
    const accessToken = await getFcmAccessToken(sa);
    const projectId = sa.project_id || 'vybe-1c966';

    let delivered = 0;
    let failed = 0;

    // Data-only high-priority FCM HTTP v1 payload prevents OS + app duplicate notifications
    for (const token of tokens) {
      const stringData: Record<string, string> = {};
      for (const [k, v] of Object.entries(data)) {
        if (v !== undefined && v !== null) stringData[k] = String(v);
      }
      stringData.title = String(title);
      stringData.body = String(message);
      stringData.type = String(type);
      stringData.event_key = canonicalEventKey;
      stringData.channel_id = String(channelId);
      if (notificationId) stringData.notification_id = notificationId;
      if (targetId) stringData.target_id = String(targetId);

      const fcmResp = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${accessToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          message: {
            token,
            data: stringData,
            android: {
              priority: 'high',
            },
          },
        }),
      });

      if (fcmResp.ok) {
        delivered++;
      } else {
        failed++;
        const errText = await fcmResp.text();
        if (errText.includes('UNREGISTERED') || errText.includes('INVALID_ARGUMENT')) {
          await supabaseAdmin
            .from('user_device_tokens')
            .update({ is_active: false, updated_at: new Date().toISOString() })
            .eq('fcm_token', token);
        }
      }
    }

    return new Response(
      JSON.stringify({
        success: true,
        notificationId,
        eventKey: canonicalEventKey,
        delivered,
        failed,
      }),
      { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  } catch (err) {
    return new Response(JSON.stringify({ error: (err as Error).message }), {
      status: 500,
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
    });
  }
});
