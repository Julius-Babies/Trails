<script lang="ts">
    import {webappSocket} from "$lib/state/webapp_socket.svelte";
    import {foreignShares} from "$lib/state/share_socket.svelte";
    import DeviceItem from "./DeviceItem.svelte";
    import ShareItem from "./ShareItem.svelte";
    import EmittedShareItem from "./EmittedShareItem.svelte";

    // "Shared with me" merges same-server shares (from the webapp socket) with
    // foreign shares (live from their origin via per-host share sockets). Foreign
    // entries appear once their first snapshot has arrived.
    let sharedWithMe = $derived.by(() => {
        const local = webappSocket.shares.map((share) => ({ key: share.id, share, homeserver: "" }));
        const foreign = foreignShares.entries.flatMap((entry) => {
            const snapshot = entry.subscription.snapshot;
            if (snapshot == null) return [];
            return [{
                key: `${entry.homeserver} ${entry.activeShareId}`,
                share: {
                    id: entry.activeShareId,
                    name: snapshot.name,
                    manufacturer: snapshot.manufacturer,
                    model: snapshot.model,
                    battery: snapshot.battery,
                    last_location: snapshot.last_location,
                },
                homeserver: entry.homeserver,
            }];
        });
        return [...local, ...foreign];
    });
</script>

<div class="flex flex-col h-full gap-2 overflow-y-auto scroll-thin pt-8">
    <h1 class="text-xl font-bold px-6">Geräte</h1>

    <div class="px-3 pb-2">
        <div class="flex flex-col rounded-4xl bg-card overflow-hidden">
            {#each webappSocket.devices as device, index (device.id)}
                {#if index > 0}
                    <div class="px-6 w-full h-px">
                        <div class="w-full h-full bg-border"></div>
                    </div>
                {/if}
                <DeviceItem device={device}/>
            {/each}
        </div>
    </div>

    {#if sharedWithMe.length > 0}
        <h1 class="text-sm font-semibold mt-1 px-6 text-accent-foreground">Geteilt mit mir</h1>

        <div class="px-3 pb-2">
            <div class="flex flex-col rounded-4xl bg-card overflow-hidden">
                {#each sharedWithMe as entry, index (entry.key)}
                    {#if index > 0}
                        <div class="px-6 w-full h-px">
                            <div class="w-full h-full bg-border"></div>
                        </div>
                    {/if}
                    <ShareItem share={entry.share} homeserver={entry.homeserver}/>
                {/each}
            </div>
        </div>
    {/if}

    {#if webappSocket.emittedShares.length > 0}
        <h1 class="text-sm font-semibold mt-1 px-6 text-accent-foreground">Meine Freigaben</h1>

        <div class="px-3 pb-2 ">
            <div class="flex flex-col rounded-4xl bg-card overflow-hidden">
                {#each webappSocket.emittedShares as share, index (share.id)}
                    <div class="border-gray-300" class:border-t={index > 0}>
                        <EmittedShareItem share={share}/>
                    </div>
                {/each}
            </div>
        </div>
    {/if}
</div>