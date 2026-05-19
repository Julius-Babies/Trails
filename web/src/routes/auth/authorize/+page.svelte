<script lang="ts">
    import {
        Authentikt,
        AuthentiktDebug,
        type AuthentiktConfiguration,
        DoneRenderer,
        useAuthentiktContext
    } from "@Julius-Babies/authentikt-svelte";
    import LinkFlow from "./steps/init/LinkFlow.svelte";
    import DeviceSelection from "./steps/device_selection/DeviceSelection.svelte";
    import EmailUserSelection from "./steps/email/EmailUserSelection.svelte";
    import Password from "./steps/password/Password.svelte";
    import { env } from '$env/dynamic/public';

    const config: AuthentiktConfiguration = {
        baseUrl: env.PUBLIC_BASE_URL + "/api/v1/auth/authentikt/",
        // debug: {show_overlay: false},
        debug: false
    }

</script>

<div class="relative flex flex-col w-full h-full">
    <Authentikt
            {config}
    >
        {@const authentikt = useAuthentiktContext()}

        {#if !!config.debug}
            <div class="relative h-80 overflow-y-auto">
                <AuthentiktDebug authentikt={authentikt} />
            </div>
        {/if}

        <div class="w-full h-full flex flex-col items-center justify-center md:py-8 md:bg-zinc-50">
            <div class="flex flex-col items-center p-4 h-full md:max-h-10/12 max-md:w-full md:w-xl md:rounded-md md:shadow-xl bg-white overflow-y-auto">
                <span class="text-6xl font-extralight pt-8">Trails</span>
                <span class="text-base font-light text-zinc-600 pt-1 pb-2">Mit Benutzerkonto anmelden.</span>

                <div class="flex grow w-full">
                    {#if !authentikt.currentFlow}
                        <LinkFlow />
                    {:else}
                        <DeviceSelection />
                        <EmailUserSelection />
                        <Password />
                        <DoneRenderer>
                            {#snippet children(plugin)}
                                <div class="flex flex-col items-center justify-center w-full h-full gap-4">
                                    <span class="text-xl font-semibold pb-1">Anmeldung erfolgreich.</span>
                                    {#if plugin.result?.type === "redirect"}
                                        <div>
                                            Die Weiterleitung funktioniert nicht?
                                            <a href={plugin.result.to} class="underline">Klicke hier.</a>
                                        </div>
                                    {/if}
                                </div>
                            {/snippet}
                        </DoneRenderer>
                    {/if}
                </div>
                <span class="text-xs text-zinc-400">Powered by <a href="https://github.com/Julius-Babies/authentikt" class="underline" target="blank" rel="noreferrer">Authentikt</a>.</span><br />

                <!-- TODO: Remove for production -->
                <span class="text-xs text-zinc-400">Anmeldung <a data-sveltekit-reload href="/api/v1/auth/app-authorization?device_manufacturer=Google&device_model=panther" class="underline">neustarten</a>.</span>
            </div>
        </div>
    </Authentikt>
</div>
