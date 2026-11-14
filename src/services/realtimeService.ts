import { SUPABASE_URL, SUPABASE_ANON_KEY } from './supabaseService';

export interface RealtimePushPayload {
  type?: string;
  title?: string;
  body?: string;
  destination?: string;
  target_id?: string;
  conversation_id?: string;
  notification_id?: string;
  actor_id?: string;
  recipient_id?: string;
  actor_username?: string;
  actor_display_name?: string;
  actor_avatar_url?: string;
  media_url?: string;
  event_key?: string;
  is_typing?: boolean;
}

export interface RealtimeDbChangePayload {
  schema?: string;
  table?: string;
  type?: 'INSERT' | 'UPDATE' | 'DELETE';
  record?: Record<string, any>;
  old_record?: Record<string, any>;
}

export interface VybeRealtimeHandlers {
  onPushEvent?: (payload: RealtimePushPayload) => void;
  onDbMessage?: (change: RealtimeDbChangePayload) => void;
  onDbNotification?: (change: RealtimeDbChangePayload) => void;
  onConnectionChange?: (connected: boolean) => void;
}

class VybeWebRealtimeManager {
  private ws: WebSocket | null = null;
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private refCounter = 1;
  private activeUserId: string | null = null;
  private activeToken: string | null = null;
  private handlers: VybeRealtimeHandlers = {};
  private reconnectAttempt = 0;
  private intentionallyClosed = false;

  public connect(userId: string, accessToken: string, handlers: VybeRealtimeHandlers): void {
    this.handlers = handlers;
    if (!userId || !accessToken || !accessToken.startsWith('eyJ')) return;

    if (
      this.ws &&
      this.ws.readyState === WebSocket.OPEN &&
      this.activeUserId === userId &&
      this.activeToken === accessToken
    ) {
      return;
    }

    this.disconnect(false);
    this.activeUserId = userId;
    this.activeToken = accessToken;
    this.intentionallyClosed = false;
    this.openSocket();
  }

  public updateToken(accessToken: string): void {
    if (!accessToken || !accessToken.startsWith('eyJ') || accessToken === this.activeToken) return;
    this.activeToken = accessToken;
    if (this.activeUserId) {
      this.connect(this.activeUserId, accessToken, this.handlers);
    }
  }

  public disconnect(clearIdentity: boolean = true): void {
    this.intentionallyClosed = true;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
    if (this.ws) {
      try {
        this.ws.onopen = null;
        this.ws.onmessage = null;
        this.ws.onerror = null;
        this.ws.onclose = null;
        this.ws.close(1000, 'Session ended');
      } catch {
        // ignore close errors
      }
      this.ws = null;
    }
    if (clearIdentity) {
      this.activeUserId = null;
      this.activeToken = null;
      this.reconnectAttempt = 0;
    }
    this.handlers.onConnectionChange?.(false);
  }

  private openSocket(): void {
    if (!this.activeUserId || !this.activeToken) return;
    const baseWs = SUPABASE_URL.replace(/^https:\/\//i, 'wss://').replace(/^http:\/\//i, 'ws://');
    const wsUrl = `${baseWs}/realtime/v1/websocket?apikey=${encodeURIComponent(SUPABASE_ANON_KEY)}&vsn=1.0.0`;

    try {
      const socket = new WebSocket(wsUrl);
      this.ws = socket;
      const userId = this.activeUserId;
      const token = this.activeToken;

      socket.onopen = () => {
        if (this.ws !== socket) return;
        this.reconnectAttempt = 0;
        this.handlers.onConnectionChange?.(true);

        // 1. Join user push broadcast topic (matches Android VybeRealtimePushManager.kt)
        this.sendFrame(socket, {
          topic: `realtime:vybe_user_push:${userId}`,
          event: 'phx_join',
          payload: {
            config: {
              broadcast: { self: false, ack: false }
            },
            access_token: token
          },
          ref: String(this.refCounter++)
        });

        // 2. Join Postgres Changes for messages
        this.sendFrame(socket, {
          topic: 'realtime:public:messages',
          event: 'phx_join',
          payload: {
            config: {
              postgres_changes: [
                { event: 'INSERT', schema: 'public', table: 'messages' },
                { event: 'UPDATE', schema: 'public', table: 'messages' }
              ]
            },
            access_token: token
          },
          ref: String(this.refCounter++)
        });

        // 3. Join Postgres Changes for recipient notifications
        this.sendFrame(socket, {
          topic: 'realtime:public:notifications',
          event: 'phx_join',
          payload: {
            config: {
              postgres_changes: [
                {
                  event: 'INSERT',
                  schema: 'public',
                  table: 'notifications',
                  filter: `recipient_id=eq.${userId}`
                }
              ]
            },
            access_token: token
          },
          ref: String(this.refCounter++)
        });

        // 25s Phoenix heartbeat
        if (this.heartbeatTimer) clearInterval(this.heartbeatTimer);
        this.heartbeatTimer = setInterval(() => {
          if (socket.readyState === WebSocket.OPEN) {
            this.sendFrame(socket, {
              topic: 'phoenix',
              event: 'heartbeat',
              payload: {},
              ref: String(this.refCounter++)
            });
          }
        }, 25_000);
      };

      socket.onmessage = (evt) => {
        try {
          const msg = JSON.parse(String(evt.data || '{}'));
          if (msg.event === 'broadcast') {
            const innerEvent = msg.payload?.event;
            const innerData = msg.payload?.payload as RealtimePushPayload | undefined;
            if ((innerEvent === 'push_event' || innerEvent === 'broadcast_event') && innerData) {
              if (innerData.actor_id && innerData.actor_id === this.activeUserId) return;
              if (innerData.recipient_id && this.activeUserId && innerData.recipient_id !== this.activeUserId) return;
              this.handlers.onPushEvent?.(innerData);
            }
          } else if (msg.event === 'postgres_changes') {
            const data = msg.payload?.data as RealtimeDbChangePayload | undefined;
            if (!data) return;
            if (data.table === 'messages') {
              this.handlers.onDbMessage?.(data);
            } else if (data.table === 'notifications') {
              this.handlers.onDbNotification?.(data);
            }
          }
        } catch {
          // ignore malformed frames
        }
      };

      socket.onerror = () => {
        this.handlers.onConnectionChange?.(false);
      };

      socket.onclose = () => {
        if (this.heartbeatTimer) {
          clearInterval(this.heartbeatTimer);
          this.heartbeatTimer = null;
        }
        if (this.ws === socket) {
          this.ws = null;
        }
        this.handlers.onConnectionChange?.(false);
        if (!this.intentionallyClosed && this.activeUserId && this.activeToken) {
          const delayMs = Math.min(20_000, 3_000 * Math.pow(2, this.reconnectAttempt));
          this.reconnectAttempt += 1;
          this.reconnectTimer = setTimeout(() => {
            if (!this.intentionallyClosed && this.activeUserId && this.activeToken) {
              this.openSocket();
            }
          }, delayMs);
        }
      };
    } catch {
      this.handlers.onConnectionChange?.(false);
    }
  }

  private sendFrame(socket: WebSocket, frame: Record<string, any>): void {
    try {
      if (socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify(frame));
      }
    } catch {
      // ignore send failures
    }
  }
}

export const vybeRealtime = new VybeWebRealtimeManager();
