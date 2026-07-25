<script lang="ts">
    import './layout.css';
    import favicon from '$lib/assets/favicon.svg';
    import {page} from "$app/state";
    import MapComponent from "$lib/app/shell/map/MapComponent.svelte";
    import {currentUser, updateUser} from "$lib/state/current_user";

    let { children } = $props();

    $effect(() => {
        if (!page.url.pathname.startsWith("/auth") && $currentUser === "loading") updateUser()
    })
</script>

<svelte:head>
    <link rel="icon" href={favicon} />
    <title>Trails</title>
</svelte:head>

<div class="fixed inset-0 z-0">
    <MapComponent />
</div>

<main class="pointer-events-none relative z-10 h-full w-full p-4">
    <div
            class="pointer-events-auto h-full w-full max-w-100 rounded-lg border border-border bg-card p-4 text-card-foreground shadow-2xl
               md:w-1/2
               lg:w-1/3
               xl:w-100"
    >
        {@render children()}
    </div>
</main>