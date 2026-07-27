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
    import {ShareRepository} from "$lib/api/shares/share_repository";

    let {
        open = $bindable(),
        shareId,
        homeserver,
    }: {
        open: boolean,
        shareId: string,
        // The share's origin homeserver; empty for a same-server share.
        homeserver: string,
    } = $props();

    let returning = $state(false);

    async function confirmReturn() {
        if (returning) return;
        returning = true;
        const ok = await ShareRepository.returnShare(shareId, homeserver);
        returning = false;
        if (ok) {
            open = false;
            // The share is no longer saved; return to the device list.
            await goto("/");
        }
    }
</script>

<Dialog bind:open={open}>
    <DialogContent>
        <DialogHeader>
            <DialogTitle>Freigabe zurückgeben</DialogTitle>
            <DialogDescription>
                Möchtest du diese Freigabe wirklich zurückgeben? Sie wird aus deinem Konto entfernt und du kannst den Standort nicht mehr sehen. Um erneut Zugriff zu erhalten, brauchst du eine neue Freigabe.
            </DialogDescription>
        </DialogHeader>

        <DialogFooter>
            <Button variant="secondary" onclick={() => open = false} disabled={returning}>Abbrechen</Button>
            <Button onclick={confirmReturn} disabled={returning}>
                {#if returning}
                    <CircleNotchIcon class="size-4 animate-spin" />
                {/if}
                Zurückgeben
            </Button>
        </DialogFooter>
    </DialogContent>
</Dialog>
