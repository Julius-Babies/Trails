<script lang="ts">
    import './layout.css';
    import favicon from '$lib/assets/favicon.svg';
    import {getMe} from "$lib/api/auth/get_me";
    import {page} from "$app/state";

    let { children } = $props();

    $effect(() => {
        if (!page.url.pathname.startsWith("/auth")) getMe()
            .then(user => {
                if (user == null) window.location.href = "/api/v1/auth/webapp-authorization"
            })
    })
</script>

<svelte:head><link rel="icon" href={favicon} /></svelte:head>

<div class="w-full h-full">
    {@render children()}
</div>
