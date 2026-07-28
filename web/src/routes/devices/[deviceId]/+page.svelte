<script lang="ts">
    import {page} from "$app/state";
    import {webappSocket} from "$lib/state/webapp_socket.svelte";
    import {focusDevice} from "$lib/state/map_focus.svelte";
    import DeviceActions from "$lib/app/devices/DeviceActions.svelte";
    import DeviceDetails from "$lib/app/devices/DeviceDetails.svelte";
    import DeviceHeader from "$lib/app/devices/DeviceHeader.svelte";
    import {loadHistory} from "$lib/state/history.svelte";
    import {setMapTrail} from "$lib/state/map_trail.svelte";

    let deviceId = $derived(page.params.deviceId);
    let device = $derived(webappSocket.devices.find((d) => d.id === deviceId) ?? null);

    // The device's full location history, read once on open. Owned devices are
    // never limited, so this is everything the server has recorded.
    let history = loadHistory(() => (deviceId ? {kind: "device", deviceId} : null));

    // The ping/ring actions only make sense for the user's own devices. The
    // device list only carries owned devices, so membership doubles as the
    // ownership check should shares ever route to this page.
    let isOwnDevice = $derived(device != null && webappSocket.devices.some((d) => d.id === deviceId));

    // Focus the map on this device while the page is open; restore on leave.
    $effect(() => {
        focusDevice(deviceId ?? null);
        return () => focusDevice(null);
    });

    // Draw the history as a line on the map while the page is open.
    $effect(() => {
        setMapTrail(history.points);
        return () => setMapTrail(null);
    });

    let imageUrl = $derived(device ? `/api/v1/devices/image/${device.manufacturer}-${device.model}` : null);
</script>

<div class="flex flex-col h-full gap-2 overflow-y-auto scroll-thin pt-8">
    {#if device}
        <DeviceHeader
                device={device}
        />
    {/if}

    {#if device}
        {#snippet deviceActions()}
            <div class="mt-1">
                <DeviceActions deviceId={device.id} />
            </div>
        {/snippet}
        <div class="flex flex-col gap-2 px-4">
            <DeviceDetails
                    imageUrl={imageUrl}
                    title={device.name}
                    subtitle={device.hasCustomName ? device.modelName : null}
                    lastLocation={device.last_location}
                    battery={device.battery}
                    actions={isOwnDevice ? deviceActions : undefined}
            />
        </div>
    {:else}
        <p class="px-2 mt-4 text-sm text-muted-foreground">Gerät nicht gefunden.</p>
    {/if}
</div>
