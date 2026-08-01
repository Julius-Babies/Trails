<script lang="ts">
    import type {EmittedShare} from "$lib/state/webapp_socket.svelte";
    import {locationHistoryLabel} from "$lib/app/emitted-shares/location_history";
    import {
        BatteryVerticalHighIcon,
        ClockCounterClockwiseIcon,
        DeviceMobileIcon,
        LockIcon,
        UsersIcon,
    } from "phosphor-svelte";
    import {_} from "svelte-i18n";

    let {
        share,
    }: {
        share: EmittedShare
    } = $props();

    let imageAvailable = $state(true);

    function handleImageError() {
        imageAvailable = false;
    }

    // Human-readable location history retention, derived from the raw seconds.
    let historyText = $derived(locationHistoryLabel(share.location_history_seconds));
</script>

<a class="flex flex-row gap-3 items-center transition-colors duration-100 hover:bg-foreground/10 cursor-pointer py-3 pl-2 pr-4 rounded-2xl" href={"/myshare/" + share.id}>
    <div class="size-10 bg-accent rounded-full flex items-center justify-center shrink-0">
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
        <span class="font-lg truncate">{share.name}</span>
        <span class="text-xs font-light text-muted-foreground truncate">
            {share.device_display_name}
        </span>
        <div class="flex flex-row flex-wrap gap-x-3 gap-y-0.5 mt-1 text-xs text-muted-foreground">
            <span class="inline-flex items-center gap-1">
                <ClockCounterClockwiseIcon class="size-3.5"/>
                {historyText}
            </span>
            {#if share.share_battery_state}
                <span class="inline-flex items-center gap-1">
                    <BatteryVerticalHighIcon class="size-3.5"/>
                    {$_("emitted_shares.battery_badge")}
                </span>
            {/if}
            {#if share.allow_multiuse}
                <span class="inline-flex items-center gap-1">
                    <UsersIcon class="size-3.5"/>
                    {$_("emitted_shares.multiuse_badge")}
                </span>
            {/if}
            {#if share.is_locked}
                <span class="inline-flex items-center gap-1">
                    <LockIcon class="size-3.5"/>
                    {$_("emitted_shares.locked_badge")}
                </span>
            {/if}
        </div>
    </div>

    <div class="flex flex-col items-end shrink-0">
        <span class="text-lg font-bold leading-none">{share.redemption_count}</span>
        <span class="text-xs font-light text-muted-foreground">
            {$_("emitted_shares.redemptions", {values: {count: share.redemption_count}})}
        </span>
    </div>
</a>
