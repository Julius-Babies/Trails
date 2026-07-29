<script lang="ts">
    import {Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter} from "$lib/components/ui/dialog";
    import Button from "$lib/components/ui/button/button.svelte";
    import { CircleNotchIcon } from "phosphor-svelte";
    import {goto} from "$app/navigation";
    import {ShareRepository} from "$lib/api/shares/share_repository";

    let {
      open = $bindable(),
      shareId,
      shareName,
    }: {
      open: boolean,
      shareId: string,
      shareName: string,
    } = $props();

    let deleting = $state(false);

    async function confirmDelete() {
        if (deleting) return;
        deleting = true;
        const ok = await ShareRepository.removeEmittedShare(shareId);
        deleting = false;
        if (ok) {
            open = false;
            // The share is gone; return to the overview.
            await goto("/");
        }
    }
</script>

<Dialog bind:open={open}>
    <DialogContent>
        <DialogHeader>
            <DialogTitle>Freigabe löschen</DialogTitle>
            <DialogDescription>
                Möchtest du „{shareName}“ wirklich löschen? Alle Abonnenten der Freigabe werden keine Daten mehr über das Gerät erhalten.
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
