# Cloud Storage API

A comprehensive cloud storage platform REST API built with Spring Boot. This backend service provides secure file management, authentication, batch operations, and API key management capabilities.

## Features

- **Authentication & Authorization**

  - JWT-based authentication with access and refresh tokens
  - API key authentication for programmatic access
  - Role-based access control (RBAC) with granular permissions
  - HttpOnly cookies for secure refresh token storage
  - Token rotation for enhanced security

- **File Management**

  - Upload files to cloud storage (Cloudinary)
  - Download files with signed URLs
  - List and search files with pagination
  - File metadata management
  - Image/video transformations on-the-fly
  - File statistics and analytics

- **Folder Management**

  - Create and organize files in folders (virtual folders)
  - Folder path validation and normalization
  - Folder statistics
  - Folders are virtual - they exist when files have matching folder paths

- **Batch Operations**

  - Batch job status tracking
  - Progress monitoring
  - Note: Bulk upload functionality exists in the service layer but is not currently exposed via REST endpoint

- **API Key Management**

  - Generate and manage API keys
  - Permission-based API keys (READ_ONLY, READ_WRITE, FULL_ACCESS)
  - API key expiration and revocation

- **Security**
  - Password encryption with BCrypt
  - CORS configuration
  - Input validation and sanitization
  - SQL injection prevention
  - Path traversal protection

## Tech Stack

- **Framework**: Spring Boot 3.5.7
- **Language**: Java 25
- **Build Tool**: Maven
- **Database**: PostgreSQL 14+
- **ORM**: Spring Data JPA / Hibernate
- **Authentication**: JWT (jjwt 0.12.6)
- **File Storage**: Cloudinary
- **Database Migrations**: Flyway
- **API Documentation**: SpringDoc OpenAPI 2.8.9
- **Testing**: JUnit 5, Mockito, Testcontainers
- **Logging**: SLF4J with Logback

## Prerequisites

- **Java**: JDK 25 or higher
- **Maven**: 3.6+
- **PostgreSQL**: 14+ (for local development)
- **Cloudinary Account**: For cloud file storage
- **Environment Variables**: Support for .env files (via dotenv-java)

## Getting Started

### Installation

1. Clone the repository:

```bash
git clone <repository-url>
cd cloud-storage-api
```

2. Install dependencies:

```bash
mvn clean install
```

### Database Setup

1. Create a PostgreSQL database:

```sql
CREATE DATABASE cloud_storage_api;
CREATE USER storage_user WITH PASSWORD 'your_password';
GRANT ALL PRIVILEGES ON DATABASE cloud_storage_api TO storage_user;
```

2. The application will automatically run Flyway migrations on startup to create the required tables.

### Configuration

1. Copy the example configuration file:

```bash
cp src/main/resources/application-dev.yaml.example src/main/resources/application-dev.yaml
```

2. Edit `src/main/resources/application-dev.yaml` with your configuration:

```yaml
# Database Configuration
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/cloud_storage_api
    username: storage_user
    password: your_password_here
    driver-class-name: org.postgresql.Driver

# Cloudinary Configuration
cloudinary:
  cloud-name: your_cloud_name
  api-key: your_api_key
  api-secret: your_api_secret
  secure: true
  folder: user-files

# Security Configuration
app:
  security:
    jwt:
      secret: your-secret-key-change-in-production # Must be at least 32 characters (256 bits)
      access-token-expiration-web: 900000 # 15 minutes (default)
      refresh-token-expiration-web: 604800000 # 7 days (default)
    api-key:
      header-name: X-API-Key # Optional: Defaults to "X-API-Key"
    cookie:
      secure: true # Optional: Defaults to true
      same-site: Strict # Optional: Must be Strict, Lax, or None (case-insensitive, defaults to Strict)
    cors:
      allowed-origins:
        - http://localhost:3000
        - http://localhost:5173
      allowed-methods:
        - GET
        - POST
        - PUT
        - DELETE
        - OPTIONS
      allowed-headers:
        - "*" # Note: Will be replaced with explicit headers if allow-credentials is true
      allow-credentials: true
      max-age: 3600
```

**Important**: The `application-dev.yaml` file is in `.gitignore` and will not be committed. Never commit sensitive credentials.

### Environment Variables

You can also use environment variables or a `.env` file. The application supports dotenv-java for loading environment variables from a `.env` file in the project root.

## Running the Application

### Development Mode

Run the application with Maven:

```bash
mvn spring-boot:run
```

The application will start on port 8000 (configurable in `application.yaml`).

### Build and Run

Build the application:

```bash
mvn clean package
```

Run the JAR file:

```bash
java -jar target/cloud-storage-api-0.0.1-SNAPSHOT.jar
```

### Running Tests

Run all tests:

```bash
mvn test
```

Run tests with coverage:

```bash
mvn test jacoco:report
```

## API Documentation

### Swagger UI

Once the application is running, access the interactive API documentation at:

- **Swagger UI**: http://localhost:8000/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8000/v3/api-docs

### Authentication

The API supports two authentication methods:

1. **JWT Bearer Token**

   - Obtain a token via `/api/auth/login` or `/api/auth/register`
   - Include in the `Authorization` header: `Bearer <token>`
   - Access tokens expire (15 minutes default, configurable)
   - Refresh tokens are stored in HttpOnly cookies

2. **API Key**
   - Generate an API key via `/api/auth/api-keys`
   - Include in the `X-API-Key` header
   - API keys support different permission levels

## API Endpoints

### Authentication (`/api/auth`)

- `POST /api/auth/register` - Register a new user
- `POST /api/auth/login` - Login and receive JWT tokens
- `POST /api/auth/refresh` - Refresh access token
- `POST /api/auth/logout` - Logout and invalidate refresh token
- `GET /api/auth/me` - Get current user information
- `POST /api/auth/api-keys` - Generate a new API key
- `GET /api/auth/api-keys` - List all API keys
- `GET /api/auth/api-keys/{id}` - Get API key details
- `DELETE /api/auth/api-keys/{id}` - Revoke an API key

### Files (`/api/files`)

- `POST /api/files/upload` - Upload a file
- `GET /api/files` - List files with pagination and filters
- `GET /api/files/{id}` - Get file metadata
- `GET /api/files/{id}/url` - Get signed download URL
- `GET /api/files/{id}/download` - Download a file
- `PUT /api/files/{id}` - Update file metadata
- `DELETE /api/files/{id}` - Delete a file (soft delete)
- `POST /api/files/{id}/transform` - Transform image/video
- `GET /api/files/{id}/transform` - Get transformation URL
- `GET /api/files/search` - Search files by filename
- `GET /api/files/statistics` - Get file statistics

### Folders (`/api/folders`)

- `POST /api/folders` - Create a folder (validates path, folders are virtual)
- `GET /api/folders` - List folders (optional `parentPath` query parameter)
- `GET /api/folders/statistics` - Get folder statistics
- `DELETE /api/folders?path={path}` - Delete a folder (path as query parameter)

### Batch Operations (`/api/batches`)

- `GET /api/batches/{id}/status` - Get batch job status and progress

### API Keys (`/api/api-keys`)

- `POST /api/api-keys/verify` - Verify API key and get user information

### Health Check (`/api/health`)

- `GET /api/health` - Application health status

## Database Migrations

The application uses Flyway for database version control. Migrations are located in `src/main/resources/db/migration/`.

### Migration Versions

- **V1**: Create users table
- **V2**: Create files table
- **V3**: Create batch_jobs table
- **V4**: Create api_keys table
- **V5**: Create triggers and functions
- **V6**: Add permissions to api_keys
- **V7**: Rename write_only to read_write

Migrations run automatically on application startup. The application validates that all migrations have been applied.

## Security Features

### Authentication & Authorization

- **JWT Tokens**: Secure token-based authentication with configurable expiration
- **API Keys**: Programmatic access with permission levels
- **Role-Based Access Control**:
  - `ROLE_READ` - Read-only access
  - `ROLE_WRITE` - Read and write access
  - `ROLE_DELETE` - Delete operations
  - `ROLE_MANAGE_API_KEYS` - API key management

### Security Best Practices

- Password hashing with BCrypt (strength 10, hardcoded in SecurityConfig)
- HttpOnly cookies for refresh tokens
- CORS configuration for cross-origin requests
- Input validation and sanitization
- Path traversal protection for folder paths
- SQL injection prevention via JPA
- Token rotation for refresh tokens

### Cookie Configuration

The `cookieSameSite` configuration must be one of: `Strict`, `Lax`, or `None` (case-insensitive). Invalid values will cause the application to fail fast on startup.

## Project Structure

```
cloud-storage-api/
├── src/
│   ├── main/
│   │   ├── java/github/vijay_papanaboina/cloud_storage_api/
│   │   │   ├── config/          # Configuration classes
│   │   │   │   ├── CloudinaryConfig.java
│   │   │   │   ├── OpenApiConfig.java
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   └── WebConfig.java
│   │   │   ├── controller/      # REST controllers
│   │   │   │   ├── AuthController.java
│   │   │   │   ├── FileController.java
│   │   │   │   ├── FolderController.java
│   │   │   │   └── BatchController.java
│   │   │   ├── service/         # Business logic
│   │   │   │   ├── AuthServiceImpl.java
│   │   │   │   ├── FileServiceImpl.java
│   │   │   │   └── storage/     # Storage service implementations
│   │   │   ├── repository/      # Data access layer
│   │   │   ├── model/           # Entity classes
│   │   │   ├── dto/             # Data transfer objects
│   │   │   ├── exception/       # Custom exceptions
│   │   │   ├── security/        # Security utilities
│   │   │   └── validation/      # Custom validators
│   │   └── resources/
│   │       ├── application.yaml
│   │       ├── application-dev.yaml.example
│   │       └── db/migration/    # Flyway migrations
│   └── test/                    # Test classes
├── pom.xml                      # Maven configuration
└── README.md                    # This file
```

## Testing

### Test Structure

- **Unit Tests**: `src/test/java/` - Service and controller unit tests
- **Integration Tests**: Testcontainers for database integration tests
- **Test Coverage**: Comprehensive coverage of controllers, services, and repositories

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=FileControllerTest

# Run with coverage
mvn test jacoco:report
```

### Test Configuration

- Uses Testcontainers for PostgreSQL integration tests
- MockMvc for controller testing
- Mockito for service mocking
- Test profile configuration

## Development Guidelines

### Code Structure

- Follow Spring Boot best practices
- Use dependency injection
- Implement proper exception handling
- Follow RESTful API design principles
- Use DTOs for request/response objects

### Exception Handling

- Custom exceptions in `exception/` package
- Global exception handler (`GlobalExceptionHandler`)
- Proper HTTP status codes
- Meaningful error messages

### Security Practices

- Always validate user input
- Use parameterized queries (JPA handles this)
- Implement proper authorization checks
- Never expose sensitive information in error messages
- Use secure defaults for configuration

### API Design

- RESTful resource naming
- Proper HTTP methods (GET, POST, PUT, DELETE)
- Consistent response formats
- Pagination for list endpoints
- Filtering and sorting support

## Deployment

### Building for Production

1. Build the application:

```bash
mvn clean package -DskipTests
```

2. The JAR file will be created at: `target/cloud-storage-api-0.0.1-SNAPSHOT.jar`

### Production Configuration

1. Create `application-prod.yaml` with production settings
2. Set environment variables for sensitive data
3. Configure production database connection
4. Set secure JWT secret
5. Configure CORS for production domains
6. Enable production logging levels

### Database Migrations in Production

Flyway will automatically run migrations on startup. Ensure:

- Database backups are taken before deployment
- Migrations are tested in staging environment
- Rollback plan is in place

### Health Checks

Monitor application health via:

- `/api/health` - Application health status
- `/actuator/health` - Spring Boot Actuator health endpoint

## Troubleshooting

### Common Issues

**Database Connection Errors**

- Verify PostgreSQL is running
- Check database credentials in `application-dev.yaml`
- Ensure database exists and user has proper permissions

**Cloudinary Errors**

- Verify Cloudinary credentials are correct
- Check API key and secret in configuration
- Ensure cloud name is correct

**JWT Token Issues**

- Verify JWT secret is set in configuration
- Check token expiration settings
- Ensure refresh token cookie is being set correctly

**CORS Errors**

- Verify CORS configuration includes your frontend origin
- Check `allowed-origins` in security configuration
- Ensure credentials are allowed if using cookies

### Debug Mode

Enable debug logging in `application.yaml`:

```yaml
logging:
  level:
    github.vijay_papanaboina.cloud_storage_api: DEBUG
    org.springframework.security: DEBUG
```

### Logs

Application logs are written to:

- Console output
- `logs/application.log` (if configured)

## Contributing

1. Create a feature branch from `main`
2. Make your changes following the code style guidelines
3. Write tests for new functionality
4. Ensure all tests pass: `mvn test`
5. Update documentation as needed
6. Submit a pull request

### Code Style

- Follow Java naming conventions
- Use meaningful variable and method names
- Add JavaDoc comments for public methods
- Keep methods focused and small
- Use proper exception handling

## License

Apache License 2.0 - See LICENSE file for details

## Support

For issues and questions:

- Open an issue in the repository
- Check the API documentation at `/swagger-ui.html`
- Review the code comments and JavaDoc
