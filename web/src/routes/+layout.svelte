<script lang="ts">
    import './layout.css';
    import favicon from '$lib/assets/favicon.svg';
    import {getMe} from "$lib/api/auth/get_me";
    import {page} from "$app/state";
    import MapComponent from "$lib/app/shell/map/MapComponent.svelte";

    let { children } = $props();

    $effect(() => {
        if (!page.url.pathname.startsWith("/auth")) getMe()
            .then(user => {
                if (user == null) window.location.href = "/api/v1/auth/webapp-authorization"
            })
    })
</script>

<svelte:head>
    <link rel="icon" href={favicon} />
    <title>Trails</title>
</svelte:head>

{#if !page.url.pathname.startsWith("/auth")}
    <div class="fixed inset-0 z-0">
        <MapComponent />
    </div>
{/if}

<main class="relative z-10 w-fit h-fit p-4">
    <div class="w-64 h-fit bg-background p-8 rounded-lg drop-shadow-2xl">
        {@render children()}
    </div>
</main>