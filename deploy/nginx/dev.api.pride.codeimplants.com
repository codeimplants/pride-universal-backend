# Pride Universal dev API → the pride-api-dev container on 127.0.0.1:8111 (scripts/deploy.sh).
# Installed by deploy/install-nginx.sh; certbot adds the 443 block and the http→https redirect.
server {
    listen 80;
    server_name dev.api.pride.codeimplants.com;

    location / {
        client_max_body_size 12M;

        proxy_pass http://127.0.0.1:8111;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        # Overwrite, not append: the OTP rate limiter trusts the first address in this header.
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 60s;
    }

    location /actuator/ {
        return 404;
    }
    # Nexus calls the admin API over loopback (127.0.0.1); it is never public.
    location /api/admin/ {
        return 404;
    }
}
