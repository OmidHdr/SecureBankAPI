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
DB_USERNAME=securebank
DB_PASSWORD=change-this-password

JWT_SECRET=replace-this-with-a-secure-secret-key-at-least-32-bytes
JWT_EXPIRATION=86400000
JWT_REFRESH_EXPIRATION=604800000
```

Do not commit the `.env` file.

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

The API will be available at:

```text
http://localhost:8080
```

## Run Locally

Start PostgreSQL and configure the required environment variables.

Then run:

```bash
./mvnw spring-boot:run
```

Or:

```bash
mvn spring-boot:run
```

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

## Authentication Flow

Each registration or login creates an independent authenticated session for
that device or browser. Both returned JWTs contain the session identifier in
the `sid` claim.

```text
Login
  → create session
  → issue access token + refresh token

Refresh
  → validate refresh token and session
  → rotate refresh token
  → keep the same session

Logout
  → revoke the current session
  → revoke all refresh tokens for that session
```

Protected requests validate both the access-token signature and its session.
Logging out therefore invalidates that session's access token immediately.
Other sessions for the same user remain active.

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
- Stateless Spring Security configuration
- Non-root Docker runtime user

Production secrets must never be committed to the repository.

## Roadmap

Planned features:

- [x] Refresh token support
- [x] Logout and token revocation
- [ ] Password change
- [ ] Email verification
- [ ] Login attempt limiter
- [ ] Monthly transfer limit
- [ ] Scheduled transfers
- [ ] Account freeze
- [ ] MapStruct integration
- [ ] Extended integration tests
- [ ] Improved API documentation
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
