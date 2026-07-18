# ============================================================
# real-pre image: multi-stage build
# Stage 1: Build static assets
# Stage 2: Serve with nginx
# ============================================================
# Stage 1: Build
FROM node:20-alpine AS builder

WORKDIR /app

RUN npm install -g pnpm

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
ARG VITE_ENV_LABEL=REAL-PRE
ENV VITE_ENV_LABEL=${VITE_ENV_LABEL}
RUN pnpm build

# Stage 2: Serve
FROM nginx:1.27-alpine
ARG GIT_COMMIT=unknown
LABEL org.opencontainers.image.revision=${GIT_COMMIT}

# Remove default nginx site
RUN rm -rf /usr/share/nginx/html/*

# Copy built assets
COPY --from=builder /app/dist /usr/share/nginx/html

# Nginx config template. envsubst runs automatically at startup via
# nginx:alpine's /docker-entrypoint.sh for files in /etc/nginx/templates/.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template

# Defaults; override via docker-compose environment
ENV DOMAIN=_
ENV BACKEND_UPSTREAM=backend-real-pre:8080

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
