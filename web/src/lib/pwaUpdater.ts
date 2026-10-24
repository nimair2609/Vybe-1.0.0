import { useEffect, useState, useCallback, useRef } from 'react';
import { APP_VERSION } from './supabase';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

export interface PwaUpdateState {
  updateAvailable: boolean;
  newVersionLabel: string | null;
  canInstallPwa: boolean;
  isStandalonePwa: boolean;
  updateError: string | null;
  applyUpdateNow: () => Promise<void>;
  dismissUpdateLater: () => void;
  triggerInstallPrompt: () => Promise<void>;
  forceSafeRecoveryReload: () => Promise<void>;
}

/**
 * Safe PWA & Web Version Update Controller
 * - Detects waiting Service Worker updates + polls `/version.json` with `cache: 'no-store'`
 * - Suppresses intrusive prompts during active Voice/Video calls or unsaved composer work
 * - Recovers automatically from stale Vite chunk load errors (`vite:preloadError`)
 */
export function usePwaAndSafeUpdates(options: { hasBusyActivity: boolean }): PwaUpdateState {
  const [waitingWorker, setWaitingWorker] = useState<ServiceWorker | null>(null);
  const [remoteVersion, setRemoteVersion] = useState<string | null>(null);
  const [dismissedUntilMs, setDismissedUntilMs] = useState<number>(0);
  const [installPromptEvent, setInstallPromptEvent] = useState<BeforeInstallPromptEvent | null>(null);
  const [updateError, setUpdateError] = useState<string | null>(null);
  const reloadingRef = useRef(false);

  const isStandalonePwa =
    typeof window !== 'undefined' &&
    (window.matchMedia?.('(display-mode: standalone)')?.matches ||
      Boolean((window.navigator as unknown as { standalone?: boolean }).standalone));

  useEffect(() => {
    if (typeof window === 'undefined') return;

    const onBeforeInstall = (e: Event) => {
      e.preventDefault();
      setInstallPromptEvent(e as BeforeInstallPromptEvent);
    };
    window.addEventListener('beforeinstallprompt', onBeforeInstall);

    // Handle Vite chunk mismatch safely when a new deployment replaces old chunks
    const onPreloadError = (event: Event) => {
      event.preventDefault();
      if (!options.hasBusyActivity && !reloadingRef.current) {
        reloadingRef.current = true;
        window.location.reload();
      } else {
        setRemoteVersion('latest');
      }
    };
    window.addEventListener('vite:preloadError', onPreloadError);

    let registrationRef: ServiceWorkerRegistration | null = null;

    if ('serviceWorker' in navigator && import.meta.env.PROD) {
      navigator.serviceWorker
        .register('/sw.js', { scope: '/' })
        .then((reg) => {
          registrationRef = reg;
          if (reg.waiting) {
            setWaitingWorker(reg.waiting);
          }
          reg.addEventListener('updatefound', () => {
            const installing = reg.installing;
            if (!installing) return;
            installing.addEventListener('statechange', () => {
              if (installing.state === 'installed' && navigator.serviceWorker.controller) {
                setWaitingWorker(installing);
              }
            });
          });
        })
        .catch(() => {
          // Non-fatal in restricted preview iframes
        });

      navigator.serviceWorker.addEventListener('controllerchange', () => {
        if (reloadingRef.current) {
          window.location.reload();
        }
      });
    }

    const checkVersionManifest = async () => {
      try {
        registrationRef?.update().catch(() => undefined);
        const resp = await fetch(`/version.json?t=${Date.now()}`, {
          cache: 'no-store',
          headers: { 'Cache-Control': 'no-cache' }
        });
        if (!resp.ok) return;
        const json = (await resp.json()) as { version?: string };
        if (json?.version && json.version !== APP_VERSION) {
          setRemoteVersion(json.version);
        }
      } catch {
        // Ignore transient offline errors
      }
    };

    const intervalId = window.setInterval(checkVersionManifest, 120_000);
    const onVisibility = () => {
      if (document.visibilityState === 'visible') {
        void checkVersionManifest();
      }
    };
    document.addEventListener('visibilitychange', onVisibility);

    return () => {
      window.removeEventListener('beforeinstallprompt', onBeforeInstall);
      window.removeEventListener('vite:preloadError', onPreloadError);
      document.removeEventListener('visibilitychange', onVisibility);
      window.clearInterval(intervalId);
    };
  }, [options.hasBusyActivity]);

  const applyUpdateNow = useCallback(async () => {
    setUpdateError(null);
    try {
      reloadingRef.current = true;
      if (waitingWorker) {
        waitingWorker.postMessage({ type: 'SKIP_WAITING' });
        // Fallback reload if controllerchange does not fire within 1.5s
        window.setTimeout(() => window.location.reload(), 1500);
        return;
      }
      window.location.reload();
    } catch {
      reloadingRef.current = false;
      setUpdateError('Could not activate update automatically. Use Safe Recovery Reload.');
    }
  }, [waitingWorker]);

  const forceSafeRecoveryReload = useCallback(async () => {
    try {
      if ('serviceWorker' in navigator) {
        const regs = await navigator.serviceWorker.getRegistrations();
        await Promise.all(regs.map((r) => r.unregister()));
      }
      if ('caches' in window) {
        const keys = await caches.keys();
        await Promise.all(keys.filter((k) => k.startsWith('vybe-static-')).map((k) => caches.delete(k)));
      }
    } finally {
      window.location.reload();
    }
  }, []);

  const dismissUpdateLater = useCallback(() => {
    // Snooze prompt for 15 minutes
    setDismissedUntilMs(Date.now() + 15 * 60_000);
  }, []);

  const triggerInstallPrompt = useCallback(async () => {
    if (!installPromptEvent) return;
    await installPromptEvent.prompt();
    const choice = await installPromptEvent.userChoice;
    if (choice.outcome === 'accepted') {
      setInstallPromptEvent(null);
    }
  }, [installPromptEvent]);

  const hasPendingUpdate = Boolean(waitingWorker) || Boolean(remoteVersion);
  const isSnoozed = Date.now() < dismissedUntilMs;

  return {
    updateAvailable: hasPendingUpdate && !isSnoozed && !options.hasBusyActivity,
    newVersionLabel: remoteVersion,
    canInstallPwa: Boolean(installPromptEvent) && !isStandalonePwa,
    isStandalonePwa,
    updateError,
    applyUpdateNow,
    dismissUpdateLater,
    triggerInstallPrompt,
    forceSafeRecoveryReload
  };
}
