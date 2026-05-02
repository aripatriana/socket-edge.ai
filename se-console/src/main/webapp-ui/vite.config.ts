import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'

// ============================================================================
// Vite config — SE-Console frontend.
//
// CRITICAL INTEGRATION POINT:
// The build output goes directly into the Maven resources output directory
// (target/classes/static), NOT the conventional `dist/` folder.
// This way, Spring Boot's static resource handler serves the files
// straight from the classpath inside the fat JAR — no copy step needed.
//
// Why a relative path up four levels?
//   src/main/webapp-ui            <-- we are here (workingDirectory)
//   src/main                      <-- ..
//   src                           <-- ../..
//   <project-root>                <-- ../../..
//   target/classes/static         <-- ../../../target/classes/static
// ============================================================================
export default defineConfig({
  plugins: [react()],

  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },

  build: {
    outDir: '../../../target/classes/static',
    emptyOutDir: true,
    // Keep chunks small-ish so the browser can cache them independently.
    // Spring Boot will gzip on the fly for compressible MIME types.
    chunkSizeWarningLimit: 800,
    // Source maps only for dev; we do not want them inside the production JAR.
    sourcemap: false,
  },

  // During `npm run dev`, proxy API calls to the Spring Boot app running
  // on :8080. This keeps the frontend at :5173 but lets fetch('/api/...')
  // work without CORS gymnastics.
  server: {
    port: 5173,
    proxy: {
      '/api':       { target: 'http://127.0.0.1:8080', changeOrigin: true },
      '/actuator':  { target: 'http://127.0.0.1:8080', changeOrigin: true },
      '/ws':        { target: 'ws://127.0.0.1:8080',   ws: true, changeOrigin: true },
    },
  },
})
