import type {Address, Battery, LastLocation, Share} from "$lib/state/webapp_socket.svelte";

/**
 * A saved share that lives on a foreign homeserver, forwarded by our own server
 * as a bare reference. The webapp resolves the actual data (share/device/owner)
 * and the live location directly from {@link homeserver} — the homeserver only
 * keeps the reference as a backup.
 */
export interface ForeignShareRef {
    /** Local saved-share row id — the stable key used for the pin and the list. */
    id: string;
    /** Active-share id on the foreign homeserver; the capability + subscribe key. */
    active_share_id: string;
    /** Bare federation host of the owning homeserver, e.g. `example.com`. */
    homeserver: string;
}

/** Static, REST-resolved data for one foreign share (everything but the location). */
interface ResolvedShare {
    name: string;
    deviceDisplayName: string;
    ownerUsername: string;
    manufacturer: string;
    model: string;
    shareBatteryState: boolean;
}

/** Live location/battery for one foreign share, pushed over the app socket. */
interface LiveShare {
    lastLocation: LastLocation | null;
    battery: Battery | null;
}

/** The app socket's `share.snapshot` message (see TrailsWebSocketServerMessage). */
interface SnapshotMessage {
    type: "share.snapshot";
    target: { type: string; id: string };
    timestamp: number; // epoch SECONDS
    location: { latitude: number; longitude: number; address: Address | null };
    battery_state: { percentage: number; is_charging: boolean } | null;
}

// Resolved foreign shares, exposed reactively as ready-to-render Share objects.
let shares = $state<Share[]>([]);

// active_share_id -> saved-share reference (routes snapshots to the right row).
const refs = new Map<string, ForeignShareRef>();
// active_share_id -> static REST data (absent until resolved).
const resolved = new Map<string, ResolvedShare>();
// active_share_id -> latest live location/battery.
const live = new Map<string, LiveShare>();
// homeserver -> the single multiplexed socket connection for that host.
const connections = new Map<string, HostConnection>();

/** Rebuild the reactive `shares` array from the current maps. */
function rebuild() {
    const list: Share[] = [];
    for (const [activeShareId, ref] of refs) {
        const r = resolved.get(activeShareId);
        if (r == null) continue; // not resolved yet — hide until we have its data
        const l = live.get(activeShareId);
        list.push({
            id: ref.id,
            name: r.name,
            device_display_name: r.deviceDisplayName,
            owner_username: r.ownerUsername,
            manufacturer: r.manufacturer,
            model: r.model,
            battery: r.shareBatteryState ? (l?.battery ?? null) : null,
            last_location: l?.lastLocation ?? null,
        });
    }
    shares = list;
}

async function fetchJson(url: string): Promise<any> {
    const response = await fetch(url);
    if (!response.ok) throw new Error(`${url} -> ${response.status}`);
    return response.json();
}

/**
 * Resolve a foreign share's static data directly from its homeserver by walking
 * active-share -> share -> device -> owner.
 */
async function resolveShare(ref: ForeignShareRef) {
    const base = `https://${ref.homeserver}/api/v1`;
    try {
        const activeShare = await fetchJson(`${base}/active-shares/${ref.active_share_id}`);
        const share = await fetchJson(`${base}/share/${activeShare.share_id}`);
        const device = await fetchJson(`${base}/devices/${share.device_id}`);
        const user = await fetchJson(`${base}/users/${device.owner_id}`);

        // The ref may have been removed while we were fetching.
        if (!refs.has(ref.active_share_id)) return;

        resolved.set(ref.active_share_id, {
            name: share.share_name,
            deviceDisplayName: device.display_name,
            ownerUsername: user.username,
            manufacturer: device.manufacturer,
            model: device.model,
            shareBatteryState: share.share_battery_state,
        });
        rebuild();
    } catch (e) {
        console.error("Failed to resolve foreign share", ref, e);
    }
}

/** One websocket to a foreign homeserver, multiplexing all its saved shares. */
class HostConnection {
    private ws: WebSocket | null = null;
    private shouldConnect = true;
    private reconnectAttempts = 0;
    private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
    // Active-share ids already subscribed on the current connection.
    private subscribed = new Set<string>();
    // Active-share ids we want subscribed (survives reconnects).
    private readonly desired = new Set<string>();

    constructor(private readonly host: string) {
        this.connect();
    }

    private url(): string {
        return `wss://${this.host}/api/v1/app/ws`;
    }

    private connect() {
        if (!this.shouldConnect) return;
        const ws = new WebSocket(this.url());
        this.ws = ws;

        ws.onopen = () => {
            this.reconnectAttempts = 0;
            this.subscribed.clear();
            this.flush();
        };
        ws.onmessage = (event) => this.handleMessage(event);
        ws.onerror = () => ws.close();
        ws.onclose = () => {
            if (this.ws === ws) this.ws = null;
            this.subscribed.clear();
            this.scheduleReconnect();
        };
    }

    private scheduleReconnect() {
        if (!this.shouldConnect) return;
        if (this.reconnectTimer != null) clearTimeout(this.reconnectTimer);
        const delay = Math.min(30_000, 1_000 * 2 ** this.reconnectAttempts);
        this.reconnectAttempts++;
        this.reconnectTimer = setTimeout(() => this.connect(), delay);
    }

    /** Reconcile the subscription set on the wire with what we want. */
    private flush() {
        if (this.ws == null || this.ws.readyState !== WebSocket.OPEN) return;

        const toSubscribe = [...this.desired].filter((id) => !this.subscribed.has(id));
        if (toSubscribe.length > 0) {
            this.ws.send(JSON.stringify({type: "appsocket.subscribe-shares", share_ids: toSubscribe}));
            toSubscribe.forEach((id) => this.subscribed.add(id));
        }

        const toUnsubscribe = [...this.subscribed].filter((id) => !this.desired.has(id));
        if (toUnsubscribe.length > 0) {
            this.ws.send(JSON.stringify({type: "appsocket.unsubscribe-share", share_ids: toUnsubscribe}));
            toUnsubscribe.forEach((id) => this.subscribed.delete(id));
        }
    }

    private handleMessage(event: MessageEvent) {
        let message: SnapshotMessage;
        try {
            message = JSON.parse(event.data);
        } catch (e) {
            console.error("Failed to parse foreign app socket message", e);
            return;
        }
        if (message.type !== "share.snapshot" || message.target?.type !== "share") return;

        const activeShareId = message.target.id;
        if (!refs.has(activeShareId)) return;

        live.set(activeShareId, {
            lastLocation: {
                latitude: message.location.latitude,
                longitude: message.location.longitude,
                // The app socket reports seconds; the rest of the app uses millis.
                found_at: message.timestamp * 1000,
                // Reverse-geocoded by the owning homeserver and shipped with the snapshot.
                address: message.location.address ?? null,
            },
            battery: message.battery_state
                ? {percentage: message.battery_state.percentage, is_charging: message.battery_state.is_charging}
                : null,
        });
        rebuild();
    }

    add(activeShareId: string) {
        this.desired.add(activeShareId);
        this.flush();
    }

    remove(activeShareId: string) {
        this.desired.delete(activeShareId);
        this.flush();
    }

    get isEmpty(): boolean {
        return this.desired.size === 0;
    }

    close() {
        this.shouldConnect = false;
        if (this.reconnectTimer != null) clearTimeout(this.reconnectTimer);
        this.desired.clear();
        this.subscribed.clear();
        if (this.ws != null) {
            const ws = this.ws;
            this.ws = null;
            ws.close();
        }
    }
}

/**
 * Reconcile the set of foreign shares with the refs forwarded by our homeserver:
 * resolve new ones directly from their homeserver, open/close per-host sockets,
 * and drop shares that are no longer saved.
 */
export function syncForeignShares(nextRefs: ForeignShareRef[]) {
    const wanted = new Set(nextRefs.map((r) => r.active_share_id));

    // Remove foreign shares that vanished.
    for (const activeShareId of [...refs.keys()]) {
        if (wanted.has(activeShareId)) continue;
        const ref = refs.get(activeShareId)!;
        refs.delete(activeShareId);
        resolved.delete(activeShareId);
        live.delete(activeShareId);
        const connection = connections.get(ref.homeserver);
        connection?.remove(activeShareId);
        if (connection != null && connection.isEmpty) {
            connection.close();
            connections.delete(ref.homeserver);
        }
    }

    // Add newly saved foreign shares.
    for (const ref of nextRefs) {
        if (refs.has(ref.active_share_id)) continue;
        refs.set(ref.active_share_id, ref);

        let connection = connections.get(ref.homeserver);
        if (connection == null) {
            connection = new HostConnection(ref.homeserver);
            connections.set(ref.homeserver, connection);
        }
        connection.add(ref.active_share_id);

        void resolveShare(ref);
    }

    rebuild();
}

/** Tear everything down (e.g. on sign-out). */
export function closeForeignShares() {
    for (const connection of connections.values()) connection.close();
    connections.clear();
    refs.clear();
    resolved.clear();
    live.clear();
    shares = [];
}

export const foreignShares = {
    get list(): Share[] {
        return shares;
    },
};
