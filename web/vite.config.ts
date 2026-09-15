import path from "node:path"
import tailwindcss from "@tailwindcss/vite"
import react from "@vitejs/plugin-react"
import basicSsl from "@vitejs/plugin-basic-ssl"
import { existsSync, readFileSync } from "node:fs"
import { VitePWA } from "vite-plugin-pwa"
import { defineConfig, type Plugin } from "vitest/config"

/** In development the static manual is rendered on request, so /docs works without a build. */
function docsDevServer(): Plugin {
  return {
    name: "mizan-docs-dev",
    apply: "serve",
    configureServer(server) {
      server.middlewares.use(async (req, res, next) => {
        const url = (req.url ?? "").split("?")[0]
        if (!url.startsWith("/docs")) return next()
        if (url === "/docs" || url === "/docs/") { res.statusCode = 302; res.setHeader("Location", "/docs/en/"); return res.end() }
        if (/^\/docs\/(en|ar)$/.test(url)) { res.statusCode = 302; res.setHeader("Location", `${url}/`); return res.end() }
        const { renderDocs } = await import("./scripts/build-docs.mjs")
        const key = url.replace(/^\/docs\//, "").replace(/\/$/, "/index.html")
        const html = renderDocs().files.get(key)
        if (!html) return next()
        res.setHeader("Content-Type", "text/html; charset=utf-8")
        res.end(html)
      })
    },
  }
}

/**
 * `npm run dev:lan` serves the app to phones on the same network. Service workers, the WebCrypto
 * PIN lock and WebAuthn need a secure origin, so the dev server runs HTTPS: with the certificate
 * files in VITE_SSL_CERT/VITE_SSL_KEY (for example from mkcert) when given, else a self-signed one.
 */
const lan = process.env.VITE_LAN === "1"
const ownCertificate = process.env.VITE_SSL_CERT && process.env.VITE_SSL_KEY && existsSync(process.env.VITE_SSL_CERT) && existsSync(process.env.VITE_SSL_KEY)

export default defineConfig({
  plugins: [
    react(),
    docsDevServer(),
    ...(lan && !ownCertificate ? [basicSsl()] : []),
    tailwindcss(),
    VitePWA({
      registerType: "prompt",
      // Custom worker: precache plus the push and notification-click handlers (FR-NTF-01).
      strategies: "injectManifest",
      srcDir: "src",
      filename: "sw.ts",
      includeAssets: ["favicon.svg"],
      manifest: {
        name: "Mizan",
        short_name: "Mizan",
        description: "Monthly plan and net worth, offline first.",
        lang: "en",
        dir: "ltr",
        start_url: "/",
        display: "standalone",
        background_color: "#eef2fa",
        theme_color: "#88aaee",
        icons: [
          { src: "icons/icon-192.png", sizes: "192x192", type: "image/png" },
          { src: "icons/icon-512.png", sizes: "512x512", type: "image/png" },
          { src: "icons/icon-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
        ],
      },
      injectManifest: {
        globPatterns: ["**/*.{js,css,html,svg,png,woff2}"],
        // The OCR worker, core and models are fetched on first use and kept by a runtime cache.
        globIgnores: ["**/ocr/**"],
      },
    }),
  ],
  resolve: {
    alias: { "@": path.resolve(import.meta.dirname, "./src") },
  },
  server: {
    host: lan ? true : undefined,
    // Tunnels such as ngrok present their own hostname; Vite blocks unknown hosts otherwise.
    allowedHosts: [".ngrok-free.app", ".ngrok-free.dev", ".ngrok.app", ".ngrok.dev", ".ngrok.io", ...(process.env.VITE_ALLOWED_HOST ? [process.env.VITE_ALLOWED_HOST] : [])],
    https: ownCertificate ? { cert: readFileSync(process.env.VITE_SSL_CERT!), key: readFileSync(process.env.VITE_SSL_KEY!) } : undefined,
    // The manual lives in ../docs so the static site and the app share one source.
    fs: { allow: [".."] },
    proxy: {
      "/api": {
        target: process.env.API_ORIGIN ?? "http://localhost:8080",
        changeOrigin: true,
        // Phones reach the API through this proxy, so the API sees the origin it allows in dev,
        // not https://<lan-ip>:5173; its CORS list stays the production one.
        configure: (proxy) => {
          proxy.on("proxyReq", (proxyReq, req) => {
            if (req.headers.origin) proxyReq.setHeader("origin", process.env.VITE_DEV_ORIGIN ?? "http://localhost:5173")
          })
        },
      },
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    globals: true,
  },
})
