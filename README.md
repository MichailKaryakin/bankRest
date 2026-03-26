# Bank Cards API

REST API для управления банковскими картами с аутентификацией через JWT, ролевым доступом и шифрованием номеров карт.

## Технологии

- **Java 21**
- **Spring Boot 4.0.4**
- **Spring Security**
- **JWT**
- **Spring Data JPA**
- **PostgreSQL**
- **Liquibase**
- **Docker**
- **Docker Compose**
- **Swagger UI**

---

## Запуск в контейнере (Docker Compose)

```bash
git clone https://github.com/MichailKaryakin/bankRest.git
cd bankRest
docker-compose up --build
```

Приложение будет доступно на `http://localhost:8080`  
Swagger UI: `http://localhost:8080/swagger-ui.html`

---

## Запуск локально

```bash
# 1. Клонировать репозиторий
git clone https://github.com/MichailKaryakin/bankRest.git
cd bankRest

# 2. Запустить PostgreSQL и создать базу
psql -U postgres -c "CREATE DATABASE bankRest;"

# 3. Собрать и запустить
mvn spring-boot:run
```
---

## Переменные окружения (сейчас в application.yml)

| Переменная               | По умолчанию    | Описание                       |
|--------------------------|-----------------|--------------------------------|
| `DB_HOST`                | `localhost`     | Хост PostgreSQL                |
| `DB_PORT`                | `5438`          | Порт PostgreSQL                |
| `DB_NAME`                | `bankRest`      | Имя базы данных                |
| `DB_USERNAME`            | `postgres`      | Юзер БД                        |
| `DB_PASSWORD`            | `postgres`      | Пароль БД                      |
| `JWT_SECRET`             | `404E635266...` | Секретный ключ                 |
| `JWT_EXPIRATION`         | `86400000`      | Время жизни токена, мс         |
| `JWT_REFRESH_EXPIRATION` | `604800000`     | Время жизни refresh токена, мс |
| `SERVER_PORT`            | `8080`          | Порт приложения                |

---

## Дефолтный администратор

Создаётся автоматически миграцией `003-insert-default-admin.yaml`:

| Поле     | Значение   |
|----------|------------|
| Username | `admin`    |
| Password | `admin123` |
| Role     | `ADMIN`    |

---

## API

### Аутентификация

| Метод  | URL                     | Доступ | Описание                      |
|--------|-------------------------|--------|-------------------------------|
| `POST` | `/api/v1/auth/register` | Public | Регистрация                   |
| `POST` | `/api/v1/auth/login`    | Public | Логин и выдача пары токенов   |
| `POST` | `/api/v1/auth/refresh`  | Public | Выдача новой пары токенов     |
| `POST` | `/api/v1/auth/logout`   | Public | Выход и отзыв refresh токенов |

### Карты (пользователь)

| Метод  | URL                                | Описание                                   |
|--------|------------------------------------|--------------------------------------------|
| `GET`  | `/api/v1/cards`                    | Карты юзера (фильтр по статусу, пагинация) |
| `GET`  | `/api/v1/cards/{id}`               | Конкретная карта по ID                     |
| `POST` | `/api/v1/cards/{id}/block-request` | Запросить блокировку карты                 |
| `POST` | `/api/v1/cards/transfer`           | Перевод между своими картами               |

### Карты (администратор)

| Метод    | URL                               | Описание                       |
|----------|-----------------------------------|--------------------------------|
| `POST`   | `/api/v1/admin/cards`             | Создать карту                  |
| `GET`    | `/api/v1/admin/cards`             | Все карты (фильтр + пагинация) |
| `GET`    | `/api/v1/admin/cards/{id}`        | Карта по ID                    |
| `PATCH`  | `/api/v1/admin/cards/{id}/status` | Сменить статус карты           |
| `DELETE` | `/api/v1/admin/cards/{id}`        | Удалить карту                  |

### Пользователи (администратор)

| Метод    | URL                               | Описание                     |
|----------|-----------------------------------|------------------------------|
| `GET`    | `/api/v1/admin/users`             | Все пользователи (пагинация) |
| `GET`    | `/api/v1/admin/users/{id}`        | Пользователь по ID           |
| `PATCH`  | `/api/v1/admin/users/{id}/toggle` | Включить/выключить аккаунт   |
| `DELETE` | `/api/v1/admin/users/{id}`        | Удалить пользователя         |

---

## Запуск тестов

```bash
mvn test
```

---

## Безопасность

- Номера карт хранятся зашифрованными **AES-256-GCM**, IV генерируется случайно для каждого шифрования
- В ответах API отображается только маска: `**** **** **** 1234`
- Пароли хешируются через **BCrypt**
- JWT токены подписываются **HS256**
- Реализован механизм refresh токенов
- Ролевой доступ через `@PreAuthorize` + `SecurityConfig`