<script lang="ts">
    import {webappSocket} from "$lib/state/webapp_socket.svelte";
    import DeviceItem from "./DeviceItem.svelte";
</script>

<div class="flex flex-col h-full gap-2 overflow-y-auto">
    <h1 class="text-xl font-bold mb-1">Geräte</h1>

    <div class="flex flex-col gap-3">
        {#each webappSocket.devices as device (device.id)}
            <DeviceItem device={device}/>
        {/each}
    </div>
</div>