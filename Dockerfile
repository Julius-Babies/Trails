FROM oven/bun:alpine AS bun-env

FROM eclipse-temurin:25-jre-alpine

RUN apk add --no-cache bash jq

RUN apk add --no-cache libstdc++
COPY --from=bun-env /usr/local/bin/bun /usr/local/bin/bun

WORKDIR /app
COPY web_build /app/web
COPY server-all.jar /app/server.jar
COPY deploy /app/deploy

EXPOSE 80

ENTRYPOINT ["bash", "/app/deploy/entrypoint.bash"]