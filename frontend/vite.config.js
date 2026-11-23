import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// During development the React app calls /api/... and Vite forwards those
// requests to Spring Boot on port 8080. No mock data is used anywhere.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Cloudflare quick-tunnel hosts must be allowed for the public demo URL.
    // This applies to the dev server only; production builds are unaffected.
    allowedHosts: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
