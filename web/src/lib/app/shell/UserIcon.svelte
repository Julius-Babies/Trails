<script lang="ts">
    import {currentUser} from "$lib/state/current_user";
    import {
        DropdownMenu,
        DropdownMenuContent,
        DropdownMenuGroup,
        DropdownMenuItem,
        DropdownMenuTrigger
    } from "$lib/components/ui/dropdown-menu";
    import {SignOutIcon} from "phosphor-svelte";

    let initials = $derived.by(() => {
        const name = $currentUser?.username || "";

        return name
            .split(/[\s.]+/)
            .filter(Boolean)
            .map((part) => part[0])
            .join("")
            .toUpperCase();
    });
</script>

{#if $currentUser}
    <DropdownMenu>
        <DropdownMenuTrigger class="cursor-pointer bg-card aspect-square w-8 h-8 rounded-full text-lg flex items-center justify-center font-black text-card-foreground p-1">
            {initials}
        </DropdownMenuTrigger>

        <DropdownMenuContent>
            <DropdownMenuGroup>
                <DropdownMenuItem class="text-destructive" href="/api/v1/webapp/auth/logout">
                    <SignOutIcon />
                    Logout
                </DropdownMenuItem>
            </DropdownMenuGroup>
        </DropdownMenuContent>
    </DropdownMenu>
{/if}