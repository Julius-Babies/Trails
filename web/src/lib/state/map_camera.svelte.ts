import {untrack} from "svelte";

/**
 * Position/size of the page content (the card overlaying the map), in CSS
 * pixels relative to the viewport. Used to inset the map's camera padding
 * so pins never end up hidden behind the card.
 */
export interface ContentRect {
    top: number;
    left: number;
    width: number;
    height: number;
}

/** Camera behaviour on the overview, with no device or share opened. */
export type GeneralCameraMode =
    /** Keep every device and share inside the visible map area. */
    | "tracking"
    /** The user drives the camera themselves. */
    | "manual";

/** Camera behaviour while a single device or share is open. */
export type DetailCameraMode =
    /** Follow the target at a readable zoom. */
    | "tracking"
    /** Keep the target's whole location history in view. */
    | "trail"
    /** The user drives the camera themselves. */
    | "manual";

/** Which of the two independent modes is currently driving the camera. */
export type CameraScope = "general" | "detail";

let contentRect = $state<ContentRect | null>(null);
let targetId = $state<string | null>(null);

// The two modes are deliberately separate state: switching to manual inside a
// device must not disturb how the overview behaves, and vice versa. Which one
// applies is decided by `targetId` alone.
//
// A detail view defaults to `trail`: opening a device is usually about where it
// has been, so the whole history is framed rather than just the latest position.
const DETAIL_DEFAULT_MODE: DetailCameraMode = "trail";

let generalMode = $state<GeneralCameraMode>("tracking");
let detailMode = $state<DetailCameraMode>(DETAIL_DEFAULT_MODE);

/** Store the current bounding box of the page content. */
export function setContentRect(rect: ContentRect | null) {
    contentRect = rect;
}

/**
 * Opens the detail scope for a device or share (highlighting its pin), or closes
 * it again with `null`. Opening a *different* target resets the detail camera to
 * {@link DETAIL_DEFAULT_MODE} — a freshly opened device should always frame
 * itself, rather than inheriting a `manual` choice made on some earlier one.
 */
export function setCameraTarget(id: string | null) {
    // The comparison is untracked on purpose: the detail pages call this from an
    // $effect, and a *tracked* read of `targetId` here would make that effect
    // depend on the very state it writes — an endless update loop.
    if (id != null && id !== untrack(() => targetId)) detailMode = DETAIL_DEFAULT_MODE;
    targetId = id;
}

export function setGeneralCameraMode(mode: GeneralCameraMode) {
    generalMode = mode;
}

export function setDetailCameraMode(mode: DetailCameraMode) {
    detailMode = mode;
}

/**
 * Hands the camera to the user, in whichever scope is currently driving it.
 * Called when they pan/zoom/rotate/pitch the map themselves.
 */
export function releaseCameraToUser() {
    if (targetId != null) detailMode = "manual";
    else generalMode = "manual";
}

export const mapCamera = {
    get contentRect() {
        return contentRect;
    },
    /** The opened device/share, or null on the overview. */
    get targetId() {
        return targetId;
    },
    get scope(): CameraScope {
        return targetId != null ? "detail" : "general";
    },
    get generalMode() {
        return generalMode;
    },
    get detailMode() {
        return detailMode;
    },
};
