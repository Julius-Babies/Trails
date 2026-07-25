<script lang="ts">
    import { onMount } from "svelte";
    import { MediaQuery } from "svelte/reactivity";
    import mapboxgl from "mapbox-gl";
    import "mapbox-gl/dist/mapbox-gl.css";
    import { getMapboxToken } from "$lib/api/mapbox/get_mapbox_token";
    import mapDark from "$lib/assets/map-dark.png";
    import mapLight from "$lib/assets/map-light.png";

    let mapContainer: HTMLDivElement | null = $state(null);
    let showPlaceholder = $state(false);
    let map: mapboxgl.Map | undefined = $state();

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

        return () => map?.remove();
    });

    $effect(() => {
        map?.setStyle(style);
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
