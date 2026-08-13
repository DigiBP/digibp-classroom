# DigiBP Classroom

[![License](http://img.shields.io/:license-apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

Spring Boot application providing a tenant-aware CIB seven classroom environment with Tasklist, Cockpit, Admin and Modeler.

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
| `CIBSEVEN_ADMIN_PASSWORD` | recommended | Password of the initial `demo` administrator; defaults to `demo` |
| `CORS_ENABLED` | no | Enables CORS; defaults to `true` in the `prod` profile |
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
  -e CIBSEVEN_WEBCLIENT_AUTHENTICATION_JWTSECRET=base64-secret \
  digibp-classroom
```

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
