<script lang="ts">
    import {PasswordRenderer} from "@Julius-Babies/authentikt-svelte";
    import Content from "./Content.svelte";
</script>

<PasswordRenderer>
    {#snippet children(plugin)}
        <Content plugin={plugin}/>
    {/snippet}
</PasswordRenderer>