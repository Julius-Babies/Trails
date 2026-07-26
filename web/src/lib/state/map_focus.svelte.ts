/**
 * Position/size of the page content (the card overlaying the map), in CSS
 * pixels relative to the viewport. Used to inset the map's `fitBounds` padding
 * so pins never end up hidden behind the card.
 */
export interface ContentRect {
    top: number;
    left: number;
    width: number;
    height: number;
}

let active = $state(true);
let contentRect = $state<ContentRect | null>(null);

/** Toggle the "keep all devices in view" mode. */
export function toggleMapFocus() {
    active = !active;
}

/** Turn the focus mode off (e.g. when the user manually pans/zooms the map). */
export function disableMapFocus() {
    active = false;
}

/** Store the current bounding box of the page content. */
export function setContentRect(rect: ContentRect | null) {
    contentRect = rect;
}

export const mapFocus = {
    get active() {
        return active;
    },
    get contentRect() {
        return contentRect;
    },
};
