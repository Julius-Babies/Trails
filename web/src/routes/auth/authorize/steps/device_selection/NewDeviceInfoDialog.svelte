<script lang="ts">
    import {
        Dialog,
        DialogContent,
        DialogDescription,
        DialogFooter,
        DialogHeader,
        DialogTitle
    } from "$lib/components/ui/dialog";
    import {Alert, AlertDescription, AlertTitle} from "$lib/components/ui/alert";
    import {TriangleAlert, Loader} from "@lucide/svelte";
    import {Input} from "$lib/components/ui/input";
    import {Button} from "$lib/components/ui/button";
    import {Field, FieldDescription, FieldError, FieldLabel, FieldSet} from "$lib/components/ui/field";
    import {slide} from "svelte/transition";
    import type {NewDeviceNameResult} from "./DeviceSelectionPlugin.svelte.ts";

    let {
        onDismiss,
        onSubmitName,
    }: {
        onDismiss: () => void;
        onSubmitName: (name: string) => Promise<NewDeviceNameResult>;
    } = $props();
    const componentId = $props.id();

    let open = $state(true);

    let deviceName = $state("");

    let deviceNameInput: HTMLInputElement | null = $state(null);
    $effect(() => {
        if (deviceNameInput) deviceNameInput.focus();
    })

    let error = $state<"unknown_error" | "name_already_exists" | "name_too_short" | null>(null);
    let isLoading = $state(false);
    $effect(() => {
        deviceName;
        error = null;
    })

    function submit() {
        if (isLoading) return;
        if (deviceName.length < 5) {
            error = "name_too_short";
            if (deviceNameInput) deviceNameInput.focus();
            return;
        }

        isLoading = true;
        onSubmitName(deviceName).then((result) => {
            isLoading = false;
            if (result === "unknown_error") error = "unknown_error";
            if (result === "name_already_exists") error = "name_already_exists";
        })
    }
</script>

<Dialog bind:open={open} onOpenChangeComplete={(to) => { if (!to) onDismiss(); }}>
    <DialogContent>
        <DialogHeader>
            <DialogTitle>Neues Gerät verknüpfen</DialogTitle>
            <DialogDescription>
                <Alert class="mt-2" variant="warning">
                    <TriangleAlert />
                    <AlertTitle>Hinweis für neue Geräte</AlertTitle>
                    <AlertDescription>
                        Du hast bereits ein Gerät des gleichen Modells in deinem Trails-Account.
                        Falls du die App oder dein Handy zurückgesetzt hast, gehe zurück und verknüpfe
                        diese Anmeldung mit dem bestehenden Gerät, sodass Freigaben und Standortdaten nahtlos
                        mit dem Gerät verknüpft werden. Fahre fort, wenn du mehrere Geräte des gleichen Modells
                        in deinem Trails-Account anmelden möchtest. Zur besseren Unterscheidbarkeit musst du diesem
                        Gerät einen Namen geben.
                    </AlertDescription>
                </Alert>
            </DialogDescription>
        </DialogHeader>

        <form class="flex flex-col w-full" onsubmit={submit}>
            <FieldSet>
                <Field>
                    <FieldLabel
                            for="device-name-field-{componentId}"
                    >Gerätenamen</FieldLabel>
                    <Input
                            id="device-name-field-{componentId}"
                            placeholder="z.B. Andys iPhone 12 (dienstlich)"
                            bind:value={deviceName}
                            bind:ref={deviceNameInput}
                    />
                    <FieldDescription>Der Name wird anstelle des Gerätemodells bevorzugt angezeigt.</FieldDescription>
                    <div>
                        {#if error}
                            <div transition:slide>
                                <FieldError>
                                    {#if error === "name_too_short"}
                                        Der Name muss mindestens 5 Zeichen umfassen.
                                    {:else if error === "name_already_exists"}
                                        Dieser Name wird bereits verwendet.
                                    {:else if error === "unknown_error"}
                                        Es ist ein unbekannter Fehler aufgetreten.
                                    {/if}
                                </FieldError>
                            </div>
                        {/if}
                    </div>
                </Field>
            </FieldSet>
        </form>

        <DialogFooter>
            <Button
                    variant="secondary"
                    onclick={() => open = false}
            >Abbrechen</Button>
            <Button
                    variant="default"
                    onclick={submit}
                    disabled={isLoading}
            >
                {#if isLoading}
                    <div class="pr-1" transition:slide={{axis: "x"}}>
                        <Loader class="animate-spin" />
                    </div>
                {/if}
                Speichern
            </Button>
        </DialogFooter>
    </DialogContent>
</Dialog>