import type {HistoryPoint} from "$lib/api/history/history_repository";

let points = $state<HistoryPoint[]>([]);

/**
 * Publishes the location history that should be drawn as a line on the map.
 * Pass `null` (or an empty list) to clear it again — the detail views do that on
 * leave, so the trail lives exactly as long as the view that loaded it.
 *
 * Only one trail is shown at a time; there is only ever one open detail view.
 */
export function setMapTrail(next: HistoryPoint[] | null) {
    points = next ?? [];
}

/** The trail currently shown on the map (reactive), oldest point first. */
export const mapTrail = {
    get points() {
        return points;
    },
};
