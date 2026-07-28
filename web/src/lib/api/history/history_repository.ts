import requireResponseIsFromTrails from "$lib/api/requireResponseIsFromTrails";
import {shareOriginBase} from "$lib/api/shares/get_share_snapshot";
import type {Battery} from "$lib/state/webapp_socket.svelte";

/**
 * One recorded position of a device. `timestamp` is epoch **milliseconds**,
 * matching `LastLocation.found_at`.
 *
 * `battery` is only present when the caller may see the battery state (always for
 * the user's own devices, for a share only when it opted in) and the device
 * actually reported it.
 */
export interface HistoryPoint {
    timestamp: number;
    latitude: number;
    longitude: number;
    location_accuracy: number;
    bearing: number;
    bearing_accuracy: number | null;
    battery: Battery | null;
}

/** A device's recorded location history, oldest point first. */
export interface LocationHistory {
    /**
     * The retention window the server applied, in seconds. `null` means nothing
     * was cut off — the caller's own device, or a share with an unbounded window.
     */
    history_seconds: number | null;
    points: HistoryPoint[];
}

async function readHistory(response: Response): Promise<LocationHistory | null> {
    if (!response.ok) return null;
    try {
        return await response.json() as LocationHistory;
    } catch {
        return null;
    }
}

/**
 * Location-history calls. Keeping them here means UI components never touch
 * `fetch` directly — they depend on this repository instead.
 *
 * History is a one-shot read, deliberately not part of the snapshot sockets:
 * it is fetched when a detail view opens and does not update live.
 */
export const HistoryRepository = {
    /**
     * The complete history of one of the current user's own devices. Owners are
     * never limited, so `history_seconds` is always null here. Resolves `null` on
     * any failure (network error, unknown device, someone else's device).
     */
    async forDevice(deviceId: string): Promise<LocationHistory | null> {
        let response: Response;
        try {
            response = await fetch(`/api/v1/devices/${deviceId}/history`);
        } catch {
            return null;
        }
        requireResponseIsFromTrails(response);
        return readHistory(response);
    },

    /**
     * The history a redeemed share is allowed to see, fetched straight from its
     * origin homeserver (`homeserver` is empty for a same-server share). The
     * active-share id is the capability, so no auth is sent — and because the
     * response may be cross-origin, the `X-Trails-Origin` marker is not checked
     * (custom headers aren't readable cross-origin), same as the share snapshot.
     *
     * How far back the server goes is the share's decision, reported back as
     * `history_seconds`. Resolves `null` on any failure (unknown/returned share,
     * network or CORS error).
     */
    async forShare(shareId: string, homeserver: string): Promise<LocationHistory | null> {
        const base = shareOriginBase(homeserver);
        let response: Response;
        try {
            response = await fetch(`${base}/api/v1/active-shares/${shareId}/history`);
        } catch {
            return null;
        }
        return readHistory(response);
    },
};
