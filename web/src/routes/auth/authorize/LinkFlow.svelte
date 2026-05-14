<script lang="ts">
    import {useAuthentiktContext} from "@Julius-Babies/authentikt-svelte";
    import {onMount, tick} from "svelte";
    import {page} from "$app/state";

    const authentikt = useAuthentiktContext()

    onMount(async () => {
        await tick();
        const sessionId = page.url.searchParams.get("session_id");
        if (sessionId) await authentikt.linkToFlow(sessionId);
        else console.warn("no session id found");
    })
</script>

Verbinde mit Auth-Sitzung...