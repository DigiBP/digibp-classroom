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

Local settings belong in `src/main/resources/application-local.yaml`. The `prod` profile uses a file-based H2 database by default. Its database and runtime configuration can be overridden with environment variables.

### Production configuration

| Environment variable | Required | Description |
|---|---:|---|
| `SPRING_DATASOURCE_URL` | no | JDBC URL; defaults to `jdbc:h2:file:./data/cibseven` |
| `SPRING_DATASOURCE_USERNAME` | no | Database user; defaults to `sa` |
| `SPRING_DATASOURCE_PASSWORD` | no | Database password; empty by default |
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

The published image runs with Java 17 as a non-root user. Without datasource variables, it stores its data in an H2 file database under `/app/data`. Mount a volume to persist the database.

On Apple Silicon Macs, use `--platform linux/amd64` unless a multi-architecture image is available:

```shell
docker run --platform linux/amd64 --name digibp-classroom -p 8080:8080 \
  -v digibp-classroom-data:/app/data \
  -e CIBSEVEN_ADMIN_PASSWORD=secret \
  -e CIBSEVEN_WEBCLIENT_AUTHENTICATION_JWTSECRET="$(openssl rand -base64 128 | tr -d '\n')" \
  -e CIBSEVEN_ENGINE_REST_URL=http://localhost:8080 \
  ghcr.io/digibp/digibp-classroom:latest
```

Replace the example admin password before running the container. The JWT secret must be a Base64-decodable string of at least 155 characters. The command above generates a suitable temporary secret automatically.

### Docker Compose

The released image can connect to an existing remote PostgreSQL database using Docker Compose. Save the following as `compose.yaml`:

```yaml
services:
  classroom:
    image: ghcr.io/digibp/digibp-classroom:${IMAGE_TAG:-latest}
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: ${SPRING_DATASOURCE_URL:?SPRING_DATASOURCE_URL must be set}
      SPRING_DATASOURCE_USERNAME: ${SPRING_DATASOURCE_USERNAME:?SPRING_DATASOURCE_USERNAME must be set}
      SPRING_DATASOURCE_PASSWORD: ${SPRING_DATASOURCE_PASSWORD:?SPRING_DATASOURCE_PASSWORD must be set}
      CIBSEVEN_ADMIN_PASSWORD: ${CIBSEVEN_ADMIN_PASSWORD:?CIBSEVEN_ADMIN_PASSWORD must be set}
      CIBSEVEN_WEBCLIENT_AUTHENTICATION_JWTSECRET: ${CIBSEVEN_JWT_SECRET:?CIBSEVEN_JWT_SECRET must be set}
      CIBSEVEN_ENGINE_REST_URL: ${PUBLIC_URL:-http://localhost:8080}
      CORS_ENABLED: ${CORS_ENABLED:-false}
      CORS_ORIGIN: ${CORS_ORIGIN:-*}
```

Create a `.env` file next to `compose.yaml`. Do not commit this file:

```dotenv
IMAGE_TAG=v1.0.0
SPRING_DATASOURCE_URL=jdbc:postgresql://db.example.org:5432/classroom
SPRING_DATASOURCE_USERNAME=classroom
SPRING_DATASOURCE_PASSWORD=replace-with-the-database-password
CIBSEVEN_ADMIN_PASSWORD=replace-with-a-strong-admin-password
CIBSEVEN_JWT_SECRET=replace-with-a-base64-secret-of-at-least-155-characters
PUBLIC_URL=https://classroom.example.org
```

Generate a suitable JWT secret with:

```shell
openssl rand -base64 128 | tr -d '\n'
```

Copy the generated value into `CIBSEVEN_JWT_SECRET` in `.env`, then start the service:

```shell
docker compose up -d
```

The remote PostgreSQL server must accept connections from the Docker host, and the database must already exist. Set `PUBLIC_URL` to the externally reachable HTTPS URL.
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
