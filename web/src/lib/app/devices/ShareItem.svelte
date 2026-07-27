<script lang="ts">
    import type {Share} from "$lib/state/webapp_socket.svelte";
    import {shareOriginBase} from "$lib/state/share_socket.svelte";
    import {DeviceMobileIcon} from "phosphor-svelte";
    import BatteryIcon from "$lib/components/BatteryIcon.svelte";
    import dayjs from "$lib/dayjs";

    let {
        share,
        homeserver = "",
    }: {
        share: Share;
        // Origin of a foreign share; empty for same-server. Drives the image
        // origin and the detail link's ?homeserver= param.
        homeserver?: string;
    } = $props();

    let base = $derived(shareOriginBase(homeserver));
    let href = $derived(
        homeserver ? `/share/${share.id}?homeserver=${encodeURIComponent(homeserver)}` : `/share/${share.id}`
    );

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

<!-- Links to the share detail page, keyed by the active-share id (the only
     identifier the client has for a share). -->
<a class="flex flex-row gap-3 items-center transition-colors duration-100 hover:bg-foreground/10 cursor-pointer py-3 pl-2 pr-4 rounded-md"
   href={href}>
    <div class="size-10 bg-accent rounded-full flex items-center justify-center">
        {#if imageAvailable}
            <img
                    src={`${base}/api/v1/devices/image/${share.manufacturer}-${share.model}`}
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
</a>
