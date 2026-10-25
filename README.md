# en-monitor-measurement-service

EnergyMonitor measurement microservice: stores the readings the ESP32 + PZEM-004T meters publish,
and serves the consumption history, statistics and levels of a device and of a home. Spring Boot 4,
Java 21, hexagonal architecture.

It is being extracted from the `measurement` module of `energy-monitor-backend`, following the same
steps as the security and device services. Until the extraction ends, energy-monitor-backend keeps
storing the readings and answering these endpoints; the gateway does not route to this service yet.

## What it serves

| Path | What it does |
|---|---|
| `GET /api/v1/measurements?deviceId=&from=&to=` | Readings of a device, oldest first |
| `GET /api/v1/measurements/latest?deviceId=` | Latest reading of a device |
| `GET /api/v1/measurements/statistics?deviceId=&from=&to=` | Count, average and maximum power and energy of a device |
| `GET /api/v1/homes/{homeId}/consumption/summary?zone=` | Current power, today and this month against the limits of a home |
| `GET /api/v1/homes/{homeId}/consumption/history?period=&zone=` | Energy of a home by day, week, month or year |
| `GET /api/v1/consumption-levels` | Consumption level catalogue |

Every path needs the access token the security service issues; this service validates it with the
service's public key and issuer and takes the caller from `sub`. Only members of the home a device
is linked to may read it: a device of another home answers 404, the same as one that does not
exist.

## Who it asks

| Service | Call | For |
|---|---|---|
| energy-monitor-backend | `GET /internal/homes/{homeId}/access?userId=` | Is the caller a member of the home? |
| energy-monitor-backend | `GET /internal/homes/{homeId}/thresholds` | Daily and monthly limits of the summary (404 = none) |
| device service | `GET /internal/devices/{deviceId}` | The home a device is linked to |
| device service | `GET /internal/homes/{homeId}/devices` | The devices of a home |

- Every call sends `INTERNAL_API_TOKEN` in `X-Internal-Token`; the other side also checks this
  service's network.
- Membership is never cached, so a member removed from a home loses access at once. Devices are
  reused for 30 s, and the last answer is served for up to an hour while the device service does
  not answer.
- No answer and nothing kept: the request gets 503.

## Events it consumes (RabbitMQ)

| Queue | Exchange and routing keys | What it does |
|---|---|---|
| `measurement-service.device.events` | `energy-monitor.device`, `device.linked` and `device.unlinked` | Drops the cached device and the cached devices of its home, so a link or an unlink shows at once |

- The queue is declared here at startup, durable, with a `.dlq` dead-letter queue on
  `measurement-service.dlx`: a message that fails three attempts (1, 2 and 4 s apart) is moved
  there instead of blocking the queue. A message without a `deviceId`, or of another type, is
  dropped.
- A message may arrive twice: forgetting twice only costs one more request.
- Without a broker the cache still expires after 30 s, and the health is DOWN.
- The test profile does not start the consumers. `DeviceLinksBrokerTest` runs against the RabbitMQ
  of infra when it is reachable (export `RABBITMQ_USERNAME` and `RABBITMQ_PASSWORD`) and is
  skipped otherwise.

## Behind the gateway

From loopback or a Docker network (`172.16.0.0/12`) the container takes the caller's address and
scheme from `X-Forwarded-For` / `X-Forwarded-Proto` (`server.forward-headers-strategy=native`,
`server.tomcat.remoteip.internal-proxies`); any other private network is not trusted.

## Requirements

- Java 21 (the repository brings `./mvnw`, no Maven install needed).
- MySQL and RabbitMQ from the `infra` repository: the schema is shared with
  `energy-monitor-backend`, the security service and the device service.
- The public key of the security service (`SECURITY_JWT_PUBLIC_KEY_PATH`).
- energy-monitor-backend and the device service running, for homes and devices.

## Run locally

```bash
cp .env.example .env
cp src/main/resources/application-local.yaml.example src/main/resources/application-local.yaml
# set the MYSQL_PASSWORD of infra/.env, the public key path, INTERNAL_API_TOKEN and RABBITMQ_*
./mvnw spring-boot:run          # http://localhost:8085/actuator/health
```

## Tests

```bash
SPRING_DATASOURCE_PASSWORD=... RABBITMQ_USERNAME=... RABBITMQ_PASSWORD=... ./mvnw test
```

The tests run under the `test` profile against the MySQL of `infra`, with a key pair generated
for the run. They never reach a running backend or device service.

## Docker

```bash
docker build -t energy-monitor-measurement .
```

Multi-stage build (Maven on JDK 21, then JRE 21), running as uid 10001, port 8085.

## Database

This service owns `measurement` and `consumption_level`. The changelogs `measurement-001` to
`measurement-003` keep the path they had in energy-monitor-backend
(`db/changelog/measurement/changes/...`), so on a database the monolith already migrated Liquibase
recognises them as applied and never runs them twice. `device_id` refers to a device by identifier
only: no foreign key across services.
