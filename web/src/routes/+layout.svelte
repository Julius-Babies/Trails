<script lang="ts">
    import './layout.css';
    import favicon from '$lib/assets/favicon.svg';
    import MapComponent from "$lib/app/shell/map/MapComponent.svelte";
    import {currentUser, updateUser} from "$lib/state/current_user";
    import {onMount} from "svelte";
    import UserIcon from "$lib/app/shell/UserIcon.svelte";
    import {startWebappSocket} from "$lib/state/webapp_socket.svelte";
    import {mapFocus, toggleMapFocus, setContentRect} from "$lib/state/map_focus.svelte";
    import {FrameCornersIcon} from "phosphor-svelte";

    let { children } = $props();

    let cardEl: HTMLDivElement | null = $state(null);

    onMount(() => {
        startWebappSocket();
        updateUser();
    })

    // Keep the store in sync with the card's position/size so the map can inset
    // its viewport padding to avoid placing pins behind the card.
    $effect(() => {
        const el = cardEl;
        if (el == null) return;

        const update = () => {
            const rect = el.getBoundingClientRect();
            setContentRect({ top: rect.top, left: rect.left, width: rect.width, height: rect.height });
        };
        update();

        const observer = new ResizeObserver(update);
        observer.observe(el);
        window.addEventListener("resize", update);

        return () => {
            observer.disconnect();
            window.removeEventListener("resize", update);
            setContentRect(null);
        };
    });
</script>

<svelte:head>
    <link rel="icon" href={favicon} />
    <title>Trails</title>
</svelte:head>

<div class="fixed inset-0 z-0">
    <MapComponent />
</div>

<main class="pointer-events-none relative z-10 flex h-full w-full flex-col p-4">
    <div
            bind:this={cardEl}
            class="xl-card pointer-events-auto h-full w-full max-w-100 overflow-y-auto rounded-3xl border border-border bg-accent/65 backdrop-blur-lg text-card-foreground shadow-2xl
               md:w-1/2
               lg:w-1/3
               xl:mt-auto
               xl:h-[66.666dvh]
               xl:w-100"
    >
        {@render children()}
    </div>
</main>

<style>
    @media (min-width: 1280px) and (max-height: 600px) {
        .xl-card {
            height: calc(100dvh - 2rem);
        }
    }
</style>

{#if $currentUser}
    <div class="fixed right-0 top-0 z-20 max-md:p-8 md:p-4">
        <UserIcon />
    </div>

    <div class="fixed bottom-0 right-0 z-20 p-4">
        <button
                type="button"
                onclick={toggleMapFocus}
                aria-pressed={mapFocus.active}
                title="Alle Geräte im Blick behalten"
                class="pointer-events-auto flex h-10 w-10 cursor-pointer items-center justify-center rounded-full border border-border shadow-2xl transition-colors
                   {mapFocus.active ? 'bg-primary text-primary-foreground' : 'bg-card text-card-foreground'}"
        >
            <FrameCornersIcon size={20} weight={mapFocus.active ? 'fill' : 'regular'} />
        </button>
    </div>
{/if}