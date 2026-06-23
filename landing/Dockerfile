# SvelteKit landing site (adapter-node) image for Coolify.
# Build context is the landing/ directory.

# ── Build stage ───────────────────────────────────────────────
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build && npm prune --omit=dev

# ── Runtime stage ─────────────────────────────────────────────
FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production

COPY --from=build /app/build ./build
COPY --from=build /app/node_modules ./node_modules
COPY --from=build /app/package.json ./package.json

# adapter-node listens on PORT (default 3000). Provided by Coolify:
#   ORIGIN                public site URL (https://teamorg.ch) — needed for form actions
#   API_URL              backend base URL (http://server:8080 on the compose network)
#   CONTACT_SHARED_SECRET shared secret sent to the backend /contact endpoint
#   TURNSTILE_SECRET     Cloudflare Turnstile secret key (server-side verification)
#   TURNSTILE_SITEKEY    Turnstile site key (delivered to the browser via page data)
ENV PORT=3000
EXPOSE 3000

CMD ["node", "build"]
