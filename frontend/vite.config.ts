import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [
      react(),
      VitePWA({
        registerType: 'autoUpdate',
        includeAssets: ['favicon.svg', 'apple-touch-icon.png'],
        manifest: {
          name: 'NexusLibrary',
          short_name: 'Nexus',
          description: 'Track your games, films, shows, anime and books in one place.',
          theme_color: '#f5f7fa',
          background_color: '#f5f7fa',
          display: 'standalone',
          start_url: '/',
          scope: '/',
          icons: [
            { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' },
            { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png' },
            {
              src: 'pwa-maskable-512x512.png',
              sizes: '512x512',
              type: 'image/png',
              purpose: 'maskable',
            },
          ],
        },
        workbox: {
          // API responses are user-scoped and auth-gated; caching them in the SW would
          // risk serving one account's data to the next session on a shared device.
          // A rebuilt app takes over on the next load rather than the one after it: without
          // these the service worker keeps serving the previous bundle for a reload, which
          // reads as a fix that did not land.
          skipWaiting: true,
          clientsClaim: true,
          cleanupOutdatedCaches: true,
          navigateFallbackDenylist: [/^\/api/],
        },
      }),
    ],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: env.VITE_API_BASE_URL || 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  }
})
