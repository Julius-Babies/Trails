<script lang="ts">
    import {page} from "$app/state";
    import {webappSocket} from "$lib/state/webapp_socket.svelte";
    import {focusDevice} from "$lib/state/map_focus.svelte";
    import {ArrowLeftIcon, DeviceMobileIcon} from "phosphor-svelte";
    import BatteryIcon from "$lib/components/BatteryIcon.svelte";
    import DeviceActions from "$lib/app/devices/DeviceActions.svelte";
    import dayjs from "$lib/dayjs";

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

    let imageAvailable = $state(true);

    function handleImageError() {
        imageAvailable = false;
    }

    let imageUrl = $derived(device ? `/api/v1/devices/image/${device.manufacturer}-${device.model}` : null);

    let hasCustomDisplayName = $derived.by(() =>
        device != null && device.display_name !== `${device.manufacturer} ${device.friendly_name}`
    );

    const TWO_MINUTES_MS = 2 * 60 * 1000;

    let locationText = $derived.by(() => {
        const location = device?.last_location;
        if (location == null) return "Noch nie gesehen";

        const address = location.address;
        const place = address != null
            ? [
                [address.road, address.house_number].filter(Boolean).join(" "),
                address.city,
                address.country,
            ].filter(Boolean).join(", ") || address.label
            : `${location.latitude.toFixed(5)}, ${location.longitude.toFixed(5)}`;

        if (Date.now() - location.found_at < TWO_MINUTES_MS) return `${place} · gerade eben`;

        return `${place} · ${dayjs(location.found_at).fromNow()}`;
    });
</script>

<div class="flex flex-col h-full gap-2 overflow-y-auto scrollbar-gutter-both pt-6">
    <a
            href="/"
            class="flex flex-row items-center gap-1.5 px-2 text-sm text-muted-foreground transition-colors hover:text-foreground"
    >
        <ArrowLeftIcon class="size-4" />
        Geräte
    </a>

    {#if device}
        <div class="flex flex-row items-center gap-4 px-2 mt-4">
            <div class="size-20 shrink-0 flex items-center justify-center">
                {#if imageAvailable && imageUrl}
                    <img
                            src={imageUrl}
                            alt={device.display_name}
                            class="object-contain w-full h-full"
                            onerror={handleImageError}
                    />
                {:else}
                    <DeviceMobileIcon class="size-10 text-muted-foreground" />
                {/if}
            </div>

            <div class="flex flex-col min-w-0 gap-0.5">
                <span class="text-lg font-semibold truncate">
                    {#if hasCustomDisplayName}
                        {device.display_name}
                    {:else}
                        {device.manufacturer} {device.friendly_name}
                    {/if}
                </span>

                {#if hasCustomDisplayName}
                    <span class="text-sm font-light text-muted-foreground truncate">
                        {device.manufacturer} {device.friendly_name}
                    </span>
                {/if}

                <span class="text-sm font-light text-muted-foreground truncate">
                    {locationText}
                </span>

                {#if device.battery}
                    <span class="flex flex-row items-center gap-1.5 text-sm font-light text-muted-foreground">
                        <BatteryIcon
                                height={16}
                                width={10}
                                isCharging={device.battery.is_charging}
                                percentage={device.battery.percentage}
                                emptyColor="color-mix(in oklab, var(--color-foreground) 18%, transparent)"
                        />
                        {device.battery.percentage}%{device.battery.is_charging ? " · lädt" : ""}
                    </span>
                {/if}
            </div>
        </div>

        {#if isOwnDevice}
            <DeviceActions deviceId={device.id} />
        {/if}
    {:else}
        <p class="px-2 mt-4 text-sm text-muted-foreground">Gerät nicht gefunden.</p>
    {/if}
</div>
