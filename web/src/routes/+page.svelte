<script lang="ts">
    import {currentUser} from "$lib/state/current_user";
    import {Button} from "$lib/components/ui/button";
</script>

{#if $currentUser === null}
    <div class="flex flex-col items-center justify-center h-full">
        <h1 class="text-4xl font-light">Willkommen bei Trails</h1>
        <Button class="mt-4" href="/api/v1/auth/webapp-authorization">Login</Button>
    </div>

{:else}
    <div class="flex flex-col h-full">
        <h1 class="text-xl font-bold">Geräte</h1>
    </div>
{/if}