<script lang="ts">
    import './layout.css';
    import favicon from '$lib/assets/favicon.svg';
    import MapComponent from "$lib/app/shell/map/MapComponent.svelte";
    import {currentUser, updateUser} from "$lib/state/current_user";
    import {onMount} from "svelte";
    import UserIcon from "$lib/app/shell/UserIcon.svelte";
    import {startWebappSocket} from "$lib/state/webapp_socket.svelte";

    let { children } = $props();

    onMount(() => {
        startWebappSocket();
        updateUser();
    })
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
            class="xl-card pointer-events-auto h-full w-full max-w-100 overflow-y-auto rounded-3xl border border-border bg-card p-4 pt-6 text-card-foreground shadow-2xl
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
{/if}