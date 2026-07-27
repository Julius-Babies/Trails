import requireResponseIsFromTrails from "$lib/api/requireResponseIsFromTrails";
import {shareOriginBase} from "$lib/api/shares/get_share_snapshot";

/**
 * Share-related server calls. Keeping them here means UI components never touch
 * `fetch` directly — they depend on this repository instead.
 */
export const ShareRepository = {
    /**
     * Returns a saved share. This is client-driven federation: the redemption
     * ({@link https ActiveShare}) is deleted on its origin homeserver, and the
     * saved reference is removed from the current account. Deleting the
     * redemption deliberately does not lift the share's lock.
     *
     * `homeserver` is the share's origin (empty for a same-server share). The
     * origin call is best-effort (it may be a foreign host); success is decided
     * by the account-side removal, which is what drops the share from the list.
     * Resolves `true` on success, `false` on failure.
     */
    async returnShare(shareId: string, homeserver: string): Promise<boolean> {
        // Delete the redemption on its origin homeserver. Capability-based, so no
        // auth; a plain POST stays a CORS "simple request" for foreign hosts.
        const base = shareOriginBase(homeserver);
        try {
            await fetch(`${base}/api/v1/active-shares/${shareId}/return`, {method: "POST"});
        } catch {
            // Best-effort — still remove the account reference below.
        }

        // Remove the saved reference from this account (same origin, authenticated).
        let response: Response;
        try {
            response = await fetch(
                `/api/v1/me/shares/${shareId}?homeserver=${encodeURIComponent(homeserver)}`,
                {method: "DELETE"},
            );
        } catch {
            return false;
        }
        requireResponseIsFromTrails(response);
        return response.ok;
    },
};
