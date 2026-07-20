import tailwindcss from '@tailwindcss/vite';
import {sveltekit} from '@sveltejs/kit/vite';
import {defineConfig} from 'vite';

export default defineConfig({
    plugins: [tailwindcss(), sveltekit()],
    server: {
        allowedHosts: [
            "trails.werkbank.space",
            "trails.julius-babies.wbcloud-dev-juliusbabies-midnight.wbspace.app",
            "trails.julius-babies.wbspace.app",
            "trails.julius-babies.werkbank.werkbank.space",
        ],
        host: true,
    },
});
