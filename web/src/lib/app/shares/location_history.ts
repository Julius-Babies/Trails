/**
 * How much location history a share reveals, encoded the way the server stores
 * it (`Share.locationHistorySeconds`):
 * - `0` — no history at all, only the current location.
 * - negative — an unbounded window (the app writes `Duration.INFINITE` this way).
 * - otherwise — a window in seconds.
 *
 * The options mirror the steps the app offers when creating a share, so a share
 * edited in the browser can still be described by the app's slider.
 */
export const INFINITE_HISTORY_SECONDS = -1;

export interface LocationHistoryOption {
    seconds: number;
    /** Short label for pickers. */
    label: string;
}

export const LOCATION_HISTORY_OPTIONS: LocationHistoryOption[] = [
    {seconds: 0, label: "Nur aktueller Standort"},
    {seconds: 60 * 60, label: "Letzte Stunde"},
    {seconds: 6 * 60 * 60, label: "Letzte 6 Stunden"},
    {seconds: 24 * 60 * 60, label: "Letzter Tag"},
    {seconds: 7 * 24 * 60 * 60, label: "Letzte Woche"},
    {seconds: INFINITE_HISTORY_SECONDS, label: "Vollständiger Verlauf"},
];

/**
 * Describes an arbitrary retention value — not just the {@link LOCATION_HISTORY_OPTIONS}
 * steps, since a share may have been created with any window.
 */
export function locationHistoryLabel(seconds: number): string {
    if (seconds < 0) return "Vollständiger Verlauf";
    if (seconds === 0) return "Nur aktueller Standort";

    const known = LOCATION_HISTORY_OPTIONS.find((option) => option.seconds === seconds);
    if (known) return known.label;

    const hours = Math.round(seconds / 3600);
    if (hours >= 24) {
        const days = Math.round(hours / 24);
        return `Letzte ${days} ${days === 1 ? "Tag" : "Tage"}`;
    }
    if (hours >= 1) return `Letzte ${hours} ${hours === 1 ? "Stunde" : "Stunden"}`;
    const minutes = Math.max(1, Math.round(seconds / 60));
    return `Letzte ${minutes} min`;
}

/**
 * The pickable options for a share, with its current value folded in: a share
 * created with a window outside the standard steps keeps that value selectable
 * instead of silently jumping to the nearest step.
 */
export function locationHistoryOptionsFor(seconds: number): LocationHistoryOption[] {
    if (LOCATION_HISTORY_OPTIONS.some((option) => option.seconds === seconds)) {
        return LOCATION_HISTORY_OPTIONS;
    }
    // Unbounded is already covered by the negative check in the label helper, so
    // any custom value is a positive window and sorts between the steps.
    const custom = {seconds, label: locationHistoryLabel(seconds)};
    return [...LOCATION_HISTORY_OPTIONS, custom].sort((a, b) => {
        if (a.seconds < 0) return 1;
        if (b.seconds < 0) return -1;
        return a.seconds - b.seconds;
    });
}
