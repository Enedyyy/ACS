import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { resolve } from 'node:path';

export default defineConfig({
	base: '/',
	plugins: [vue()],
	publicDir: false, // we already have server-side public/; avoid copying
	build: {
		outDir: resolve(__dirname, './public'),
		emptyOutDir: false, // keep sw.js and styles.css
		assetsDir: 'assets',
		sourcemap: false
	},
	server: {
		port: 5173,
		strictPort: true,
		proxy: {
			'/api': 'http://localhost:8080'
		}
	}
});


