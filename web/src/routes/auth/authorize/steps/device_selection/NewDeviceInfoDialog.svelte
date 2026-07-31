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
    import {_} from "svelte-i18n";

    /** Shortest name the server accepts for a new device. */
    const MIN_NAME_LENGTH = 5;

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
        if (deviceName.length < MIN_NAME_LENGTH) {
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
            <DialogTitle>{$_("deviceSelection.newTitle")}</DialogTitle>
            <DialogDescription>
                <Alert class="mt-2" variant="warning">
                    <TriangleAlert />
                    <AlertTitle>{$_("deviceSelection.newHintTitle")}</AlertTitle>
                    <AlertDescription>
                        {$_("deviceSelection.newHint")}
                    </AlertDescription>
                </Alert>
            </DialogDescription>
        </DialogHeader>

        <form class="flex flex-col w-full" onsubmit={submit}>
            <FieldSet>
                <Field>
                    <FieldLabel
                            for="device-name-field-{componentId}"
                    >{$_("deviceSelection.nameLabel")}</FieldLabel>
                    <Input
                            id="device-name-field-{componentId}"
                            placeholder={$_("deviceSelection.namePlaceholder")}
                            bind:value={deviceName}
                            bind:ref={deviceNameInput}
                    />
                    <FieldDescription>{$_("deviceSelection.nameDescription")}</FieldDescription>
                    <div>
                        {#if error}
                            <div transition:slide>
                                <FieldError>
                                    {#if error === "name_too_short"}
                                        {$_("deviceSelection.nameTooShort", {values: {min: MIN_NAME_LENGTH}})}
                                    {:else if error === "name_already_exists"}
                                        {$_("deviceSelection.nameTaken")}
                                    {:else if error === "unknown_error"}
                                        {$_("common.unknownError")}
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
            >{$_("common.cancel")}</Button>
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
                {$_("common.save")}
            </Button>
        </DialogFooter>
    </DialogContent>
</Dialog>