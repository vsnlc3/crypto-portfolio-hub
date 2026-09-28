# Production Compose

1. Copy `deployment/production.env.example` to `.env.production` on the deployment host.
2. Replace every `REPLACE_...` value. Keep the file outside Git and restrict access to the deployment user (for example, mode `600`).
3. Set `APP_DOMAIN` to the public hostname without a scheme or path. Point its DNS records at the server and allow inbound TCP ports 80 and 443 before starting Caddy.
4. Start the production stack from the repository root:

   ```sh
   docker compose --env-file .env.production -f docker-compose.production.yml up --build -d
   ```

Google OAuth uses the same public host. Register `https://<APP_DOMAIN>/login/oauth2/code/google` as the authorized redirect URI. The sign-in route `/oauth2/authorization/google` and callback route `/login/oauth2/code/google` are proxied to Backend by Caddy.

Production credentials are supplied to containers as runtime environment values. They are not Docker build arguments, Dockerfile instructions, or image files. The encryption key must be a protected Base64-encoded 32-byte value; its generation, storage, rotation, and backup procedures remain deployment operations.
