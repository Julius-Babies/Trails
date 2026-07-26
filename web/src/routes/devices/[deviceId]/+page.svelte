<script lang="ts">
    import {page} from "$app/state";
    import {webappSocket} from "$lib/state/webapp_socket.svelte";
    import {focusDevice} from "$lib/state/map_focus.svelte";
    import {ArrowLeftIcon} from "phosphor-svelte";
    import DeviceActions from "$lib/app/devices/DeviceActions.svelte";
    import DeviceDetails from "$lib/app/devices/DeviceDetails.svelte";

    let deviceId = $derived(page.params.deviceId);
    let device = $derived(webappSocket.devices.find((d) => d.id === deviceId) ?? null);

    // The ping/ring actions only make sense for the user's own devices. The
    // device list only carries owned devices, so membership doubles as the
    // ownership check should shares ever route to this page.
    let isOwnDevice = $derived(device != null && webappSocket.devices.some((d) => d.id === deviceId));

    // Focus the map on this device while the page is open; restore on leave.
    $effect(() => {
        focusDevice(deviceId ?? null);
        return () => focusDevice(null);
    });

    let imageUrl = $derived(device ? `/api/v1/devices/image/${device.manufacturer}-${device.model}` : null);

    let hasCustomDisplayName = $derived.by(() =>
        device != null && device.display_name !== `${device.manufacturer} ${device.friendly_name}`
    );
</script>

<div class="flex flex-col h-full gap-2 overflow-y-auto scroll-thin pt-8">
    <a
            href="/"
            class="flex flex-row items-center gap-1.5 px-4 text-sm text-muted-foreground transition-colors hover:text-foreground"
    >
        <ArrowLeftIcon class="size-4" />
        Geräte
    </a>

    {#if device}
        <div class="flex flex-col gap-2 px-4">
            <DeviceDetails
                    imageUrl={imageUrl}
                    title={hasCustomDisplayName ? device.display_name : `${device.manufacturer} ${device.friendly_name}`}
                    subtitle={hasCustomDisplayName ? `${device.manufacturer} ${device.friendly_name}` : null}
                    lastLocation={device.last_location}
                    battery={device.battery}
            />

            {#if isOwnDevice}
                <DeviceActions deviceId={device.id} />
            {/if}
        </div>
    {:else}
        <p class="px-2 mt-4 text-sm text-muted-foreground">Gerät nicht gefunden.</p>
    {/if}
</div>
