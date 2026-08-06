# SecureBankAPI

SecureBankAPI is a secure banking REST API built with Spring Boot.

The project provides authentication, account management, money transfers,
transaction history, database migrations, Docker support, automated testing,
and continuous integration with GitHub Actions.

> This project is under active development and is intended as a backend
> portfolio project demonstrating secure API design and modern Spring Boot
> development practices.

## Build Status

[![SecureBank CI](https://github.com/OmidHdr/SecureBankAPI/actions/workflows/ci.yml/badge.svg)](https://github.com/OmidHdr/SecureBankAPI/actions/workflows/ci.yml)

## Features

### Authentication

- User registration
- User login
- Persistent login-attempt limiting with a 15-minute account lock
- IP-based authentication endpoint rate limiting
- JWT-based authentication
- Session-bound access and refresh tokens
- Refresh token rotation and replay detection
- Immediate per-session logout
- Independent multi-device sessions
- Secure password hashing
- JWT expiration validation
- Invalid and expired token handling
- Centralized unauthorized response handling

### Banking

- Bank account management
- Money transfers between accounts
- Transaction history
- Optimistic locking for concurrent transactions
- Database indexes for frequently used queries

### Infrastructure

- PostgreSQL database
- Flyway database migrations
- Docker multi-stage build
- Docker Compose environment
- Persistent PostgreSQL volume
- GitHub Actions CI pipeline
- Automated test execution
- Maven dependency caching
- Separate development, test, and production profiles
- Restricted Actuator exposure and container health checks

## Technology Stack

- Java 21
- Spring Boot
- Spring Security
- Spring Data JPA
- PostgreSQL
- Flyway
- JWT / JJWT
- Maven
- Docker
- Docker Compose
- JUnit
- Testcontainers
- GitHub Actions

## Architecture

The application follows a layered architecture:

```text
Controller
    ↓
Service
    ↓
Repository
    ↓
PostgreSQL
```

Main responsibilities:

- `Controller`: HTTP request and response handling
- `Service`: Business logic and transaction management
- `Repository`: Database access
- `Security`: JWT authentication and authorization
- `DTO`: API request and response models
- `Migration`: Database schema management using Flyway

## Project Structure

```text
SecureBankAPI/
├── .github/
│   └── workflows/
│       └── ci.yml
├── src/
│   ├── main/
│   │   ├── java/ir/h0p3/securebankapi/
│   │   │   ├── account/
│   │   │   ├── auth/
│   │   │   ├── transaction/
│   │   │   ├── user/
│   │   │   └── exception/
│   │   └── resources/
│   │       ├── db/migration/
│   │       └── application.yml
│   └── test/
├── Dockerfile
├── docker-compose.yml
├── pom.xml
└── README.md
```

## Requirements

To run the project locally, install:

- Java 21
- Maven 3.9+
- PostgreSQL

Alternatively, install only:

- Docker
- Docker Compose

## Environment Variables

Create a `.env` file in the project root:

```env
POSTGRES_DB=secure_bank_db
POSTGRES_USER=securebank
POSTGRES_PASSWORD=replace-with-a-strong-database-password

DB_URL=jdbc:postgresql://localhost:5432/secure_bank_db
DB_USERNAME=securebank
DB_PASSWORD=replace-with-a-strong-database-password

APP_PORT=8080
SPRING_PROFILES_ACTIVE=dev

JWT_SECRET=replace-this-with-a-secure-secret-key-at-least-32-bytes
JWT_EXPIRATION=86400000
JWT_REFRESH_EXPIRATION=604800000

LOGIN_LOCKOUT_MAX_FAILED_ATTEMPTS=5
LOGIN_LOCKOUT_DURATION=15m

CORS_ALLOWED_ORIGINS=http://localhost:3000
CORS_ALLOWED_METHODS=GET,POST,PUT,PATCH,DELETE,OPTIONS
CORS_ALLOWED_HEADERS=Authorization,Content-Type,Accept
CORS_ALLOW_CREDENTIALS=false
CORS_MAX_AGE=1h

SWAGGER_ENABLED=false

RATE_LIMIT_TRUST_FORWARDED_HEADERS=false
RATE_LIMIT_BUCKET_EXPIRATION=10m
RATE_LIMIT_MAXIMUM_BUCKETS=10000
RATE_LIMIT_LOGIN_REQUESTS=10
RATE_LIMIT_LOGIN_WINDOW=1m
RATE_LIMIT_REGISTER_REQUESTS=5
RATE_LIMIT_REGISTER_WINDOW=1m
RATE_LIMIT_REFRESH_REQUESTS=20
RATE_LIMIT_REFRESH_WINDOW=1m
RATE_LIMIT_LOGOUT_REQUESTS=20
RATE_LIMIT_LOGOUT_WINDOW=1m
```

Do not commit the `.env` file.

Durations use Spring Boot syntax such as `30s`, `15m`, or `1h`. Comma-separated
values configure CORS lists. JWT and database values are mandatory.

## Spring Profiles

| Profile | Purpose |
| --- | --- |
| `dev` | Default local profile; Swagger enabled, useful application logging, and optional SQL output through `SPRING_JPA_SHOW_SQL` |
| `test` | Automated tests; Swagger and DevTools disabled, database supplied by Testcontainers |
| `prod` | Mandatory external database/JWT settings, Swagger disabled by default, and hardened operational defaults |

Shared settings live in `application.yml`; profile files contain only their
overrides. Run development locally with:

```bash
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Run the packaged application in production mode with externally supplied
secrets:

```bash
SPRING_PROFILES_ACTIVE=prod java -jar target/SecureBankAPI-0.0.1-SNAPSHOT.jar
```

## Run with Docker

Build and start the application:

```bash
docker compose up -d --build
```

View application logs:

```bash
docker compose logs -f app
```

View PostgreSQL logs:

```bash
docker compose logs -f postgres
```

Stop the containers without deleting the database volume:

```bash
docker compose down
```

> Avoid using `docker compose down -v` unless you intentionally want to
> delete the PostgreSQL data volume.

The application always listens on port `8080` inside the container. `APP_PORT`
selects the loopback-only host port, so with the default the API is available at:

```text
http://localhost:8080
```

## CORS

CORS is configured globally through the `CORS_*` variables. Origins, methods,
and headers are explicit; credentials default to disabled. Production startup
rejects credentialed requests combined with a wildcard origin.

## Actuator Policy

Only `health` and `info` are exposed over HTTP. `/actuator/health` is public so
Docker can check readiness without credentials, and health details are hidden.
`/actuator/info` and all other `/actuator/**` requests require valid JWT
authentication. Environment and database properties are not exposed.

## Build

Create the application JAR:

```bash
./mvnw clean package
```

The generated JAR will be available under:

```text
target/
```

## Testing

Run all automated tests:

```bash
./mvnw clean test
```

Current test coverage includes JWT behavior such as:

- Valid token generation
- Username extraction
- Token validation
- Expired token rejection
- Malformed token rejection
- Invalid signature rejection
- JWT configuration validation
- Session creation and validation
- Refresh token rotation
- Logout invalidation
- Multi-device session isolation
- Login lockout persistence, expiry, and successful-login reset

## Authentication Flow

Each registration or login creates an independent authenticated session for
that device or browser. Both returned JWTs contain the session identifier in
the `sid` claim. The server validates that identifier against the
`auth_sessions` table on every authenticated request; possession of a signed
token alone is not enough when its session is revoked or expired.

```text
Login
  → enforce persisted account lock
  → verify password and update failed-attempt state
  → create auth session
  → issue access token and refresh token

Refresh
  → validate active session
  → rotate refresh token
  → issue a new token pair in the same session

Logout
  → revoke the current session
  → revoke related refresh tokens
  → previous access and refresh tokens return 401
```

Each refresh operation invalidates the refresh token it consumes and links it
to its replacement. Reusing a rotated token is rejected as replay and revokes
the existing replacement chain. Logout is immediate and session-scoped:
protected requests with that session's old access token and refresh attempts
with its old refresh token return `401 Unauthorized`. Other device sessions
for the same user remain active and can continue accessing and refreshing.

Five consecutive wrong passwords lock the user account for 15 minutes and
return `423 Locked`. The failed-attempt count, lock flag, and lock time are
persisted in PostgreSQL. A successful login clears that state. After the
15-minute window expires, the next login attempt clears the old lock before
checking the supplied password.

## Authentication Rate Limiting

Authentication requests are limited by client IP before JWT parsing,
password hashing, or database-backed authentication work. The defaults are:

| Endpoint | Default limit |
| --- | --- |
| `POST /api/auth/login` | 10 requests per minute |
| `POST /api/auth/register` | 5 requests per minute |
| `POST /api/auth/refresh` | 20 requests per minute |
| `POST /api/auth/logout` | 20 requests per minute |

The `RATE_LIMIT_*_REQUESTS` and `RATE_LIMIT_*_WINDOW` variables configure each
endpoint. `RATE_LIMIT_BUCKET_EXPIRATION` controls inactive-bucket eviction,
and `RATE_LIMIT_MAXIMUM_BUCKETS` provides a hard memory bound. A rejected
request returns `429 Too Many Requests` with the standard JSON error body and
`Retry-After`, `X-RateLimit-Limit`, and `X-RateLimit-Remaining` headers.

By default, forwarded headers are ignored and the limiter uses the direct
socket address reported by the servlet container. Set
`RATE_LIMIT_TRUST_FORWARDED_HEADERS=true` only when the application is behind
a trusted reverse proxy that removes client-supplied forwarding headers and
sets `X-Forwarded-For` itself. When enabled, the first valid IP literal in
`X-Forwarded-For` is used; missing or malformed values fall back to the direct
remote address.

Buckets are stored in application memory. This is appropriate for the current
single-instance deployment, but limits are per-instance and are not globally
coordinated when multiple application replicas are running.

## Continuous Integration

GitHub Actions automatically runs when code is pushed to `main` or `master`,
and when a pull request is opened.

The CI workflow:

1. Checks out the repository
2. Configures Java 21
3. Restores Maven dependencies
4. Runs automated tests
5. Builds the application

Workflow file:

```text
.github/workflows/ci.yml
```

## Database Migrations

Flyway manages database schema changes.

Migration files are located in:

```text
src/main/resources/db/migration
```

Current migrations include:

- Initial database schema
- Optimistic locking version column
- Query optimization indexes
- Refresh token persistence
- Authenticated sessions
- Login-attempt and timed account-lock state

## Security

The project currently includes:

- BCrypt password hashing
- JWT authentication
- HS256 token signing
- Minimum JWT secret validation
- Token expiration validation
- Session validation on every protected request
- Refresh token rotation and replay protection
- Immediate, session-scoped token revocation
- Centralized authentication failure responses
- Persistent lockout after five consecutive wrong passwords
- Early IP-based rate limiting for authentication endpoints
- Stateless Spring Security configuration
- Non-root Docker runtime user
- Restrictive, configurable CORS
- Public minimal health check with protected operational endpoints
- `nosniff`, frame denial, no-referrer, restrictive Permissions Policy, and no-store headers

No Content Security Policy is applied globally. The API primarily returns JSON,
and a browser-focused global CSP would provide little value while potentially
breaking Swagger when it is deliberately enabled for development.

Production secrets must never be committed to the repository.

## Production Checklist

- Activate the `prod` profile.
- Supply a unique JWT secret of at least 32 bytes and strong database credentials.
- Configure only required CORS origins; keep credentials disabled unless needed.
- Keep Swagger disabled unless access is protected externally.
- Keep `/actuator/health` reachable and do not expose more Actuator endpoints.
- Trust forwarded headers only behind a proxy that removes spoofed values.
- Terminate TLS at a trusted reverse proxy or load balancer.
- Protect persistent database data and application logs.

## Roadmap

Planned features:

- [x] Refresh token support
- [x] Logout and token revocation
- [ ] Password change
- [ ] Email verification
- [x] Login attempt limiter
- [x] Authentication endpoint rate limiting
- [ ] Monthly transfer limit
- [ ] Scheduled transfers
- [ ] Account freeze
- [ ] MapStruct integration
- [x] Extended authentication integration tests
- [x] Improved authentication API documentation
- [ ] Code coverage reporting
- [ ] Docker image publishing
- [ ] Automated deployment

## Development Status

The core API infrastructure is implemented, including authentication,
persistence, migrations, Docker support, and continuous integration.

Additional security and banking features are currently planned and tracked
through GitHub Issues.

## Author

Developed by [OmidHdr](https://github.com/OmidHdr)

## License

This project currently has no published license.
