# Agent instructions

## Server Builds & Testing
- **DO NOT** run `mvn` or `maven` commands directly on the host / Windows machine.
- If you need to build or run tests for the Java backend, run them inside a Docker container (e.g. `docker-compose up --build -d` or `docker run --rm ... maven:... mvn test`) or do not run them directly at all.

## Web client development

When running the browser client locally:

```bash
docker-compose up --build -d
cd client-web
npm run build   # run before starting — catches compile-time issues
npm run dev
```

Open http://localhost:5173. Vite proxies `/api` and `/game` (WebSocket) to the Docker server on port 3030.

