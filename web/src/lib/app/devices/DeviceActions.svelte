<script lang="ts">
    import {BellRingingIcon, CheckIcon, CircleNotchIcon, PhoneCallIcon, PhoneSlashIcon, XIcon} from "phosphor-svelte";
    import {pingDevice} from "$lib/api/devices/ping_device";
    import {ringDevice, stopRingDevice} from "$lib/api/devices/ring_device";

    let {
        deviceId,
    }: {
        deviceId: string
    } = $props();

    type PingState = "idle" | "pending" | "delivered" | "found" | "failed";

    let pingState = $state<PingState>("idle");
    let pingResetTimer: ReturnType<typeof setTimeout> | null = null;

    // Ring is a local toggle: HTTP has no live push, so we optimistically track
    // whether we started a ring and offer to stop it again.
    let ringing = $state(false);
    let ringPending = $state(false);

    const pingLabel: Record<PingState, string> = {
        idle: "Pingen",
        pending: "Pingen …",
        delivered: "Benachrichtigt",
        found: "Gefunden",
        failed: "Fehlgeschlagen",
    };

    function flashPing(state: Exclude<PingState, "idle" | "pending">) {
        pingState = state;
        if (pingResetTimer != null) clearTimeout(pingResetTimer);
        pingResetTimer = setTimeout(() => (pingState = "idle"), 2500);
    }

    async function handlePing() {
        if (pingState === "pending") return;
        if (pingResetTimer != null) clearTimeout(pingResetTimer);
        pingState = "pending";

        const result = await pingDevice(deviceId);
        if (result.type === "success") {
            flashPing(result.hasDeliveredNotification ? "delivered" : "found");
        } else {
            flashPing("failed");
        }
    }

    async function handleRing() {
        if (ringPending) return;
        const shouldRing = !ringing;
        ringPending = true;

        const result = shouldRing ? await ringDevice(deviceId) : await stopRingDevice(deviceId);
        ringPending = false;
        if (result.type === "success") {
            ringing = shouldRing;
        }
    }
</script>

<div class="mt-6 flex w-full overflow-hidden rounded-xl bg-card">
    <button
            type="button"
            onclick={handlePing}
            disabled={pingState === "pending"}
            class="group relative flex min-w-0 flex-1 cursor-pointer items-center justify-center p-4 disabled:cursor-default"
    >
        <div class="absolute inset-0 transition-colors group-hover:bg-muted group-disabled:bg-transparent"></div>

        <div class="relative flex flex-col items-center justify-center gap-1.5 transition-transform group-active:scale-95">
            {#if pingState === "pending"}
                <CircleNotchIcon class="size-6 animate-spin" />
            {:else if pingState === "delivered" || pingState === "found"}
                <CheckIcon class="size-6 text-primary" />
            {:else if pingState === "failed"}
                <XIcon class="size-6 text-destructive" />
            {:else}
                <BellRingingIcon class="size-6" />
            {/if}
            <span class="text-xs font-medium">{pingLabel[pingState]}</span>
        </div>
    </button>

    <div class="my-2 w-px bg-border"></div>

    <button
            type="button"
            onclick={handleRing}
            disabled={ringPending}
            aria-pressed={ringing}
            class="group relative flex min-w-0 flex-1 cursor-pointer items-center justify-center p-4 disabled:cursor-default"
    >
        <div
                class="absolute inset-0 transition-colors group-hover:bg-muted group-disabled:bg-transparent"
                class:bg-muted={ringing}
        ></div>

        <div class="relative flex flex-col items-center justify-center gap-1.5 transition-transform group-active:scale-95">
            {#if ringPending}
                <CircleNotchIcon class="size-6 animate-spin" />
            {:else if ringing}
                <PhoneSlashIcon class="size-6 text-primary" />
            {:else}
                <PhoneCallIcon class="size-6" />
            {/if}
            <span class="text-xs font-medium">{ringing ? "Stoppen" : "Anklingeln"}</span>
        </div>
    </button>
</div>
