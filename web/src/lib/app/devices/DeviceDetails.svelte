<script lang="ts">
    import {DeviceMobileIcon} from "phosphor-svelte";
    import BatteryIcon from "$lib/components/BatteryIcon.svelte";
    import type {Battery, LastLocation} from "$lib/state/webapp_socket.svelte";
    import dayjs from "$lib/dayjs";

    let {
        imageUrl,
        title,
        subtitle = null,
        lastLocation,
        battery = null,
    }: {
        imageUrl: string | null;
        title: string;
        // Optional secondary line under the title (e.g. manufacturer + model).
        subtitle?: string | null;
        lastLocation: LastLocation | null;
        battery?: Battery | null;
    } = $props();

    let imageAvailable = $state(true);

    function handleImageError() {
        imageAvailable = false;
    }

    // Reset the image fallback when the source changes (e.g. navigating between
    // two detail views that reuse this component).
    $effect(() => {
        imageUrl;
        imageAvailable = true;
    });

    const TWO_MINUTES_MS = 2 * 60 * 1000;

    let locationText = $derived.by(() => {
        const location = lastLocation;
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

<div class="flex flex-row items-center gap-4 mt-4">
    <div class="size-20 shrink-0 flex items-center justify-center">
        {#if imageAvailable && imageUrl}
            <img
                    src={imageUrl}
                    alt={title}
                    class="object-contain w-full h-full"
                    onerror={handleImageError}
            />
        {:else}
            <DeviceMobileIcon class="size-10 text-muted-foreground" />
        {/if}
    </div>

    <div class="flex flex-col min-w-0 gap-0.5">
        <span class="text-lg font-semibold truncate">{title}</span>

        {#if subtitle}
            <span class="text-sm font-light text-muted-foreground truncate">{subtitle}</span>
        {/if}

        <span class="text-sm font-light text-muted-foreground truncate">{locationText}</span>

        {#if battery}
            <span class="flex flex-row items-center gap-1.5 text-sm font-light text-muted-foreground">
                <BatteryIcon
                        height={16}
                        width={10}
                        isCharging={battery.is_charging}
                        percentage={battery.percentage}
                        emptyColor="color-mix(in oklab, var(--color-foreground) 18%, transparent)"
                />
                {battery.percentage}%{battery.is_charging ? " · lädt" : ""}
            </span>
        {/if}
    </div>
</div>
