<script lang="ts">
    import {ArrowLeftIcon, DotsThreeVerticalIcon, PencilIcon, TrashIcon} from "phosphor-svelte";
    import {
        DropdownMenu,
        DropdownMenuContent,
        DropdownMenuGroup,
        DropdownMenuItem,
        DropdownMenuTrigger
    } from "$lib/components/ui/dropdown-menu";
    import {Button} from "$lib/components/ui/button";
    import RenameDeviceDialog from "$lib/app/devices/RenameDeviceDialog.svelte";
    import DeleteDeviceDialog from "$lib/app/devices/DeleteDeviceDialog.svelte";
    import type {Device} from "$lib/state/webapp_socket.svelte";

    let {
        device,
    }: {
        // The device this header belongs to, or undefined for a shared device
        // (which can't be renamed or deleted, only returned).
        device?: Device;
    } = $props();

    // Only own devices carry a Device; shared ones don't.
    let isOwnDevice = $derived(device != null);

    let showRenameDialog = $state(false);
    let showDeleteDialog = $state(false);
</script>

<div class="flex flex-row items-center gap-2 justify-between px-4">
    <a
            href="/"
            class="flex flex-row grow items-center gap-1.5 text-sm text-muted-foreground transition-colors hover:text-foreground"
    >
        <ArrowLeftIcon class="size-4"/>
        Geräte
    </a>

    <DropdownMenu>
        <DropdownMenuTrigger>
            <Button variant="ghost" size="icon" class="size-8">
                <DotsThreeVerticalIcon/>
            </Button>
        </DropdownMenuTrigger>

        <DropdownMenuContent>
            <DropdownMenuGroup>
                {#if !isOwnDevice}
                    <DropdownMenuItem>Freigabe zurückgeben</DropdownMenuItem>
                {:else}
                    <DropdownMenuItem class="text-destructive" onclick={() => showDeleteDialog = true}>
                        <TrashIcon/>
                        Löschen
                    </DropdownMenuItem>

                    <DropdownMenuItem onclick={() => showRenameDialog = true}>
                        <PencilIcon/>
                        Umbenennen
                    </DropdownMenuItem>
                {/if}
            </DropdownMenuGroup>
        </DropdownMenuContent>
    </DropdownMenu>
</div>

{#if device}
    <RenameDeviceDialog
            deviceId={device.id}
            currentDeviceName={device.hasCustomName ? device.name : ""}
            bind:open={showRenameDialog}
    />

    <DeleteDeviceDialog
            deviceId={device.id}
            deviceName={device.name}
            bind:open={showDeleteDialog}
    />
{/if}