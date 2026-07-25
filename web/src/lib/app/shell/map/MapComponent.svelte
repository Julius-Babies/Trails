<script lang="ts">
    import { onMount, mount, unmount } from "svelte";
    import { MediaQuery } from "svelte/reactivity";
    import mapboxgl from "mapbox-gl";
    import "mapbox-gl/dist/mapbox-gl.css";
    import { getMapboxToken } from "$lib/api/mapbox/get_mapbox_token";
    import { webappSocket } from "$lib/state/webapp_socket.svelte";
    import DevicePin from "./DevicePin.svelte";
    import mapDark from "$lib/assets/map-dark.png";
    import mapLight from "$lib/assets/map-light.png";

    let mapContainer: HTMLDivElement | null = $state(null);
    let showPlaceholder = $state(false);
    let map: mapboxgl.Map | undefined = $state();

    // One mapbox marker + mounted DevicePin per device that has a location.
    type PinEntry = { marker: mapboxgl.Marker; component: Record<string, any> };
    const pins = new Map<string, PinEntry>();

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
        });

        return () => {
            for (const id of [...pins.keys()]) removePin(id);
            map?.remove();
        };
    });

    $effect(() => {
        map?.setStyle(style);
    });

    // Keep a pin on the map for every device that has a last known location.
    $effect(() => {
        const currentMap = map;
        if (currentMap == null) return;

        const seen = new Set<string>();
        for (const device of webappSocket.devices) {
            const location = device.last_location;
            if (location == null) continue;
            seen.add(device.id);

            const lngLat: [number, number] = [location.longitude, location.latitude];
            const existing = pins.get(device.id);
            if (existing != null) {
                existing.marker.setLngLat(lngLat);
                continue;
            }

            const element = document.createElement("div");
            const component = mount(DevicePin, { target: element, props: { device } });
            const marker = new mapboxgl.Marker({ element, anchor: "bottom" })
                .setLngLat(lngLat)
                .addTo(currentMap);
            pins.set(device.id, { marker, component });
        }

        // Drop pins for devices that vanished or lost their location.
        for (const id of [...pins.keys()]) {
            if (!seen.has(id)) removePin(id);
        }
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
