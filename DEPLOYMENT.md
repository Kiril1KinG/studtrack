# Развертывание и запуск StudTrack

Документ описывает 2 варианта запуска:

1. локально (приложение из IDE/терминала + внешние Postgres/MinIO),
2. полностью в Docker Compose.

---

## 1. Требования

### Для локального запуска

- JDK 21 (рекомендуется).
- Maven Wrapper (`mvnw.cmd`) уже в проекте.
- PostgreSQL 16+.
- MinIO (локально или в Docker).

### Для запуска через Docker

- Docker Desktop (или Docker Engine + Compose plugin).

---

## 2. Переменные и порты по умолчанию

Приложение использует:

- `http://localhost:8080` - StudTrack
- `http://localhost:9000` - MinIO S3 API
- `http://localhost:9001` - MinIO Console
- `localhost:5432` - PostgreSQL

Параметры по умолчанию:

- DB: `studtrack`
- DB user: `studtrack` (в Docker Compose) / `postgres` (в `application.properties`)
- MinIO user: `minioadmin`
- MinIO password: `minioadmin`
- MinIO bucket: `studtrack-attachments`

---

## 3. Быстрый старт (рекомендуется): Docker Compose

Из корня проекта:

```powershell
docker compose up -d --build
```

Проверка статуса:

```powershell
docker compose ps
```

Логи приложения:

```powershell
docker compose logs -f app
```

После старта открой:

- приложение: <http://localhost:8080>
- MinIO Console: <http://localhost:9001>

Остановка:

```powershell
docker compose down
```

Остановка с удалением данных БД/MinIO:

```powershell
docker compose down -v
```

---

## 4. Локальный запуск без контейнера приложения

Этот вариант удобен для разработки в IDE.

### 4.1 Поднять инфраструктуру (Postgres + MinIO)

```powershell
docker compose up -d postgres minio
```

### 4.2 Настроить `application.properties`

Проверь значения в `src/main/resources/application.properties`:

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `minio.endpoint`
- `minio.public-endpoint`
- `minio.access-key`
- `minio.secret-key`
- `minio.bucket`

### 4.3 Запустить приложение

```powershell
./mvnw.cmd spring-boot:run
```

Если в окружении временно нет JDK 21:

```powershell
./mvnw.cmd "-Djava.version=17" "-Dmaven.compiler.release=17" spring-boot:run
```

---

## 5. Сборка и запуск jar

Сборка:

```powershell
./mvnw.cmd clean package -DskipTests
```

Запуск:

```powershell
java -jar target/*.jar
```

При необходимости можно передать параметры через `SPRING_DATASOURCE_*`, `MINIO_*` и т.д.

---

## 6. Первый вход в систему

В проекте нет предзаведенного администратора.
Создай пользователя через страницу регистрации.

Если получаешь ошибку при регистрации, см. блок "Типовые проблемы".

---

## 7. Типовые проблемы

### 7.1 `release version 21 not supported`

Причина: в системе активна JDK ниже 21.

Решение:

- установить/выбрать JDK 21, или
- временно запускать с override(напимер 17):

```powershell
./mvnw.cmd "-Djava.version=17" "-Dmaven.compiler.release=17" <goal>
```

### 7.2 Ошибки БД после изменения модели (`column ... does not exist` / `null value ... violates not-null`)

Причина: схема в существующем volume устарела относительно текущего кода.

Решение для dev-окружения:

```powershell
docker compose down -v
docker compose up -d --build
```

### 7.3 MinIO недоступен / не создается bucket

Проверь:

- доступность `http://localhost:9000`,
- корректность `MINIO_*` параметров,
- наличие логов инициализации bucket в `app`.

### 7.4 Порт уже занят

Если заняты `8080`, `5432`, `9000`, `9001`, измени пробросы портов в `docker-compose.yml` или освободи порты.

---

## 8. Полезные команды

Перезапуск только приложения:

```powershell
docker compose restart app
```

Пересобрать только приложение:

```powershell
docker compose build app
docker compose up -d app
```

Запустить тесты:

```powershell
./mvnw.cmd test
```

Покрытие + отчеты JaCoCo:

```powershell
./mvnw.cmd verify
```

Подробно про тестирование: `TESTING.md`.
