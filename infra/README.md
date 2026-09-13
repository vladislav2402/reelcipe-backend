# Local infrastructure

The canonical B02 Compose configuration lives in `compose.local.yml`.

## Start

Run from the backend root:

```powershell
Copy-Item infra/.env.local.example infra/.env.local
docker compose --env-file infra/.env.local -f infra/compose.local.yml up -d --build --wait
docker compose --env-file infra/.env.local -f infra/compose.local.yml ps
```

Services:

- PostgreSQL: `localhost:5432`, database `reelcipe`, user `reelcipe`, password `reelcipe_local`;
- S3Mock: `http://localhost:9090`, bucket `reelcipe-local`;
- media-tools: internal container with FFmpeg/ffprobe and no network access.

The Java processes launched from IDEA are intentionally not part of this Compose file.
Start `Reelcipe API (local)` and `Reelcipe Worker (local)` separately from IDEA.

For Java processes started from IDEA, use `localhost:5432` and `http://localhost:9090`.

## Stop

```powershell
docker compose --env-file infra/.env.local -f infra/compose.local.yml stop
```

`stop` preserves named volumes. To remove containers and local data explicitly:

```powershell
docker compose --env-file infra/.env.local -f infra/compose.local.yml down -v
```

`.env.local` is local-only and must not be committed.
