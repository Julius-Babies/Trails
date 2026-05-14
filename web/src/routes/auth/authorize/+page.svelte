<script lang="ts">
    import {Authentikt, EmailUserSelectionRenderer, PasswordRenderer, useAuthentiktContext} from "@Julius-Babies/authentikt-svelte";
    import LinkFlow from "./LinkFlow.svelte";
    import {Label} from "$lib/components/ui/label";
    import {Input} from "$lib/components/ui/input";
    import { slide } from "svelte/transition";
    import {Button} from "$lib/components/ui/button";
    import {ArrowRight, Loader} from "@lucide/svelte";

    let id = $props.id();
</script>

<div class="relative flex flex-col w-full h-full">
    <Authentikt
            baseUrl="https://trails.werkbank.space/api/v1/auth/authentikt/"
            authentikt_debug={false}
    >
        {@const authentikt = useAuthentiktContext()}

        <div class="w-full h-full flex flex-col items-center justify-center md:py-8 md:bg-zinc-50">
            <div class="flex flex-col items-center p-4 h-full md:max-h-8/12 md:w-xl md:rounded-mc md:shadow-xl bg-white">
                <span class="text-6xl font-extralight pt-8">Trails</span>
                <span class="text-base font-light text-zinc-600 pt-2 pb-8">Mit Benutzerkonto anmelden.</span>

                <div class="flex grow w-full">
                    {#if !authentikt.currentFlow}
                        <LinkFlow />
                    {:else}
                        <EmailUserSelectionRenderer>
                            {#snippet children(plugin)}
                                <div class="w-full h-full flex flex-col">
                                    <form class="w-full flex flex-col gap-1.5" onsubmit={plugin.submit}>
                                        <Label for="email-{id}">E-Mail oder Benutzername</Label>
                                        <Input
                                                type="text"
                                                id="email-{id}"
                                                bind:value={plugin.email}
                                                placeholder="andrea.musterfrau@gmx.de"
                                                onsubmit={plugin.submit}
                                        />
                                    </form>

                                    {#if plugin.status !== "ready" && plugin.status !== "loading"}
                                        <span
                                                class="text-sm text-red-500 mt-2"
                                                transition:slide={{ duration: 200 }}
                                        >
                                            {#if plugin.status === "user_not_existing"}
                                                Der Nutzer existiert nicht.
                                            {:else if plugin.status === "error"}
                                                Ein Fehler ist aufgetreten.
                                            {/if}
                                        </span>
                                    {/if}

                                    <Button
                                            class="mt-2"
                                            onclick={plugin.submit}
                                            disabled={plugin.status === "loading" || plugin.email === ""}
                                    >Weiter {#if plugin.status === "loading"}<Loader />{:else}<ArrowRight />{/if}</Button>
                                </div>
                            {/snippet}
                        </EmailUserSelectionRenderer>

                        <!-- Custom password UI with snippet override -->
                        <PasswordRenderer>
                            {#snippet children(plugin)}
                                <div class="w-full h-full flex flex-col">
                                    <form class="w-full flex flex-col gap-1.5" onsubmit={plugin.submit}>
                                        <Label for="password-{id}">Passwort</Label>
                                        <Input
                                                type="text"
                                                id="password-{id}"
                                                bind:value={plugin.password}
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
                                    >Weiter {#if plugin.status === "loading"}<Loader />{:else}<ArrowRight />{/if}</Button>
                                </div>
                            {/snippet}
                        </PasswordRenderer>
                    {/if}
                </div>
                <span class="text-xs text-zinc-400">Powered by <a href="https://github.com/Julius-Babies/authentikt" class="underline" target="blank" rel="noreferrer">Authentikt</a>.</span><br />
                <span class="text-xs text-zinc-400">Anmeldung <a data-sveltekit-reload href="/api/v1/auth/app-authorization" class="underline">neustarten</a>.</span>
            </div>
        </div>
    </Authentikt>
</div>
