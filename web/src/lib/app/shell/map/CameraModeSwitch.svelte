<script lang="ts">
    import {CrosshairIcon, FrameCornersIcon, HandIcon, PathIcon} from "phosphor-svelte";
    import {
        mapCamera,
        setDetailCameraMode,
        setGeneralCameraMode,
        type DetailCameraMode,
        type GeneralCameraMode,
    } from "$lib/state/map_camera.svelte";

    // Which options exist depends on the scope: the overview can only keep every
    // device in view or hand over to the user, while a detail view can also frame
    // the target's trail. The two scopes carry independent modes, so the switch
    // reflects whichever one is currently driving the camera.
    type Option = { mode: GeneralCameraMode | DetailCameraMode; label: string };

    const GENERAL_OPTIONS: Option[] = [
        {mode: "tracking", label: "Alle Geräte im Blick behalten"},
        {mode: "manual", label: "Karte selbst bewegen"},
    ];

    const DETAIL_OPTIONS: Option[] = [
        {mode: "tracking", label: "Gerät folgen"},
        {mode: "trail", label: "Verlauf im Blick behalten"},
        {mode: "manual", label: "Karte selbst bewegen"},
    ];

    let isDetail = $derived(mapCamera.scope === "detail");
    let options = $derived(isDetail ? DETAIL_OPTIONS : GENERAL_OPTIONS);
    let activeMode = $derived(isDetail ? mapCamera.detailMode : mapCamera.generalMode);

    function select(mode: GeneralCameraMode | DetailCameraMode) {
        if (isDetail) setDetailCameraMode(mode as DetailCameraMode);
        else setGeneralCameraMode(mode as GeneralCameraMode);
    }
</script>

<div
        role="group"
        aria-label="Kameramodus"
        class="pointer-events-auto flex flex-col items-center gap-1 rounded-full border border-border bg-accent/65 p-1 text-card-foreground shadow-2xl backdrop-blur-lg"
>
    {#each options as option (option.mode)}
        {@const active = option.mode === activeMode}
        <button
                type="button"
                onclick={() => select(option.mode)}
                aria-pressed={active}
                title={option.label}
                class="flex h-8 w-8 cursor-pointer items-center justify-center rounded-full transition-colors
                   {active ? 'bg-primary text-primary-foreground' : 'hover:bg-accent'}"
        >
            {#if option.mode === "manual"}
                <HandIcon size={18} weight={active ? "fill" : "regular"} />
            {:else if option.mode === "trail"}
                <PathIcon size={18} weight={active ? "fill" : "regular"} />
            {:else if isDetail}
                <CrosshairIcon size={18} weight={active ? "fill" : "regular"} />
            {:else}
                <FrameCornersIcon size={18} weight={active ? "fill" : "regular"} />
            {/if}
        </button>
    {/each}
</div>
