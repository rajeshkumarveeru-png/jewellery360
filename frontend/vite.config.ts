import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
    plugins: [react()],

    server: {
        host: 'localhost',
        port: 5173,

        proxy: {
            '/api': {
                // J360_API_TARGET lets a second backend (e.g. a test database) be used without editing this file
                target: process.env.J360_API_TARGET || 'http://localhost:8080',
                changeOrigin: true,
                secure: false
            }
        }
    }
});