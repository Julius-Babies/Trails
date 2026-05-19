#!/usr/bin/env bash
set -e

function start_server {
  java -jar /app/server.jar --storage-directory=/data --bind-address=127.0.0.1
}

function start_web {
  cd /app/web
  bun index.js
}

function start_proxy {
  cd /app/deploy
  bun proxy.js
}

start_server & start_web
start_proxy