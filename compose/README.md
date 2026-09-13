# Local PostgreSQL

Запускать из этой папки:

```powershell
Copy-Item .env.example .env
docker compose up -d
docker compose ps
```

Параметры подключения с хоста:

```text
Host:     localhost
Port:     5432
Database: reelcipe
User:     reelcipe
Password: reelcipe-local
```

Остановка без удаления данных:

```powershell
docker compose stop
```

Полное удаление контейнера и локальных данных:

```powershell
docker compose down -v
```

Файл `.env` предназначен только для локальной разработки и не должен попадать в Git.
