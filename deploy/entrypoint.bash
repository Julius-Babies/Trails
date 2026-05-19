#!/usr/bin/env bash
set -e

function start_server {
  java -jar /app/server.jar --storage-directory=/data --bind-host=127.0.0.1
}

function start_web {
  cd /app/web
  PUBLIC_BASE_URL=$(jq '.base_url' /data/config.json -r)
  echo "Using base URL: $PUBLIC_BASE_URL"
  echo "PUBLIC_BASE_URL=$PUBLIC_BASE_URL" > .env
  bun index.js
}

function start_proxy {
  cd /app/deploy
  bun proxy.js
}

start_server & start_web
start_proxy