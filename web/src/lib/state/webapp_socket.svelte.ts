import {currentUser, type User} from "$lib/state/current_user";

export interface Battery {
    percentage: number;
    is_charging: boolean;
}

export interface Address {
    road: string | null;
    house_number: string | null;
    postcode: string | null;
    city: string | null;
    state: string | null;
    country: string | null;
    display_name: string | null;
    label: string;
}

export interface LastLocation {
    latitude: number;
    longitude: number;
    found_at: number;
    address: Address | null;
}

export interface Device {
    [x: string]: any;
    id: string;
    manufacturer: string;
    model: string;
    display_name: string;
    friendly_name: string;
    battery: Battery | null;
    last_location: LastLocation | null;
}

/** A location share saved by the current user. Has its own name (not a
 * device's manufacturer/model naming); manufacturer/model are only for the
 * device image. `id` is the active-share id. */
export interface Share {
    id: string;
    name: string;
    manufacturer: string;
    model: string;
    battery: Battery | null;
    last_location: LastLocation | null;
}

/** A location share the current user has emitted (created) themselves. Carries
 * the share settings and how often it has been redeemed. Manufacturer/model are
 * only for the device image. */
export interface EmittedShare {
    id: string;
    name: string;
    device_id: string;
    device_display_name: string;
    manufacturer: string;
    model: string;
    location_history_seconds: number;
    share_battery_state: boolean;
    allow_multiuse: boolean;
    is_locked: boolean;
    created_at: number;
    redemption_count: number;
}

/** A saved share that lives on another homeserver — only its capability
 * (active-share id) and origin are known here; the data is fetched from the
 * origin over a per-host share socket. */
export interface ForeignShareRef {
    active_share_id: string;
    homeserver: string;
}

type ServerMessage =
    | {
          type: "devices.update";
          devices: Device[];
          shares?: Share[];
          emitted_shares?: EmittedShare[];
          foreign_shares?: ForeignShareRef[];
      };

let devices = $state<Device[]>([]);
let shares = $state<Share[]>([]);
let emittedShares = $state<EmittedShare[]>([]);
let foreignShares = $state<ForeignShareRef[]>([]);
let connected = $state(false);

let socket: WebSocket | null = null;
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
let reconnectAttempts = 0;
let shouldConnect = false;
let started = false;

function socketUrl(): string {
    const protocol = location.protocol === "https:" ? "wss:" : "ws:";
    return `${protocol}//${location.host}/api/v1/webapp/ws`;
}

function clearReconnect() {
    if (reconnectTimer != null) {
        clearTimeout(reconnectTimer);
        reconnectTimer = null;
    }
}

function scheduleReconnect() {
    if (!shouldConnect) return;
    clearReconnect();
    const delay = Math.min(30_000, 1_000 * 2 ** reconnectAttempts);
    reconnectAttempts++;
    reconnectTimer = setTimeout(connect, delay);
}

function handleMessage(message: ServerMessage) {
    switch (message.type) {
        case "devices.update":
            devices = message.devices;
            shares = message.shares ?? [];
            emittedShares = message.emitted_shares ?? [];
            foreignShares = message.foreign_shares ?? [];
            break;
        default:
            console.warn("Unknown webapp socket message", message);
    }
}

function connect() {
    if (!shouldConnect) return;
    if (socket != null && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) return;

    const ws = new WebSocket(socketUrl());
    socket = ws;

    ws.onopen = () => {
        reconnectAttempts = 0;
        connected = true;
    };

    ws.onmessage = (event) => {
        try {
            handleMessage(JSON.parse(event.data) as ServerMessage);
        } catch (e) {
            console.error("Failed to parse webapp socket message", e);
        }
    };

    ws.onerror = () => {
        ws.close();
    };

    ws.onclose = () => {
        if (socket === ws) socket = null;
        connected = false;
        scheduleReconnect();
    };
}

function open() {
    shouldConnect = true;
    reconnectAttempts = 0;
    connect();
}

function close() {
    shouldConnect = false;
    clearReconnect();
    reconnectAttempts = 0;
    connected = false;
    devices = [];
    shares = [];
    emittedShares = [];
    foreignShares = [];
    if (socket != null) {
        const ws = socket;
        socket = null;
        ws.close();
    }
}

/**
 * Starts watching the current user and keeps a websocket connection open
 * (with automatic reconnect) while a user is signed in. Safe to call multiple
 * times — only the first call attaches the subscription.
 */
export function startWebappSocket() {
    if (started) return;
    started = true;
    currentUser.subscribe((user: User | null) => {
        if (user != null) open();
        else close();
    });
}

export const webappSocket = {
    get devices() {
        return devices;
    },
    get shares() {
        return shares;
    },
    get emittedShares() {
        return emittedShares;
    },
    get foreignShares() {
        return foreignShares;
    },
    get connected() {
        return connected;
    },
};
