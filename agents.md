# Agent instructions

## Web client development

When running the browser client locally:

```bash
docker-compose up --build -d
cd client-web
npm run build   # run before starting — catches compile-time issues
npm run dev
```

Open http://localhost:5173. Vite proxies `/api` and `/game` (WebSocket) to the Docker server on port 3030.
