<script lang="ts">
    import {page} from "$app/state";
    import {webappSocket} from "$lib/state/webapp_socket.svelte";
    import {focusDevice} from "$lib/state/map_focus.svelte";
    import {ArrowLeftIcon} from "phosphor-svelte";
    import DeviceDetails from "$lib/app/devices/DeviceDetails.svelte";
    import {ShareSubscription, shareOriginBase} from "$lib/state/share_socket.svelte";

    let shareId = $derived(page.params.shareId);
    // The share's origin homeserver. Absent for same-server shares → current origin.
    let homeserver = $derived(page.url.searchParams.get("homeserver") ?? "");
    let isForeign = $derived(homeserver !== "");

    // Foreign shares have no webapp socket on this origin, so subscribe to their
    // host's (persistent, per-host) share socket. Same-server shares are NOT
    // handled here — they already ride the always-on webapp socket below.
    let subscription = $state<ShareSubscription | null>(null);
    $effect(() => {
        if (!isForeign) return;
        const sub = new ShareSubscription(homeserver, shareId);
        sub.open();
        subscription = sub;
        return () => {
            sub.close();
            subscription = null;
        };
    });

    // Unified view of the share regardless of origin. `undefined` = still loading,
    // `null` = not found. Same-server data comes from the persistent webapp socket
    // (already warm from the start page → no per-view socket, no loading flash).
    let share = $derived.by(() => {
        if (isForeign) {
            const snapshot = subscription?.snapshot;
            if (snapshot == null) return snapshot; // undefined (loading) or null (gone)
            return { ...snapshot, base: shareOriginBase(homeserver) };
        }

        const local = webappSocket.shares.find((s) => s.id === shareId);
        if (local != null) {
            return {
                name: local.name,
                manufacturer: local.manufacturer,
                model: local.model,
                device_friendly_name: local.device_friendly_name,
                owner_username: local.owner_username,
                last_location: local.last_location,
                battery: local.battery,
                base: "",
            };
        }
        // Absent from a connected socket → genuinely gone; otherwise still loading.
        return webappSocket.connected ? null : undefined;
    });

    // Highlight the pin (present for same-server shares) while the page is open.
    $effect(() => {
        focusDevice(shareId ?? null);
        return () => focusDevice(null);
    });

</script>

<div class="flex flex-col h-full gap-2 overflow-y-auto scroll-thin pt-8">
    <a
            href="/"
            class="flex flex-row items-center gap-1.5 px-4 text-sm text-muted-foreground transition-colors hover:text-foreground"
    >
        <ArrowLeftIcon class="size-4" />
        Geräte
    </a>

    {#if share}
        <div class="flex flex-col gap-2 px-4">
            <DeviceDetails share={share} />
        </div>
    {:else if share === null}
        <p class="px-2 mt-4 text-sm text-muted-foreground">Freigabe nicht gefunden.</p>
    {:else}
        <p class="px-2 mt-4 text-sm text-muted-foreground">Wird geladen …</p>
    {/if}
</div>
