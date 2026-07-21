FROM node:24.18.0-alpine AS build
WORKDIR /workspace
COPY apps/admin/package*.json ./
RUN npm ci
COPY apps/admin/ .
RUN npm run build

FROM nginx:1.29-alpine
COPY --from=build /workspace/dist/admin/browser /usr/share/nginx/html
COPY infra/nginx/default.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
