FROM node:20-slim
WORKDIR /app
COPY package.json package-lock.json .npmrc ./
RUN npm ci --omit=dev
COPY src ./src
COPY public ./public
ENV NODE_ENV=production
EXPOSE 8787
CMD ["npx", "tsx", "src/server.ts"]
