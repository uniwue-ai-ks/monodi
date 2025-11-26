import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'

// https://vitejs.dev/config/
export default defineConfig({
  base: '/',
  plugins: [react()],
  server: {
    proxy: {
      '/pdf': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/resources': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/svgs': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/fuseki/*' : {
        target: 'http://localhost:3030',
        changeOrigin: true,
        secure: false,
      }
    },
    cors: true
  }
})
