<script lang="ts">
    import {DeviceMobileIcon} from "phosphor-svelte";

    let {
        id,
        label,
        imageUrl,
        href = null,
    }: {
        id: string;
        label: string;
        imageUrl: string;
        // When set the pin links somewhere; otherwise it is a plain marker.
        href?: string | null;
    } = $props();

    let imageAvailable = $state(true);

    function handleImageError() {
        imageAvailable = false;
    }

    // Unique per pin so the clip paths of different markers don't collide.
    const clipId = `map-pin-${id}`;
</script>

{#snippet pin()}
    <div class="relative transition-transform group-hover:scale-110">
        <svg
                width="60"
                height="68"
                viewBox="0 0 48 54"
                class="drop-shadow-lg"
                xmlns="http://www.w3.org/2000/svg"
        >
            <defs>
                <clipPath id={clipId}>
                    <circle cx="24" cy="20" r="18"/>
                </clipPath>
            </defs>

            <!--
                Water-drop shape: a full circle on top whose sides leave the
                circle tangentially below its midline and converge to a sharp
                tip at the coordinate.
            -->
            <path
                    d="M24 51 C26 47.5 31.85 40.16 38.74 30.33 A18 18 0 1 0 9.26 30.33 C16.15 40.16 22 47.5 24 51 Z"
                    class="fill-background stroke-primary/40"
                    stroke-width="1"
                    stroke-linejoin="round"
            />

            {#if imageAvailable}
                <!-- Box inscribed in the head circle so the image is fully visible. -->
                <image
                        href={imageUrl}
                        x="11"
                        y="7"
                        width="26"
                        height="26"
                        clip-path={`url(#${clipId})`}
                        preserveAspectRatio="xMidYMid meet"
                        onerror={handleImageError}
                />
            {/if}
        </svg>

        {#if !imageAvailable}
            <DeviceMobileIcon
                    class="absolute left-1/2 top-[37%] size-7 -translate-x-1/2 -translate-y-1/2 text-primary"
            />
        {/if}
    </div>
{/snippet}

{#if href != null}
    <a {href} aria-label={label} class="group block cursor-pointer">
        {@render pin()}
    </a>
{:else}
    <div aria-label={label} class="group block">
        {@render pin()}
    </div>
{/if}
