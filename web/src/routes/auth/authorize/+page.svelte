<script lang="ts">
    import {Authentikt, DoneRenderer, useAuthentiktContext} from "@Julius-Babies/authentikt-svelte";
    import LinkFlow from "./steps/init/LinkFlow.svelte";
    import EmailUserSelection from "./steps/email/EmailUserSelection.svelte";
    import Password from "./steps/password/Password.svelte";
</script>

<div class="relative flex flex-col w-full h-full">
    <Authentikt
            baseUrl="https://trails.werkbank.space/api/v1/auth/authentikt/"
            authentikt_debug={false}
    >
        {@const authentikt = useAuthentiktContext()}

        <div class="w-full h-full flex flex-col items-center justify-center md:py-8 md:bg-zinc-50">
            <div class="flex flex-col items-center p-4 h-full md:max-h-8/12 max-md:w-full md:w-xl md:rounded-mc md:shadow-xl bg-white">
                <span class="text-6xl font-extralight pt-8">Trails</span>
                <span class="text-base font-light text-zinc-600 pt-2">Mit Benutzerkonto anmelden.</span>
                <span class="text-base font-light text-zinc-600 pb-8">{authentikt.currentFlow?.attributes["device_name"]}</span>

                <div class="flex grow w-full">
                    {#if !authentikt.currentFlow}
                        <LinkFlow />
                    {:else}
                        <EmailUserSelection />
                        <Password />
                        <DoneRenderer />
                    {/if}
                </div>
                <span class="text-xs text-zinc-400">Powered by <a href="https://github.com/Julius-Babies/authentikt" class="underline" target="blank" rel="noreferrer">Authentikt</a>.</span><br />
                <span class="text-xs text-zinc-400">Anmeldung <a data-sveltekit-reload href="/api/v1/auth/app-authorization" class="underline">neustarten</a>.</span>
            </div>
        </div>
    </Authentikt>
</div>
