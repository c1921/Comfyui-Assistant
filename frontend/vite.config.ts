import fs from 'node:fs'
import path from 'node:path'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    tailwindcss()
  ],
  server: {
    host: true,        // 等价于 0.0.0.0
    port: 5173,
    proxy: (() => {
      const configPath = path.resolve(__dirname, '../backend/config.json')
      let pcIp = ''
      let pcPort = '8000'
      try {
        const raw = fs.readFileSync(configPath, 'utf-8')
        const parsed = JSON.parse(raw)
        if (typeof parsed?.pcIp === 'string') pcIp = parsed.pcIp
        if (typeof parsed?.pcPort === 'string') pcPort = parsed.pcPort
      } catch {
        // Ignore config load failures
      }
      const host = pcIp.trim() || 'localhost'
      const port = pcPort.trim()
      const target = port ? `http://${host}:${port}` : `http://${host}`
      return {
        '/api': { target, changeOrigin: true },
        '/ws': { target, changeOrigin: true, ws: true },
      }
    })(),
  },
})
