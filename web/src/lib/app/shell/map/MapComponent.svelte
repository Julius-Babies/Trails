<script lang="ts">
    import { onMount } from "svelte";
    import mapboxgl from "mapbox-gl";
    import "mapbox-gl/dist/mapbox-gl.css";
    import {env} from "$env/dynamic/public";

    let mapContainer: HTMLDivElement;

    onMount(() => {
        const mediaQuery = window.matchMedia(
            "(prefers-color-scheme: dark)"
        );

        const map = new mapboxgl.Map({
            accessToken: env.PUBLIC_MAPBOX_TOKEN,
            container: mapContainer,
            style: getStyle(mediaQuery.matches),
            projection: "globe",
            center: [13.7373, 51.0504],
            zoom: 11
        });

        function handleThemeChange(event: MediaQueryListEvent) {
            map.setStyle(getStyle(event.matches));
        }

        mediaQuery.addEventListener("change", handleThemeChange);

        return () => {
            mediaQuery.removeEventListener("change", handleThemeChange);
            map.remove();
        };
    });

    function getStyle(isDark: boolean) {
        return isDark
            ? "mapbox://styles/mapbox/traffic-night-v2"
            : "mapbox://styles/mapbox/standard";
    }
</script>

<div bind:this={mapContainer} class="h-full w-full"></div>