# DigiBP Classroom

[![License](http://img.shields.io/:license-apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

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

- [Apache License, Version 2.0](https://github.com/DigiBP/digibp-archetype-camunda-boot/blob/master/LICENSE)
