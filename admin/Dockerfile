# SvelteKit admin (adapter-node) image for Coolify.
# Build context is the admin/ directory.

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
# API_URL (backend base URL), ORIGIN (public admin URL, needed for form actions).
ENV PORT=3000
EXPOSE 3000

CMD ["node", "build"]
