<script lang="ts">
    import {
        Dialog,
        DialogContent,
        DialogDescription,
        DialogFooter,
        DialogHeader,
        DialogTitle
    } from "$lib/components/ui/dialog";
    import {Button} from "$lib/components/ui/button";
    import {CircleNotchIcon} from "phosphor-svelte";
    import {goto} from "$app/navigation";
    import {DeviceRepository} from "$lib/api/devices/device_repository";

    let {
        open = $bindable(),
        deviceId,
        deviceName,
    }: {
        open: boolean,
        deviceId: string,
        deviceName: string,
    } = $props();

    let deleting = $state(false);

    async function confirmDelete() {
        if (deleting) return;
        deleting = true;
        const ok = await DeviceRepository.remove(deviceId);
        deleting = false;
        if (ok) {
            open = false;
            // The device is gone; return to the device list.
            await goto("/");
        }
    }
</script>

<Dialog bind:open={open}>
    <DialogContent>
        <DialogHeader>
            <DialogTitle>Gerät löschen</DialogTitle>
            <DialogDescription>
                Möchtest du „{deviceName}“ wirklich löschen? Bestehende Freigaben dieses Geräts werden ungültig. Diese Aktion kann nicht rückgängig gemacht werden.
            </DialogDescription>
        </DialogHeader>

        <DialogFooter>
            <Button variant="secondary" onclick={() => open = false} disabled={deleting}>Abbrechen</Button>
            <Button variant="destructive" onclick={confirmDelete} disabled={deleting}>
                {#if deleting}
                    <CircleNotchIcon class="size-4 animate-spin" />
                {/if}
                Löschen
            </Button>
        </DialogFooter>
    </DialogContent>
</Dialog>
