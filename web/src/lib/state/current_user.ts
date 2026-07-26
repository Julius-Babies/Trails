import {writable} from "svelte/store";
import {getMe} from "$lib/api/auth/get_me";

export interface User {
    id: string;
    username: string;
}

export const currentUser = writable<User | null>(null);

/**
 * Whether the initial auth check (getMe) has completed. Until it flips true,
 * `currentUser` being null is indistinguishable from "not signed in", so the UI
 * should show a loading state rather than the logged-out view.
 */
export const authInitialized = writable(false);

export async function updateUser() {
    try {
        const currentUserResult = await getMe();
        currentUser.set(currentUserResult ?? null);
    } finally {
        authInitialized.set(true);
    }
}