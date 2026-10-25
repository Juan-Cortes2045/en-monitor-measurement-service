# en-monitor-measurement-service

EnergyMonitor measurement microservice: stores the readings the ESP32 + PZEM-004T meters publish,
and serves the consumption history, statistics and levels of a device and of a home. Spring Boot 4,
Java 21, hexagonal architecture.

It is being extracted from the `measurement` module of `energy-monitor-backend`, following the same
steps as the security and device services. Until the extraction ends, energy-monitor-backend keeps
storing the readings and answering these endpoints: the gateway does not route to this service and
its MQTT subscription is off.

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

## Readings (MQTT)

This service subscribes to `energy-monitor/devices/+/telemetry` with its own MQTT user
(`MQTT_MEASUREMENT_SERVICE_USER`, which the broker lets only read the telemetry) and client id
(`${MQTT_CLIENT_ID}-telemetry`), and stores every reading with the device timestamp, so samples
replayed from a module's offline queue land at the time they were taken.

- The topic, not the payload, identifies the device. A message on an unexpected topic, that claims
  another device or that does not parse is logged and dropped.
- A reading is stored only for a device the device service knows (cached 30 s); while the service
  does not answer and nothing was kept, the reading is dropped.
- **Off by default** (`MEASUREMENT_MQTT_ENABLED=false`): while energy-monitor-backend still stores
  the readings, both subscribed would store each one twice in the shared table. It is turned on
  when the backend stops subscribing. The test profile keeps it off.

## Events it publishes (RabbitMQ)

Every stored reading is announced on the topic exchange `energy-monitor.measurement`, through a
transactional outbox (`measurement_outbox`, changelog `measurement-004`):

| Routing key | Payload | Raised when |
|---|---|---|
| `measurement.recorded` | `{idMeasurement, deviceId, dateTime, voltage, current, activePower, storedEnergy}` | A reading is stored |

- The message is written in the transaction of the reading (`TransactionalRegisterMeasurement`):
  one never commits without the other.
- A relay publishes the pending messages every second, oldest first, up to 50 per run. A message
  counts as published only when the broker confirms it (correlated confirms) and a queue took it;
  until energy-monitor-backend declares its queue they stay pending. The first failure stops the
  run, so the order holds. Delivery is at least once, with the message id.
- One message per reading: published and discarded messages are purged after 7 days
  (`messaging.retention`), every hour. Pending ones are never purged.
- Settings: `RABBITMQ_*` and `messaging.*`. The test profile turns the relay and the purge off and
  stores the messages already discarded. `RabbitMeasurementOutboxPublisherBrokerTest` runs against
  the RabbitMQ of infra when it is reachable and is skipped otherwise.

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
- The test profile does not start the consumers. `DeviceEventsBrokerTest` runs against the RabbitMQ
  of infra when it is reachable (export `RABBITMQ_USERNAME` and `RABBITMQ_PASSWORD`), with
  exchanges and a queue of its own, and is skipped otherwise.

## Behind the gateway

From loopback or a Docker network (`172.16.0.0/12`) the container takes the caller's address and
scheme from `X-Forwarded-For` / `X-Forwarded-Proto` (`server.forward-headers-strategy=native`,
`server.tomcat.remoteip.internal-proxies`); any other private network is not trusted.

## Requirements

- Java 21 (the repository brings `./mvnw`, no Maven install needed).
- MySQL, RabbitMQ and Mosquitto from the `infra` repository: the schema is shared with
  `energy-monitor-backend`, the security service and the device service.
- The public key of the security service (`SECURITY_JWT_PUBLIC_KEY_PATH`).
- energy-monitor-backend and the device service running, for homes and devices.

## Run locally

```bash
cp .env.example .env
cp src/main/resources/application-local.yaml.example src/main/resources/application-local.yaml
# set the MYSQL_PASSWORD of infra/.env, the public key path, INTERNAL_API_TOKEN, RABBITMQ_*
# and the MQTT user
./mvnw spring-boot:run          # http://localhost:8085/actuator/health
```

## Tests

```bash
SPRING_DATASOURCE_PASSWORD=... RABBITMQ_USERNAME=... RABBITMQ_PASSWORD=... ./mvnw test
```

The tests run under the `test` profile against the MySQL of `infra`, with a key pair generated
for the run. They never reach a running backend, device service or MQTT broker.

## Docker

```bash
docker build -t energy-monitor-measurement .
```

Multi-stage build (Maven on JDK 21, then JRE 21), running as uid 10001, port 8085.

## Database

This service owns `measurement`, `consumption_level` and `measurement_outbox`. The changelogs
`measurement-001` to `measurement-003` keep the path they had in energy-monitor-backend
(`db/changelog/measurement/changes/...`), so on a database the monolith already migrated Liquibase
recognises them as applied and never runs them twice. `device_id` refers to a device by identifier
only: no foreign key across services.
