<script module lang="ts">
    export type BatteryOrientation = "up" | "right" | "down" | "left";

    const ROTATION: Record<BatteryOrientation, number> = {
        up: 0,
        right: 90,
        down: 180,
        left: -90,
    };

    // Charge-level colors (mirrors the Compose original).
    const COLOR_FULL = "#34C759"; // green  – 30-100 %
    const COLOR_MEDIUM = "#FFCC00"; // yellow – 15-29 %
    const COLOR_LOW = "#FF3B30"; // red    – 0-14 %

    // Stable, deterministic ids so multiple instances don't collide (SSR-safe).
    let instanceCounter = 0;
</script>

<script lang="ts">
    let {
        percentage = 80,
        isCharging = false,
        emptyColor = "rgba(0, 0, 0, 0.27)",
        orientation = "up",
        width = 36,
        height = 72,
        class: className = "",
    }: {
        percentage?: number;
        isCharging?: boolean;
        emptyColor?: string;
        orientation?: BatteryOrientation;
        width?: number;
        height?: number;
        class?: string;
    } = $props();

    const uid = `battery-${instanceCounter++}`;

    const clamped = $derived(Math.min(100, Math.max(0, Math.round(percentage))));
    const pct = $derived(clamped / 100);

    const fillColor = $derived(
        clamped >= 30 ? COLOR_FULL : clamped >= 15 ? COLOR_MEDIUM : COLOR_LOW,
    );

    // ── Geometry (relative to the width/height coordinate space) ─────────────
    const w = $derived(width);
    const h = $derived(height);

    const capH = $derived(h * 0.07);
    const gap = $derived(h * 0.025);
    const capW = $derived(w * 0.42);
    const capX = $derived((w - capW) / 2);
    const capRadius = $derived(capH / 2);

    const bodyTop = $derived(capH + gap);
    const bodyHeight = $derived(h - bodyTop);
    const bodyRadius = $derived(w * 0.38);

    // Fill rises from the bottom across the full height (cap included at 100 %).
    const fillTop = $derived(h - h * pct);

    // ── Lightning bolt ───────────────────────────────────────────────────────
    const boltHeight = $derived(bodyHeight * 0.4);
    const boltWidth = $derived(boltHeight);
    const boltCenterX = $derived(w / 2);
    const boltCenterY = $derived(bodyTop + bodyHeight / 2);

    const boltPath = $derived.by(() => {
        const cx = boltCenterX;
        const cy = boltCenterY;
        const bw = boltWidth;
        const bh = boltHeight;
        const p = (x: number, y: number) => `${x.toFixed(3)},${y.toFixed(3)}`;
        return (
            `M${p(cx + bw * 0.15, cy - bh * 0.45)}` + // top tip
            `L${p(cx - bw * 0.3, cy + bh * 0.05)}` + // outer left jog
            `L${p(cx + bw * 0.05, cy + bh * 0.05)}` + // inner left corner
            `L${p(cx - bw * 0.15, cy + bh * 0.45)}` + // bottom tip
            `L${p(cx + bw * 0.3, cy - bh * 0.05)}` + // outer right jog
            `L${p(cx - bw * 0.05, cy - bh * 0.05)}` + // inner right corner
            "Z"
        );
    });
</script>

<svg
    class={className}
    {width}
    {height}
    viewBox={`0 0 ${w} ${h}`}
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    role="img"
    aria-label={`Battery ${clamped}%${isCharging ? ", charging" : ""}`}
    style={`transform: rotate(${ROTATION[orientation]}deg); transform-origin: center;`}
>
    <defs>
        <!-- Cap + body as one shape so the fill flows through both seamlessly. -->
        <clipPath id={`${uid}-clip`}>
            <rect x={capX} y="0" width={capW} height={capH} rx={capRadius} ry={capRadius} />
            <rect x="0" y={bodyTop} width={w} height={bodyHeight} rx={bodyRadius} ry={bodyRadius} />
        </clipPath>

        <linearGradient
            id={`${uid}-fill`}
            gradientUnits="userSpaceOnUse"
            x1="0"
            y1={fillTop}
            x2="0"
            y2={h}
        >
            <stop offset="0" stop-color={fillColor} stop-opacity="0.8" />
            <stop offset="1" stop-color={fillColor} stop-opacity="1" />
        </linearGradient>

        {#if isCharging}
            <!-- Punches a transparent halo around the bolt, matching BlendMode.Clear. -->
            <mask id={`${uid}-halo`}>
                <rect x="0" y="0" width={w} height={h} fill="white" />
                <path
                    d={boltPath}
                    fill="none"
                    stroke="black"
                    stroke-width={boltHeight * 0.2}
                    stroke-linejoin="round"
                    stroke-linecap="round"
                />
            </mask>
        {/if}
    </defs>

    <g clip-path={`url(#${uid}-clip)`} mask={isCharging ? `url(#${uid}-halo)` : undefined}>
        <!-- Empty shell background -->
        <rect x="0" y="0" width={w} height={h} fill={emptyColor} />

        <!-- Fill (pulses only while charging) -->
        {#if pct > 0}
            <g class={isCharging ? "battery-fill-pulse" : ""}>
                <rect x="0" y={fillTop} width={w} height={h - fillTop} fill={`url(#${uid}-fill)`} />
            </g>
        {/if}
    </g>

    {#if isCharging}
        <path d={boltPath} fill="white" />
        <path
            d={boltPath}
            fill="none"
            stroke="white"
            stroke-width={boltHeight * 0.06}
            stroke-linejoin="round"
        />
    {/if}
</svg>

<style>
    .battery-fill-pulse {
        animation: battery-pulse 850ms ease-in-out infinite alternate;
    }

    @keyframes battery-pulse {
        from {
            opacity: 0.7;
        }
        to {
            opacity: 1;
        }
    }

    @media (prefers-reduced-motion: reduce) {
        .battery-fill-pulse {
            animation: none;
        }
    }
</style>
