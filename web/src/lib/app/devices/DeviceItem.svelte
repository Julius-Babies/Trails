<script lang="ts">
    import type {Device} from "$lib/state/webapp_socket.svelte";
    import {DeviceMobileIcon} from "phosphor-svelte";

    let {
        device,
    }: {
        device: Device
    } = $props();

    let imageAvailable = $state(true);

    function handleImageError() {
        imageAvailable = false;
    }

    let hasCustomDisplayName = $derived.by(() => {
        return device.display_name !== `${device.manufacturer} ${device.friendly_name}`;
    });
</script>

<div class="flex flex-row gap-3 items-center">
    <div class="size-10 bg-accent rounded-full flex items-center justify-center">
        {#if imageAvailable}
            <img
                    src={`/api/v1/devices/image/${device.manufacturer}-${device.model}`}
                    alt={device.display_name}
                    onerror={handleImageError}
                    class="object-contain p-2.5"
            />
        {:else}
            <DeviceMobileIcon class="size-5"/>
        {/if}
    </div>

    <div class="flex flex-col">
        <span class="font-lg">
            {#if hasCustomDisplayName}
                {device.display_name}
            {:else}
                {device.manufacturer} {device.friendly_name}
            {/if}
        </span>
        {#if hasCustomDisplayName}
            <span class="text-xs font-light text-muted-foreground">
                {device.manufacturer} {device.friendly_name}
            </span>
        {/if}
    </div>
</div>