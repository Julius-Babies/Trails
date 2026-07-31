import {addMessages, format, init, unwrapFunctionStore} from "svelte-i18n";
import de from "./locales/de.json";
import en from "./locales/en.json";

/**
 * The base language: keys are authored against it and every message missing from
 * another catalogue falls back to it.
 */
export const FALLBACK_LOCALE = "en";

/** Every language the web app ships a catalogue for. */
export const SUPPORTED_LOCALES: readonly string[] = [FALLBACK_LOCALE, "de"];

// Both catalogues are added eagerly rather than registered as async loaders:
// they are small, and having them in place before the first render means the
// server-rendered HTML never ships raw message keys.
addMessages(FALLBACK_LOCALE, en);
addMessages("de", de);

// The active locale is decided per request/page load in `src/routes/+layout.ts`,
// so `init` only establishes the fallback. Nothing may format a message before
// this ran, which is why every entry point reaches the library through this
// module.
init({fallbackLocale: FALLBACK_LOCALE, initialLocale: FALLBACK_LOCALE});

/**
 * Message formatter for plain TypeScript modules, where svelte-i18n's `$_` store
 * subscription isn't available. It always reflects the active locale, but reading
 * it does not make a component re-render — that is fine because the locale is
 * fixed for the lifetime of a page (it is auto-detected, never switched).
 * Components should use `$_` instead.
 */
export const t = unwrapFunctionStore(format);

/**
 * Narrows a list of BCP 47 language tags, most preferred first, to a locale we
 * actually have messages for. Only the language subtag is compared, so `de-AT`
 * resolves to `de`.
 */
export function resolveLocale(tags: Iterable<string | null | undefined>): string {
    for (const tag of tags) {
        if (!tag) continue;
        const language = tag.trim().toLowerCase().split("-")[0];
        if (SUPPORTED_LOCALES.includes(language)) return language;
    }
    return FALLBACK_LOCALE;
}

/**
 * The browser's preferred locale. `navigator.languages` is consulted after
 * `navigator.language` so a visitor whose primary language we don't ship still
 * gets their next choice instead of the fallback.
 */
export function localeFromNavigator(): string {
    if (typeof navigator === "undefined") return FALLBACK_LOCALE;
    return resolveLocale([navigator.language, ...(navigator.languages ?? [])]);
}

/**
 * The preferred locale of an `Accept-Language` header, honouring its q-values so
 * the client's own ranking decides. Used while server-side rendering, where
 * `navigator` is out of reach.
 */
export function localeFromAcceptLanguage(header: string | null | undefined): string {
    if (!header) return FALLBACK_LOCALE;

    const tags = header
        .split(",")
        .map((entry) => {
            const [tag, ...parameters] = entry.split(";");
            const quality = parameters
                .map((parameter) => parameter.trim())
                .find((parameter) => parameter.startsWith("q="));
            return {tag: tag.trim(), quality: quality ? Number.parseFloat(quality.slice(2)) : 1};
        })
        // A q-value of 0 is an explicit rejection, and `*` matches nothing we ship.
        .filter((entry) => entry.tag !== "" && entry.tag !== "*" && entry.quality > 0)
        .sort((a, b) => b.quality - a.quality)
        .map((entry) => entry.tag);

    return resolveLocale(tags);
}
