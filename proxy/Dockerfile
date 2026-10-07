# Hiro-Kids API proxy. Runs on any container host: Cloud Run, a VM, Fly.io, or your own machine with a tunnel.
# Credentials are never baked in: NLB_API_KEY and NLB_APP_CODE arrive as environment variables at run time.
FROM node:22-slim

ENV NODE_ENV=production \
    PORT=8080

WORKDIR /app

# No dependencies (plain Node), so only the package file and the source are needed.
COPY package.json ./
COPY src ./src

# The official image ships a non-root "node" user.
USER node

EXPOSE 8080

# TCP check only: the proxy has no health route, because only the 8 NLB paths are allowed (spec section 2).
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD node -e "require('net').connect(process.env.PORT || 8080, '127.0.0.1').on('connect', () => process.exit(0)).on('error', () => process.exit(1))"

CMD ["node", "src/server.mjs"]
