<script lang="ts">
    import { onMount, mount, unmount } from "svelte";
    import { MediaQuery } from "svelte/reactivity";
    import mapboxgl from "mapbox-gl";
    import "mapbox-gl/dist/mapbox-gl.css";
    import { getMapboxToken } from "$lib/api/mapbox/get_mapbox_token";
    import { webappSocket, shareMainText } from "$lib/state/webapp_socket.svelte";
    import { foreignShares, shareOriginBase } from "$lib/state/share_socket.svelte";
    import { mapFocus, disableMapFocus } from "$lib/state/map_focus.svelte";
    import { mapTrail } from "$lib/state/map_trail.svelte";
    import type { HistoryPoint } from "$lib/api/history/history_repository";
    import MapPin from "./MapPin.svelte";
    import mapDark from "$lib/assets/map-dark.png";
    import mapLight from "$lib/assets/map-light.png";

    let mapContainer: HTMLDivElement | null = $state(null);
    let showPlaceholder = $state(false);
    let map: mapboxgl.Map | undefined = $state();

    // One mapbox marker + mounted MapPin per device/share that has a location.
    type PinEntry = { marker: mapboxgl.Marker; component: Record<string, any> };
    const pins = new Map<string, PinEntry>();

    // MapPin's rendered SVG size. The marker is anchored at its bottom tip,
    // so a pin overhangs its coordinate by the full height upwards and half its
    // width to each side (and nothing below). Used to pad fitBounds so the whole
    // pin stays visible, not just its anchor point.
    const PIN_WIDTH = 60;
    const PIN_HEIGHT = 67;

    function removePin(id: string) {
        const entry = pins.get(id);
        if (entry == null) return;
        entry.marker.remove();
        unmount(entry.component);
        pins.delete(id);
    }

    const darkMode = new MediaQuery("(prefers-color-scheme: dark)");
    const style = $derived(
        darkMode.current
            ? "mapbox://styles/mapbox/traffic-night-v2"
            : "mapbox://styles/mapbox/standard"
    );

    // The location-history line: a light casing under a solid stroke, so the
    // trail stays legible over both map styles. The colours mirror the theme's
    // `--primary` / `--background` (layout.css) as hex, because mapbox-gl cannot
    // parse the oklch() values those tokens are written in.
    const TRAIL_SOURCE = "location-history";
    const TRAIL_CASING_LAYER = "location-history-casing";
    const TRAIL_LINE_LAYER = "location-history-line";
    const trailColors = $derived(
        darkMode.current
            ? { line: "#e2e8f0", casing: "#020617" }
            : { line: "#0f172a", casing: "#ffffff" }
    );

    // Counts style loads: the initial one and each dark-mode swap. A style change
    // drops custom sources and layers, so the trail effect depends on this to
    // know when to (re)add them. A counter rather than a boolean, so a *second*
    // load is also a change the effect can see.
    let styleEpoch = $state(0);

    // Minimal GeoJSON shape for the trail. Spelled out locally because
    // @types/geojson isn't a dependency, so the global `GeoJSON` namespace that
    // mapbox-gl's own typings reference is unavailable here.
    type TrailData = {
        type: "FeatureCollection";
        features: {
            type: "Feature";
            properties: Record<string, never>;
            geometry: { type: "LineString"; coordinates: number[][] };
        }[];
    };

    function trailData(coordinates: number[][]): TrailData {
        return {
            type: "FeatureCollection",
            // A LineString needs at least two positions; fewer means nothing to draw.
            features: coordinates.length < 2
                ? []
                : [{ type: "Feature", properties: {}, geometry: { type: "LineString", coordinates } }]
        };
    }

    function toCoordinates(points: HistoryPoint[]): number[][] {
        return points.map((point) => [point.longitude, point.latitude]);
    }

    /** Keeps the trail below the style's labels so road/place names stay readable. */
    function firstSymbolLayerId(currentMap: mapboxgl.Map): string | undefined {
        return currentMap.getStyle()?.layers?.find((layer) => layer.type === "symbol")?.id;
    }

    /**
     * Adds the trail's source and layers if they aren't there yet. Returns whether
     * the map is ready to be drawn on: adding throws while a style swap is
     * mid-flight, and the `style.load` that follows bumps `styleEpoch` and re-runs
     * the caller, so skipping a beat here is safe.
     */
    function addTrailLayers(currentMap: mapboxgl.Map): boolean {
        if (currentMap.getSource(TRAIL_SOURCE) != null) return true;

        try {
            currentMap.addSource(TRAIL_SOURCE, { type: "geojson", data: trailData([]) });

            // `slot` positions the layers in the v3 "standard" style (which imports
            // its basemap, so it exposes no symbol layers to sort against); the
            // beforeId does the same job in the classic night style.
            const beforeId = firstSymbolLayerId(currentMap);
            currentMap.addLayer({
                id: TRAIL_CASING_LAYER,
                type: "line",
                slot: "middle",
                source: TRAIL_SOURCE,
                layout: { "line-cap": "round", "line-join": "round" },
                paint: { "line-color": trailColors.casing, "line-width": 7, "line-opacity": 0.7 }
            }, beforeId);
            currentMap.addLayer({
                id: TRAIL_LINE_LAYER,
                type: "line",
                slot: "middle",
                source: TRAIL_SOURCE,
                layout: { "line-cap": "round", "line-join": "round" },
                paint: { "line-color": trailColors.line, "line-width": 3.5, "line-opacity": 0.9 }
            }, beforeId);
            return true;
        } catch {
            return false;
        }
    }

    function setTrailCoordinates(currentMap: mapboxgl.Map, coordinates: number[][]) {
        const source = currentMap.getSource(TRAIL_SOURCE);
        if (source?.type === "geojson") source.setData(trailData(coordinates));
    }

    const TRAIL_ANIMATION_MS = 2000;

    /**
     * Heavy ease-out: the line shoots ahead immediately and spends most of the
     * two seconds settling into its final shape.
     */
    function easeOutExpo(t: number): number {
        return t >= 1 ? 1 : 1 - Math.pow(2, -10 * t);
    }

    /**
     * Cumulative length along the trail. Longitude is scaled by the latitude's
     * cosine so a degree of lng counts for what it's actually worth on screen —
     * otherwise the tip would race through east-west stretches.
     */
    function cumulativeLengths(coordinates: number[][]): number[] {
        const lengths = [0];
        for (let i = 1; i < coordinates.length; i++) {
            const [previousLng, previousLat] = coordinates[i - 1];
            const [lng, lat] = coordinates[i];
            const scale = Math.cos(((previousLat + lat) / 2) * (Math.PI / 180));
            const dx = (lng - previousLng) * scale;
            const dy = lat - previousLat;
            lengths.push(lengths[i - 1] + Math.hypot(dx, dy));
        }
        return lengths;
    }

    /**
     * The trail truncated to `progress` (0–1) of its total length, interpolating
     * within the final segment so the tip glides instead of hopping vertex to
     * vertex.
     */
    function trailUpTo(coordinates: number[][], lengths: number[], progress: number): number[][] {
        const total = lengths[lengths.length - 1];
        if (progress >= 1 || total === 0) return coordinates;

        const target = total * progress;
        // The last vertex at or before the target — everything up to it is kept.
        let index = 0;
        while (index + 1 < lengths.length && lengths[index + 1] <= target) index++;

        const head = coordinates.slice(0, index + 1);
        const next = coordinates[index + 1];
        if (next == null) return head;

        const segment = lengths[index + 1] - lengths[index];
        const fraction = segment === 0 ? 0 : (target - lengths[index]) / segment;
        const [lng, lat] = coordinates[index];
        head.push([lng + (next[0] - lng) * fraction, lat + (next[1] - lat) * fraction]);
        return head;
    }

    const reducedMotion = new MediaQuery("(prefers-reduced-motion: reduce)");
    let trailFrame: number | null = null;

    function cancelTrailAnimation() {
        if (trailFrame != null) cancelAnimationFrame(trailFrame);
        trailFrame = null;
    }

    /** Grows the trail in from its oldest point over {@link TRAIL_ANIMATION_MS}. */
    function drawTrail(currentMap: mapboxgl.Map, points: HistoryPoint[], animate: boolean) {
        cancelTrailAnimation();

        const coordinates = toCoordinates(points);
        if (!animate || coordinates.length < 2) {
            setTrailCoordinates(currentMap, coordinates);
            return;
        }

        const lengths = cumulativeLengths(coordinates);
        const start = performance.now();
        const step = (now: number) => {
            const t = Math.min(1, (now - start) / TRAIL_ANIMATION_MS);
            setTrailCoordinates(currentMap, trailUpTo(coordinates, lengths, easeOutExpo(t)));
            trailFrame = t < 1 ? requestAnimationFrame(step) : null;
        };
        // Start from nothing so the first frame doesn't flash the full line.
        setTrailCoordinates(currentMap, []);
        trailFrame = requestAnimationFrame(step);
    }

    onMount(() => {
        getMapboxToken().then((accessToken) => {
            if (accessToken == null) {
                showPlaceholder = true;
                return;
            }

            map = new mapboxgl.Map({
                accessToken,
                container: mapContainer!,
                style,
                projection: "globe",
                center: [13.7373, 51.0504],
                zoom: 11
            });

            // Any manual camera interaction cancels focus mode. Programmatic
            // camera moves (our own fitBounds) have no `originalEvent`, so they
            // don't trip this.
            const onUserInteraction = (e: { originalEvent?: unknown }) => {
                if (e.originalEvent != null) disableMapFocus();
            };
            map.on("dragstart", onUserInteraction);
            map.on("zoomstart", onUserInteraction);
            map.on("rotatestart", onUserInteraction);
            map.on("pitchstart", onUserInteraction);

            // Fires for the initial style and again after every setStyle.
            map.on("style.load", () => styleEpoch++);
        });

        return () => {
            cancelTrailAnimation();
            for (const id of [...pins.keys()]) removePin(id);
            map?.remove();
        };
    });

    $effect(() => {
        map?.setStyle(style);
    });

    // The point set the grow-in animation last played for, so a style swap can
    // restore the finished line instead of replaying it.
    let animatedTrail: HistoryPoint[] | null = null;

    // Draw the published location history (see setMapTrail). Clearing it on
    // teardown is what removes the line when the detail view navigates away —
    // and the effect re-runs after a style swap wiped the layers.
    $effect(() => {
        // All three dependencies are read into locals up front, before any early
        // return, so the effect re-runs no matter which of them settles last.
        // `styleEpoch` in particular must be a real read (not a bare reference):
        // on a direct page load the map needs a token fetch plus a style download,
        // so the history is usually published *first* and the draw below only
        // becomes possible once the style load bumps the epoch.
        const currentMap = map;
        const epoch = styleEpoch;
        const points = mapTrail.points;

        // `style.load` (epoch > 0) is the signal that a style is in place, and
        // deliberately not isStyleLoaded() — that one also waits for every tile to
        // arrive, so on a direct page load it is still false when the style is
        // long ready, and nothing would ever retry.
        if (currentMap == null || epoch === 0) return;
        if (!addTrailLayers(currentMap)) return;

        // Animate a newly opened trail; a re-run for the same points (dark-mode
        // style swap) just puts the finished line back.
        const isNewTrail = points !== animatedTrail;
        animatedTrail = points;
        drawTrail(currentMap, points, isNewTrail && !reducedMotion.current);

        return () => {
            cancelTrailAnimation();
            // The map (or just its style) may already be gone — on component
            // teardown, or mid-swap between two styles.
            try {
                setTrailCoordinates(currentMap, []);
            } catch {
                // Nothing to clear.
            }
        };
    });

    // Add or move the marker for one entity (own device or share). The pin id
    // is the entity's own id; each kind renders its own pin component.
    function upsertPin(
        currentMap: mapboxgl.Map,
        id: string,
        location: { longitude: number; latitude: number },
        makeComponent: (target: HTMLElement) => Record<string, any>
    ) {
        const lngLat: [number, number] = [location.longitude, location.latitude];
        const existing = pins.get(id);
        if (existing != null) {
            existing.marker.setLngLat(lngLat);
            return;
        }

        const element = document.createElement("div");
        const component = makeComponent(element);
        const marker = new mapboxgl.Marker({ element, anchor: "bottom" })
            .setLngLat(lngLat)
            .addTo(currentMap);
        pins.set(id, { marker, component });
    }

    // Keep a pin on the map for every own device and share that has a location.
    $effect(() => {
        const currentMap = map;
        if (currentMap == null) return;

        const seen = new Set<string>();

        for (const device of webappSocket.devices) {
            const location = device.last_location;
            if (location == null) continue;
            seen.add(device.id);
            upsertPin(currentMap, device.id, location, (target) =>
                mount(MapPin, {
                    target,
                    props: {
                        id: device.id,
                        label: device.name,
                        imageUrl: `/api/v1/devices/image/${device.manufacturer}-${device.model}`,
                        href: `/devices/${device.id}`
                    }
                })
            );
        }

        for (const share of webappSocket.shares) {
            const location = share.last_location;
            if (location == null) continue;
            seen.add(share.id);
            upsertPin(currentMap, share.id, location, (target) =>
                mount(MapPin, {
                    target,
                    props: {
                        id: share.id,
                        label: shareMainText(share),
                        imageUrl: `/api/v1/devices/image/${share.manufacturer}-${share.model}`,
                        href: `/share/${share.id}`
                    }
                })
            );
        }

        for (const entry of foreignShares.entries) {
            const snapshot = entry.subscription.snapshot;
            const location = snapshot?.last_location;
            if (snapshot == null || location == null) continue;
            seen.add(entry.activeShareId);
            const base = shareOriginBase(entry.homeserver);
            upsertPin(currentMap, entry.activeShareId, location, (target) =>
                mount(MapPin, {
                    target,
                    props: {
                        id: entry.activeShareId,
                        label: shareMainText(snapshot),
                        imageUrl: `${base}/api/v1/devices/image/${snapshot.manufacturer}-${snapshot.model}`,
                        href: `/share/${entry.activeShareId}?homeserver=${encodeURIComponent(entry.homeserver)}`
                    }
                })
            );
        }

        // Drop pins for entities that vanished or lost their location.
        for (const id of [...pins.keys()]) {
            if (!seen.has(id)) removePin(id);
        }
    });

    // Turn the card's bounding box into fitBounds padding insets (in pixels).
    // We reserve only the *single* edge the card is docked against, so the
    // remaining space stays a clean rectangle. fitBounds then centres the
    // devices inside it, giving an even margin (border) on all sides.
    //
    // fitBounds only fits the pins' anchor points, so we also add each pin's
    // overhang (top/sides) to the base margin to keep the whole pin visible.
    function focusPadding(currentMap: mapboxgl.Map) {
        const gap = 16;
        const pinX = PIN_WIDTH / 2; // pin half-width around its anchor
        const pinTop = PIN_HEIGHT;  // pin height above its anchor
        const rect = mapFocus.contentRect;

        const padding = {
            top: gap + pinTop,
            right: gap + pinX,
            bottom: gap, // anchor sits at the pin's bottom tip → no overhang below
            left: gap + pinX
        };
        if (rect == null || rect.width === 0 || rect.height === 0) return padding;

        const { clientWidth: w, clientHeight: h } = currentMap.getContainer();
        const cardRight = rect.left + rect.width;
        const cardBottom = rect.top + rect.height;

        if (rect.width <= rect.height) {
            // Tall card → a vertical strip; reserve the left or right column.
            if (rect.left <= w - cardRight) padding.left = cardRight + gap + pinX;
            else padding.right = w - rect.left + gap + pinX;
        } else {
            // Wide card → a horizontal strip; reserve the top or bottom row.
            if (rect.top <= h - cardBottom) padding.top = cardBottom + gap + pinTop;
            else padding.bottom = h - rect.top + gap;
        }

        // If the card covers (almost) the whole viewport there is no free area
        // to fit into — fall back to the base margin so fitBounds stays valid.
        const minFree = 48;
        if (padding.left + padding.right > w - minFree) { padding.left = gap + pinX; padding.right = gap + pinX; }
        if (padding.top + padding.bottom > h - minFree) { padding.top = gap + pinTop; padding.bottom = gap; }

        return padding;
    }

    // While focus mode is on, keep every device inside the visible map area —
    // re-running on location updates and card resizes. Steps aside while a
    // single device is focused (its own effect drives the camera then).
    $effect(() => {
        const currentMap = map;
        if (currentMap == null || !mapFocus.active) return;
        if (mapFocus.focusedDeviceId != null) return;

        const coords: [number, number][] = [];
        for (const device of [...webappSocket.devices, ...webappSocket.shares]) {
            const location = device.last_location;
            if (location != null) coords.push([location.longitude, location.latitude]);
        }
        for (const entry of foreignShares.entries) {
            const location = entry.subscription.snapshot?.last_location;
            if (location != null) coords.push([location.longitude, location.latitude]);
        }
        if (coords.length === 0) return;

        const bounds = coords.reduce(
            (b, c) => b.extend(c),
            new mapboxgl.LngLatBounds(coords[0], coords[0])
        );

        currentMap.fitBounds(bounds, {
            padding: focusPadding(currentMap),
            maxZoom: 16,
            duration: 800
        });
    });

    // The camera to fall back to when a single-device focus is cleared.
    let prevFocusId: string | null = null;
    let preFocusCamera: {
        center: mapboxgl.LngLat;
        zoom: number;
        bearing: number;
        pitch: number;
    } | null = null;

    // Zoom onto a single device while its detail page is open, then restore the
    // previous camera when the focus is cleared (e.g. navigating back).
    $effect(() => {
        const currentMap = map;
        if (currentMap == null) return;

        const id = mapFocus.focusedDeviceId;

        if (id != null) {
            // Remember where we were the moment focus begins (once, not on the
            // re-runs triggered by later location/card updates).
            if (prevFocusId == null) {
                preFocusCamera = {
                    center: currentMap.getCenter(),
                    zoom: currentMap.getZoom(),
                    bearing: currentMap.getBearing(),
                    pitch: currentMap.getPitch()
                };
            }

            // The focused id may be an own device, a same-server share, or a
            // foreign share — look it up across all three.
            const location =
                webappSocket.devices.find((d) => d.id === id)?.last_location
                ?? webappSocket.shares.find((s) => s.id === id)?.last_location
                ?? foreignShares.entries.find((e) => e.activeShareId === id)?.subscription.snapshot?.last_location
                ?? null;
            if (location != null) {
                currentMap.flyTo({
                    center: [location.longitude, location.latitude],
                    zoom: 16,
                    padding: focusPadding(currentMap),
                    duration: 800
                });
            }
        } else if (prevFocusId != null) {
            // Leaving focus: when "keep all devices in view" is on the other
            // effect refits everything; otherwise fly back to where we started.
            if (!mapFocus.active && preFocusCamera != null) {
                currentMap.flyTo({ ...preFocusCamera, duration: 800 });
            }
            preFocusCamera = null;
        }

        prevFocusId = id;
    });
</script>

{#if showPlaceholder}
    <img
        src={darkMode.current ? mapDark : mapLight}
        alt=""
        class="h-full w-full object-cover object-center"
    />
{:else}
    <div bind:this={mapContainer} class="h-full w-full"></div>
{/if}
