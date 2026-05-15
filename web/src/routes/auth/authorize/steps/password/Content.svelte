<script lang="ts">
    import {Label} from "$lib/components/ui/label";
    import {Input} from "$lib/components/ui/input";
    import {slide} from "svelte/transition";
    import {Button} from "$lib/components/ui/button";
    import {ArrowRight, Loader} from "@lucide/svelte";
    import type {PasswordPluginInstance} from "@Julius-Babies/authentikt-svelte";

    let {
        plugin,
    }: {
        plugin: PasswordPluginInstance,
    } = $props();

    let id = $props.id();
    let input: HTMLInputElement | null = $state(null);

    $effect(() => {
        if (input) input.focus();
    })

    $effect(() => {
        if ((plugin.status === "error" || plugin.status === "password_incorrect") && input) input.focus();
    })
</script>

<div class="w-full h-full flex flex-col">
    <form class="w-full flex flex-col gap-1.5" onsubmit={plugin.submit}>
        <Label for="password-{id}">Passwort</Label>
        <Input
                type="password"
                id="password-{id}"
                bind:value={plugin.password}
                bind:ref={input}
                placeholder="&bullet;&bullet;&bullet;&bullet;&bullet;&bullet;"
                onsubmit={plugin.submit}
        />
    </form>

    {#if plugin.status !== "ready" && plugin.status !== "loading"}
                                        <span
                                                class="text-sm text-red-500 mt-2"
                                                transition:slide={{ duration: 200 }}
                                        >
                                            {#if plugin.status === "password_incorrect"}
                                                Das Passwort ist falsch.
                                            {:else if plugin.status === "error"}
                                                Ein Fehler ist aufgetreten.
                                            {/if}
                                        </span>
    {/if}

    <Button
            class="mt-2"
            onclick={plugin.submit}
            disabled={plugin.status === "loading" || plugin.password === ""}
    >Weiter {#if plugin.status === "loading"}<Loader class="animate-spin" />{:else}<ArrowRight />{/if}</Button>
</div>