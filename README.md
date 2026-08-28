# DigiBP Classroom

[![License](http://img.shields.io/:license-apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

Spring Boot application providing a tenant-aware CIB seven classroom environment with Tasklist, Cockpit, Admin and Modeler.

API documentation is available through Swagger UI at `http://localhost:8080/apis`. It provides separate definitions for the DigiBP Message API, DigiBP Classroom API and CIB seven REST API.

## Build and run

The application requires Java 17 and Maven.

```shell
mvn clean package
java -jar target/digibp-classroom.jar
```

Local settings belong in `src/main/resources/application-local.yaml`. The `prod` profile reads its database and runtime configuration from environment variables.

### Production configuration

| Environment variable | Required | Description |
|---|---:|---|
| `SPRING_DATASOURCE_URL` | yes | JDBC URL, for example `jdbc:postgresql://localhost:5432/classroom` |
| `SPRING_DATASOURCE_USERNAME` | yes | Database user |
| `SPRING_DATASOURCE_PASSWORD` | yes | Database password |
| `CIBSEVEN_WEBCLIENT_AUTHENTICATION_JWTSECRET` | yes | Base64-decodable JWT secret shared by the webclient and REST API |
| `CIBSEVEN_ENGINE_REST_URL` | yes in production | Public application URL without `/engine-rest`, for example `https://example.org` |
| `CIBSEVEN_ADMIN_PASSWORD` | yes in production | Password of the initial `demo` administrator |
| `CORS_ENABLED` | no | Enables CORS; defaults to `false` in the `prod` profile |
| `CORS_ORIGIN` | no | Allowed CORS origin; defaults to `*` |
| `OPENAI_API_KEY` | when using AI tasks | API key for the AI Agent connector |
| `OPENAI_BASE_URL` | no | OpenAI-compatible API base URL |
| `CIBSEVEN_CONNECT_AI_AGENT_DEFAULT_MODEL` | no | Default model for AI Agent tasks |

Run the packaged application in production mode with:

```shell
java -jar target/digibp-classroom.jar --spring.profiles.active=prod
```

### Docker

The Docker image builds the application with Java 17 and runs it as a non-root user:

```shell
docker build -t digibp-classroom .
docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/classroom \
  -e SPRING_DATASOURCE_USERNAME=classroom \
  -e SPRING_DATASOURCE_PASSWORD=secret \
  -e CIBSEVEN_ADMIN_PASSWORD=secret \
  -e CIBSEVEN_WEBCLIENT_AUTHENTICATION_JWTSECRET="$(openssl rand -base64 64)" \
  -e CIBSEVEN_ENGINE_REST_URL=http://localhost:8080 \
  digibp-classroom
```

### Docker Compose

The released image can be run together with PostgreSQL using Docker Compose. Save the following as `compose.yaml`:

```yaml
services:
  classroom:
    image: ghcr.io/digibp/digibp-classroom:${IMAGE_TAG:-latest}
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/classroom
      SPRING_DATASOURCE_USERNAME: classroom
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD must be set}
      CIBSEVEN_ADMIN_PASSWORD: ${CIBSEVEN_ADMIN_PASSWORD:?CIBSEVEN_ADMIN_PASSWORD must be set}
      CIBSEVEN_WEBCLIENT_AUTHENTICATION_JWTSECRET: ${CIBSEVEN_JWT_SECRET:?CIBSEVEN_JWT_SECRET must be set}
      CIBSEVEN_ENGINE_REST_URL: ${PUBLIC_URL:-http://localhost:8080}
      CORS_ENABLED: ${CORS_ENABLED:-false}
      CORS_ORIGIN: ${CORS_ORIGIN:-*}
    depends_on:
      postgres:
        condition: service_healthy

  postgres:
    image: postgres:17-alpine
    restart: unless-stopped
    environment:
      POSTGRES_DB: classroom
      POSTGRES_USER: classroom
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD must be set}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U classroom -d classroom"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres-data:
```

Create a `.env` file next to `compose.yaml`. Do not commit this file:

```dotenv
IMAGE_TAG=v1.0.0
POSTGRES_PASSWORD=replace-with-a-strong-database-password
CIBSEVEN_ADMIN_PASSWORD=replace-with-a-strong-admin-password
CIBSEVEN_JWT_SECRET=replace-with-a-long-random-base64-secret
PUBLIC_URL=http://localhost:8080
```

Generate the JWT secret, then start the services:

```shell
openssl rand -base64 64
docker compose up -d
```

For a public deployment, set `PUBLIC_URL` to the externally reachable HTTPS URL. If the GHCR package is private, authenticate first with `docker login ghcr.io`.

## Creating a release

Releases are built automatically by the GitHub Actions workflow when a new tag whose name starts with `v` is created. Use a semantic version tag such as `v1.2.0`.

1. Open the repository on GitHub and select **Releases** on the repository page.
2. Select **Draft a new release**.
3. Select **Choose a tag**, enter the new version such as `v1.2.0`, and select **Create new tag**.
4. Set the target branch or commit that should be released, normally `master`.
5. Enter a release title such as `v1.2.0`. Release notes may be entered manually or created with **Generate release notes**.
6. Select **Publish release**. Creating the `v1.2.0` tag starts the `Release` GitHub Actions workflow.
7. Open the repository's **Actions** tab and follow the workflow run. It builds and tests the application and publishes these images to the GitHub Container Registry:

   ```text
   ghcr.io/digibp/digibp-classroom:v1.2.0
   ghcr.io/digibp/digibp-classroom:latest
   ```

The workflow also updates the GitHub Release with generated release notes. Use a new version number for each release. Consumers should pin the versioned image tag instead of `latest` when reproducible deployments are required.

## Roles

| Process Role | Group | User | Tasklist | Cockpit | Admin | Modeler | Dashboard | Reports | Name |
|---|---|---|---|---|---|---|---|---|---|
| Owner | owner | giulia | - | READ | - | - | READ | ALL | Giulia Ricci |
| Manager | manager | martina | READ, START | ALL | - | - | ALL | ALL | Martina Russo |
| Analyst | analyst | sofia | - | READ | - | - | READ | ALL | Sofia Conti |
| Engineer | engineer | chiara | ALL | ALL | ALL (own tenant) | ALL | ALL | ALL | Chiara Lombardi |
| Participant | initiator, assistant | beppe | READ, START | - | - | - | - | - | Beppe Ferrari |
| Participant | worker, chef | matteo | READ | - | - | - | - | - | Matteo Alfonsi |
| Participant | worker, courier | silvio | READ | - | - | - | - | - | Silvio Esposito |

### Exemplary JSON

```json
{
  "users": [
    {
      "firstName": "Giulia",
      "groupIds": [
        {
          "groupId": "owner"
        }
      ],
      "lastName": "Ricci",
      "password": "password"
    },
    {
      "firstName": "Martina",
      "groupIds": [
        {
          "groupId": "manager"
        }
      ],
      "lastName": "Russo",
      "password": "password"
    },
    {
      "firstName": "Sofia",
      "groupIds": [
        {
          "groupId": "analyst"
        }
      ],
      "lastName": "Conti",
      "password": "password"
    },
    {
      "firstName": "Chiara",
      "groupIds": [
        {
          "groupId": "engineer"
        }
      ],
      "lastName": "Lombardi",
      "password": "password"
    },
    {
      "firstName": "Beppe",
      "groupIds": [
        {
          "groupId": "initiator"
        },
        {
          "groupId": "assistant"
        }
      ],
      "lastName": "Ferrari",
      "password": "password"
    },
    {
      "firstName": "Matteo",
      "groupIds": [
        {
          "groupId": "worker"
        },
        {
          "groupId": "chef"
        }
      ],
      "lastName": "Alfonsi",
      "password": "password"
    },
    {
      "firstName": "Silvio",
      "groupIds": [
        {
          "groupId": "worker"
        },
        {
          "groupId": "courier"
        }
      ],
      "lastName": "Esposito",
      "password": "password"
    }
  ]
}
```

### Exemplary CSV

```csv
newtenant,Fraenzi,Meier,password,engineer
newtenant,Hans,Mueller,password,initiator,worker
othertenant,Susi,Schmid,password,engineer
othertenant,Luki,Bolliger,password,initiator
```

## Maintainer

- [Andreas Martin](https://mrtn.onl)

## License

- [Apache License, Version 2.0](LICENSE)
