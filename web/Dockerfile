# Built from the repository root: docker build -f web/Dockerfile .
FROM node:24-alpine AS build
WORKDIR /build
COPY web/package.json web/package-lock.json ./
RUN npm ci
COPY web/ .
COPY shared/ ../shared/
RUN npm run build

# Caddy serves the PWA and terminates TLS; /api is proxied to the API container.
FROM caddy:2-alpine
COPY --from=build /build/dist /srv
COPY infra/caddy/Caddyfile /etc/caddy/Caddyfile
