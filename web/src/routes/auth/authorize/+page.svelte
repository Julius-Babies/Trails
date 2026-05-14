<script lang="ts">
    import {Authentikt, EmailUserSelectionRenderer, PasswordRenderer, useAuthentiktContext} from "@Julius-Babies/authentikt-svelte";
    import LinkFlow from "./LinkFlow.svelte";
</script>

<div class="relative flex flex-col w-full h-full">
    <a data-sveltekit-reload href="/api/v1/auth/app-authorization">Neustart</a>
    <Authentikt
            baseUrl="https://trails.werkbank.space/api/v1/auth/authentikt/"
            authentikt_debug={true}
    >
        {@const authentikt = useAuthentiktContext()}
        {#if !authentikt.currentFlow}
            <LinkFlow />
        {:else}
            <EmailUserSelectionRenderer />

            <!-- Custom password UI with snippet override -->
            <PasswordRenderer>
                {#snippet children(plugin)}
                    <form onsubmit={plugin.submit}>
                        <input bind:value={plugin.password} type="password" placeholder="Password" />
                        {#if plugin.status === "password_incorrect"}
                            <p class="error">Incorrect password</p>
                        {/if}
                        <button type="submit" disabled={plugin.status === "loading"}>
                            {plugin.status === "loading" ? "Checking..." : "Continue"}
                        </button>
                    </form>
                {/snippet}
            </PasswordRenderer>
        {/if}
    </Authentikt>
</div>
