<script lang="ts">
    import type {Device} from "$lib/state/webapp_socket.svelte";
    import {DeviceMobileIcon} from "phosphor-svelte";
    import BatteryIcon from "$lib/components/BatteryIcon.svelte";
    import dayjs from "$lib/dayjs";

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

    const TWO_MINUTES_MS = 2 * 60 * 1000;

    let locationText = $derived.by(() => {
        const location = device.last_location;
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

<a class="flex flex-row gap-3 items-center transition-colors duration-100 hover:bg-foreground/10 cursor-pointer py-3 pl-2 pr-4 rounded-2xl"
   href={`/devices/${device.id}`}>
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

    <div class="flex flex-col flex-1 min-w-0">
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
        <span class="text-xs font-light text-muted-foreground truncate">
            {locationText}
        </span>
    </div>

    <div>
        {#if device.battery}
            <BatteryIcon
                    height={16}
                    width={10}
                    isCharging={device.battery.is_charging}
                    percentage={device.battery.percentage}
                    emptyColor="color-mix(in oklab, var(--color-foreground) 18%, transparent)"
            />
        {/if}
    </div>
</a>