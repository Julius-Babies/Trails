import {writable} from "svelte/store";
import {getMe} from "$lib/api/auth/get_me";

export interface User {
    id: string;
    username: string;
}

export const currentUser = writable<User | null>(null);

export async function updateUser() {
    const currentUserResult = await getMe();
    if (currentUserResult == null) currentUser.set(null);
    else currentUser.set(currentUserResult);
}