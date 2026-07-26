import {currentUser, type User} from "$lib/state/current_user";

/** Confirmed ring state for a single device, as reported by the device itself. */
export interface RingState {
    isRinging: boolean;
    ringedBy: string;
}

type RingSocketMessage =
    | { type: "ring.state"; device_id: string; is_ringing: boolean; ringed_by: string };

let ringStates = $state<Record<string, RingState>>({});
let connected = $state(false);

let socket: WebSocket | null = null;
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
let reconnectAttempts = 0;
let shouldConnect = false;
let started = false;

function socketUrl(): string {
    const protocol = location.protocol === "https:" ? "wss:" : "ws:";
    return `${protocol}//${location.host}/api/v1/ring/ws`;
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

function handleMessage(message: RingSocketMessage) {
    switch (message.type) {
        case "ring.state":
            if (message.is_ringing) {
                ringStates = { ...ringStates, [message.device_id]: { isRinging: true, ringedBy: message.ringed_by } };
            } else {
                const { [message.device_id]: _removed, ...rest } = ringStates;
                ringStates = rest;
            }
            break;
        default:
            console.warn("Unknown ring socket message", message);
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
            handleMessage(JSON.parse(event.data) as RingSocketMessage);
        } catch (e) {
            console.error("Failed to parse ring socket message", e);
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
    ringStates = {};
    if (socket != null) {
        const ws = socket;
        socket = null;
        ws.close();
    }
}

/**
 * Keeps a websocket to the ring-state channel open while a user is signed in.
 * Safe to call multiple times — only the first call attaches the subscription.
 */
export function startRingSocket() {
    if (started) return;
    started = true;
    currentUser.subscribe((user: User | null) => {
        if (user != null) open();
        else close();
    });
}

export const ringSocket = {
    get ringStates() {
        return ringStates;
    },
    isRinging(deviceId: string): boolean {
        return ringStates[deviceId]?.isRinging ?? false;
    },
    get connected() {
        return connected;
    },
};
