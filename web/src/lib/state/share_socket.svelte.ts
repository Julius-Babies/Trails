import type {Battery, LastLocation} from "$lib/state/webapp_socket.svelte";

/** A shared device's current state, pushed by its origin homeserver. */
export interface ShareSnapshot {
    name: string;
    manufacturer: string;
    model: string;
    last_location: LastLocation | null;
    battery: Battery | null;
}

type ServerMessage =
    | { type: "share.snapshot"; active_share_id: string; snapshot: ShareSnapshot }
    | { type: "share.gone"; active_share_id: string };

/**
 * Resolves a homeserver identifier into a URL base. An empty value means the
 * current origin (relative URLs); otherwise it is treated as an origin, with
 * https:// assumed when no scheme is given.
 */
export function shareOriginBase(homeserver: string): string {
    if (!homeserver) return "";
    const withScheme = /^https?:\/\//i.test(homeserver) ? homeserver : `https://${homeserver}`;
    return withScheme.replace(/\/$/, "");
}

function socketUrl(homeserver: string): string {
    const base = shareOriginBase(homeserver);
    if (!base) {
        const proto = location.protocol === "https:" ? "wss:" : "ws:";
        return `${proto}//${location.host}/api/v1/active-shares/ws`;
    }
    return `${base.replace(/^http/i, "ws")}/api/v1/active-shares/ws`;
}

/**
 * One multiplexed WebSocket to a single homeserver, shared by every share
 * subscription on that host. Snapshots are keyed by active-share id; a value of
 * `undefined` means "not received yet" and `null` means the share is gone.
 */
class ShareHostSocket {
    readonly homeserver: string;

    #snapshots = $state<Record<string, ShareSnapshot | null>>({});
    #refCounts = new Map<string, number>();

    #socket: WebSocket | null = null;
    #reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    #reconnectAttempts = 0;

    constructor(homeserver: string) {
        this.homeserver = homeserver;
    }

    snapshot(activeShareId: string): ShareSnapshot | null | undefined {
        return this.#snapshots[activeShareId];
    }

    acquire(activeShareId: string) {
        this.#refCounts.set(activeShareId, (this.#refCounts.get(activeShareId) ?? 0) + 1);
        if (this.#socket == null) this.#connect();
        else this.#send({ type: "subscribe", active_share_ids: [activeShareId] });
    }

    release(activeShareId: string) {
        const next = (this.#refCounts.get(activeShareId) ?? 0) - 1;
        if (next > 0) {
            this.#refCounts.set(activeShareId, next);
            return;
        }
        this.#refCounts.delete(activeShareId);
        this.#send({ type: "unsubscribe", active_share_ids: [activeShareId] });
        const { [activeShareId]: _removed, ...rest } = this.#snapshots;
        this.#snapshots = rest;
        if (this.#refCounts.size === 0) this.#shutdown();
    }

    #send(message: { type: string; active_share_ids: string[] }) {
        if (this.#socket?.readyState === WebSocket.OPEN) {
            this.#socket.send(JSON.stringify(message));
        }
    }

    #connect() {
        if (this.#refCounts.size === 0) return;
        const existing = this.#socket;
        if (existing != null && (existing.readyState === WebSocket.OPEN || existing.readyState === WebSocket.CONNECTING)) return;

        const ws = new WebSocket(socketUrl(this.homeserver));
        this.#socket = ws;

        ws.onopen = () => {
            this.#reconnectAttempts = 0;
            // (Re)subscribe to everything currently held on this host.
            const ids = [...this.#refCounts.keys()];
            if (ids.length > 0) this.#send({ type: "subscribe", active_share_ids: ids });
        };

        ws.onmessage = (event) => {
            try {
                this.#handleMessage(JSON.parse(event.data) as ServerMessage);
            } catch (e) {
                console.error("Failed to parse share socket message", e);
            }
        };

        ws.onerror = () => ws.close();

        ws.onclose = () => {
            if (this.#socket === ws) this.#socket = null;
            this.#scheduleReconnect();
        };
    }

    #handleMessage(message: ServerMessage) {
        switch (message.type) {
            case "share.snapshot":
                this.#snapshots = { ...this.#snapshots, [message.active_share_id]: message.snapshot };
                break;
            case "share.gone":
                this.#snapshots = { ...this.#snapshots, [message.active_share_id]: null };
                break;
            default:
                console.warn("Unknown share socket message", message);
        }
    }

    #scheduleReconnect() {
        if (this.#refCounts.size === 0) return;
        this.#clearReconnect();
        const delay = Math.min(30_000, 1_000 * 2 ** this.#reconnectAttempts);
        this.#reconnectAttempts++;
        this.#reconnectTimer = setTimeout(() => this.#connect(), delay);
    }

    #clearReconnect() {
        if (this.#reconnectTimer != null) {
            clearTimeout(this.#reconnectTimer);
            this.#reconnectTimer = null;
        }
    }

    #shutdown() {
        this.#clearReconnect();
        this.#reconnectAttempts = 0;
        const ws = this.#socket;
        this.#socket = null;
        ws?.close();
        hostSockets.delete(this.homeserver);
    }
}

// One socket instance per homeserver, reused across all subscriptions.
const hostSockets = new Map<string, ShareHostSocket>();

function hostSocket(homeserver: string): ShareHostSocket {
    let socket = hostSockets.get(homeserver);
    if (socket == null) {
        socket = new ShareHostSocket(homeserver);
        hostSockets.set(homeserver, socket);
    }
    return socket;
}

/**
 * A single share's live snapshot, backed by the shared per-host socket. Open it
 * while the share is shown and close it on leave; the underlying socket is
 * reference-counted and torn down once its last subscription is released.
 */
export class ShareSubscription {
    readonly homeserver: string;
    readonly activeShareId: string;
    #host: ShareHostSocket | null = null;

    constructor(homeserver: string, activeShareId: string) {
        this.homeserver = homeserver;
        this.activeShareId = activeShareId;
    }

    open() {
        this.#host = hostSocket(this.homeserver);
        this.#host.acquire(this.activeShareId);
    }

    close() {
        this.#host?.release(this.activeShareId);
        this.#host = null;
    }

    /** `undefined` while loading, `null` if the share is gone, otherwise the snapshot. */
    get snapshot(): ShareSnapshot | null | undefined {
        return this.#host?.snapshot(this.activeShareId);
    }
}
