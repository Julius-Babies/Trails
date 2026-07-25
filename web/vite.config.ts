import tailwindcss from '@tailwindcss/vite';
import {sveltekit} from '@sveltejs/kit/vite';
import {defineConfig} from 'vite';

export default defineConfig({
    plugins: [tailwindcss(), sveltekit()],
    server: {
        allowedHosts: [
            "trails.werkbank.space",
            "trailsdevelopment.jvbabi.es",
            "trails.julius-babies.wbspace.app"
        ],
        host: '127.0.0.1',
        strictPort: true,
        hmr: {
            clientPort: 443,
            protocol: 'wss'
        }
    }
});
