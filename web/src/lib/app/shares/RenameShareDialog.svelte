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
    import {Input} from "$lib/components/ui/input";
    import {CircleNotchIcon} from "phosphor-svelte";
    import {ShareRepository, type RenameShareResult} from "$lib/api/shares/share_repository";

    let {
        open = $bindable(),
        shareId,
        currentShareName,
    }: {
        open: boolean,
        shareId: string,
        currentShareName: string,
    } = $props();

    let name = $state("");
    let error = $state<RenameShareResult | null>(null);

    $effect(() => {
        if (open) {
            name = currentShareName;
            error = null;
        }
    })

    let inputElement = $state<HTMLInputElement | null>(null);

    $effect(() => {
        if (open && inputElement) inputElement.focus();
    })

    let saving = $state(false);

    async function save() {
        if (saving) return;
        saving = true;
        error = null;
        const result = await ShareRepository.renameEmittedShare(shareId, name.trim());
        saving = false;
        if (result === "ok") open = false;
        else error = result;
    }
</script>

<Dialog bind:open={open}>
    <DialogContent>
        <DialogHeader>
            <DialogTitle>Freigabe umbenennen</DialogTitle>
            <DialogDescription>Der Name hilft dir, deine Freigaben auseinanderzuhalten. Empfänger sehen ihn nicht.</DialogDescription>
        </DialogHeader>

        <div class="flex flex-col gap-1.5">
            <Input
                    bind:value={name}
                    bind:ref={inputElement}
                    type="text"
                    placeholder="z.B. Freigabe für Familie"
                    aria-invalid={error != null}
                    onkeydown={(e) => { if (e.key === "Enter") save(); }}
            />

            {#if error === "name-taken"}
                <span class="text-sm text-destructive">Du hast bereits eine Freigabe mit diesem Namen.</span>
            {:else if error === "name-blank"}
                <span class="text-sm text-destructive">Der Freigabename darf nicht leer sein.</span>
            {:else if error === "error"}
                <span class="text-sm text-destructive">Die Freigabe konnte nicht umbenannt werden.</span>
            {/if}
        </div>

        <DialogFooter>
            <Button variant="secondary" onclick={() => open = false} disabled={saving}>Abbrechen</Button>
            <Button onclick={save} disabled={saving}>
                {#if saving}
                    <CircleNotchIcon class="size-4 animate-spin" />
                {/if}
                Speichern
            </Button>
        </DialogFooter>
    </DialogContent>
</Dialog>
