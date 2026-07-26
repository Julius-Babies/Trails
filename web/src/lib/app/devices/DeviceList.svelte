<script lang="ts">
    import {webappSocket} from "$lib/state/webapp_socket.svelte";
    import DeviceItem from "./DeviceItem.svelte";
    import ShareItem from "./ShareItem.svelte";
    import EmittedShareItem from "./EmittedShareItem.svelte";
</script>

<div class="flex flex-col h-full gap-2 overflow-y-auto scroll-thin pt-6">
    <h1 class="text-xl font-bold px-6">Geräte</h1>

    <div class="px-2 pb-2">
        <div class="flex flex-col rounded-4xl bg-card overflow-hidden">
            {#each webappSocket.devices as device, index (device.id)}
                <div class="border-gray-300" class:border-t={index > 0}>
                    <DeviceItem device={device}/>
                </div>
            {/each}
        </div>
    </div>

    {#if webappSocket.shares.length > 0}
        <h1 class="text-xl font-bold mt-1 px-6">Geteilt mit mir</h1>

        <div class="px-2 pb-2 ">
            <div class="flex flex-col rounded-4xl bg-card overflow-hidden">
                {#each webappSocket.shares as share, index (share.id)}
                    <div class="border-gray-300" class:border-t={index > 0}>
                        <ShareItem share={share}/>
                    </div>
                {/each}
            </div>
        </div>
    {/if}

    {#if webappSocket.emittedShares.length > 0}
        <h1 class="text-xl font-bold mt-1 px-6">Von mir geteilt</h1>

        <div class="px-2 pb-2 ">
            <div class="flex flex-col rounded-4xl bg-card overflow-hidden">
                {#each webappSocket.emittedShares as share, index (share.id)}
                    <div class="border-gray-300" class:border-t={index > 0}>
                        <EmittedShareItem share={share}/>
                    </div>
                {/each}
            </div>
        </div>
    {/if}
</div>