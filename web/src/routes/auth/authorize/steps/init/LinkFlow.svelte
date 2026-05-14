<script lang="ts">
    import {useAuthentiktContext} from "@Julius-Babies/authentikt-svelte";
    import {onMount, tick} from "svelte";
    import {page} from "$app/state";
    import {Loader} from "@lucide/svelte";

    const authentikt = useAuthentiktContext()

    onMount(async () => {
        await tick();
        const sessionId = page.url.searchParams.get("session_id");
        if (sessionId) await authentikt.linkToFlow(sessionId);
        else console.warn("no session id found");
    })
</script>

<div class="flex flex-col items-center justify-center w-full h-full gap-4">
    <Loader class="animate-spin" />
    <span>Wird geladen...</span>
</div>