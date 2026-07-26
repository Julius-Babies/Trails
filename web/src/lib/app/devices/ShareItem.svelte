<script lang="ts">
    import type {Share} from "$lib/state/webapp_socket.svelte";
    import {DeviceMobileIcon} from "phosphor-svelte";
    import BatteryIcon from "$lib/components/BatteryIcon.svelte";
    import dayjs from "$lib/dayjs";

    let {
        share,
    }: {
        share: Share
    } = $props();

    let imageAvailable = $state(true);

    function handleImageError() {
        imageAvailable = false;
    }

    const TWO_MINUTES_MS = 2 * 60 * 1000;

    let locationText = $derived.by(() => {
        const location = share.last_location;
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

<div class="flex flex-row gap-3 items-center py-3 pl-2 pr-4 rounded-2xl">
    <div class="size-10 bg-accent rounded-full flex items-center justify-center">
        {#if imageAvailable}
            <img
                    src={`/api/v1/devices/image/${share.manufacturer}-${share.model}`}
                    alt={share.name}
                    onerror={handleImageError}
                    class="object-contain p-2.5"
            />
        {:else}
            <DeviceMobileIcon class="size-5"/>
        {/if}
    </div>

    <div class="flex flex-col flex-1 min-w-0">
        <span class="font-lg">{share.name}</span>
        <span class="text-xs font-light text-muted-foreground truncate">
            {locationText}
        </span>
    </div>

    <div>
        {#if share.battery}
            <BatteryIcon
                    height={16}
                    width={10}
                    isCharging={share.battery.is_charging}
                    percentage={share.battery.percentage}
                    emptyColor="color-mix(in oklab, var(--color-foreground) 18%, transparent)"
            />
        {/if}
    </div>
</div>
