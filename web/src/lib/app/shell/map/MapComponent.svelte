<script lang="ts">
    import { onMount, mount, unmount } from "svelte";
    import { MediaQuery } from "svelte/reactivity";
    import mapboxgl from "mapbox-gl";
    import "mapbox-gl/dist/mapbox-gl.css";
    import { getMapboxToken } from "$lib/api/mapbox/get_mapbox_token";
    import { webappSocket } from "$lib/state/webapp_socket.svelte";
    import { mapFocus, disableMapFocus } from "$lib/state/map_focus.svelte";
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
    const PIN_HEIGHT = 68;

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
        });

        return () => {
            for (const id of [...pins.keys()]) removePin(id);
            map?.remove();
        };
    });

    $effect(() => {
        map?.setStyle(style);
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
                        label: device.display_name,
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
                        label: share.name,
                        imageUrl: `/api/v1/devices/image/${share.manufacturer}-${share.model}`
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
    // re-running on location updates and card resizes.
    $effect(() => {
        const currentMap = map;
        if (currentMap == null || !mapFocus.active) return;

        const coords: [number, number][] = [];
        for (const device of [...webappSocket.devices, ...webappSocket.shares]) {
            const location = device.last_location;
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
