<script lang="ts">
    import {EmailUserSelectionRenderer} from "@Julius-Babies/authentikt-svelte";
    import Content from "./Content.svelte";
</script>

<EmailUserSelectionRenderer>
    {#snippet children(plugin)}
        <Content plugin={plugin}/>
    {/snippet}
</EmailUserSelectionRenderer>