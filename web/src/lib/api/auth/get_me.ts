import requireResponseIsFromTrails from "$lib/api/requireResponseIsFromTrails";

export async function getMe() {
    const response = await fetch("/api/v1/webapp/me");
    requireResponseIsFromTrails(response);
    if (response.ok) {
        const data = await response.json();
        return {
            id: data.id,
            username: data.username,
        }
    } else {
        return null;
    }
}