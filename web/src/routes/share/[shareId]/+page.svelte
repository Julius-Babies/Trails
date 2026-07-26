<script lang="ts">
    import {page} from "$app/state";
    import {focusDevice} from "$lib/state/map_focus.svelte";
    import {ArrowLeftIcon} from "phosphor-svelte";
    import DeviceDetails from "$lib/app/devices/DeviceDetails.svelte";
    import {ShareSubscription, shareOriginBase} from "$lib/state/share_socket.svelte";

    let shareId = $derived(page.params.shareId);
    // The share's origin homeserver. Absent for same-server shares → current origin.
    let homeserver = $derived(page.url.searchParams.get("homeserver") ?? "");

    // Live snapshot via the shared per-host socket; open while this page is shown.
    let subscription = $state<ShareSubscription | null>(null);
    $effect(() => {
        const sub = new ShareSubscription(homeserver, shareId);
        sub.open();
        subscription = sub;
        return () => {
            sub.close();
            subscription = null;
        };
    });

    let snapshot = $derived(subscription?.snapshot);

    // Highlight the pin (present for same-server shares) while the page is open.
    $effect(() => {
        focusDevice(shareId ?? null);
        return () => focusDevice(null);
    });

    let imageUrl = $derived(
        snapshot ? `${shareOriginBase(homeserver)}/api/v1/devices/image/${snapshot.manufacturer}-${snapshot.model}` : null
    );
</script>

<div class="flex flex-col h-full gap-2 overflow-y-auto scroll-thin pt-6">
    <a
            href="/"
            class="flex flex-row items-center gap-1.5 px-2 text-sm text-muted-foreground transition-colors hover:text-foreground"
    >
        <ArrowLeftIcon class="size-4" />
        Geräte
    </a>

    {#if snapshot}
        <DeviceDetails
                imageUrl={imageUrl}
                title={snapshot.name}
                lastLocation={snapshot.last_location}
                battery={snapshot.battery}
        />
    {:else if snapshot === null}
        <p class="px-2 mt-4 text-sm text-muted-foreground">Freigabe nicht gefunden.</p>
    {:else}
        <p class="px-2 mt-4 text-sm text-muted-foreground">Wird geladen …</p>
    {/if}
</div>
