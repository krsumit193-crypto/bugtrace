import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// During development the React app calls /api/... and Vite forwards those
// requests to Spring Boot on port 8080. No mock data is used anywhere.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
