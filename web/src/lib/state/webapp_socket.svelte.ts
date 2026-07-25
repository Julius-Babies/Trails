import {currentUser, type User} from "$lib/state/current_user";

export interface Device {
    id: string;
    manufacturer: string;
    model: string;
    display_name: string;
    friendly_name: string;
}

type ServerMessage =
    | { type: "devices.update"; devices: Device[] };

let devices = $state<Device[]>([]);
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
    get connected() {
        return connected;
    },
};
