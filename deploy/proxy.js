const API_PORT = 8080;
const WEB_PORT = 3000;

Bun.serve({
  port: 80,
  async fetch(request) {
    const url = new URL(request.url);

    const targetPort = url.pathname.startsWith("/api") ? API_PORT : WEB_PORT;

    const targetUrl = `http://127.0.0.1:${targetPort}${url.pathname}${url.search}`;

    try {
      return await fetch(targetUrl, {
        method: request.method,
        headers: request.headers,
        body: request.body,
        redirect: "manual"
      });
    } catch (error) {
      console.error(`Fehler beim Weiterleiten an Port ${targetPort}:`, error);
      return new Response("Bad Gateway", { status: 502 });
    }
  },
});

console.log("Bun reverse proxy running on port 80");