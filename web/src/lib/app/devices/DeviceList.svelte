<script lang="ts">
    import {webappSocket} from "$lib/state/webapp_socket.svelte";
    import DeviceItem from "./DeviceItem.svelte";
    import ShareItem from "./ShareItem.svelte";
</script>

<div class="flex flex-col h-full gap-2 overflow-y-auto pt-6">
    <h1 class="text-xl font-bold mb-1 px-6">Geräte</h1>

    <div class="px-2 pb-2 ">
        <div class="flex flex-col rounded-4xl bg-card overflow-hidden">
            {#each webappSocket.devices as device, index (device.id)}
                <div class="border-gray-300" class:border-t={index > 0}>
                    <DeviceItem device={device}/>
                </div>
            {/each}
        </div>
    </div>

    {#if webappSocket.shares.length > 0}
        <h2 class="text-lg font-bold mb-1 px-6 mt-4">Geteilt mit mir</h2>

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
</div>