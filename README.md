# coco-mock – MWS Mock SOAP Service

Spring Boot mock of the `MWSProcessServiceBasic` SOAP service (MWS / CoCo).  
Allows integration adapters to be tested locally without connecting to a real CoCo system.

Supports two adapter variants:

| Adapter | What is mocked |
|---|---|
| **druckAdapter** | MWS SOAP service (Process_Create, Process_Start, Process_GetInfo, etc.) |
| **modBatchAdapter** | CoCo database (`AUFTRAGSEINGANG` table / ModusOne) via H2 TCP |

---

## Requirements

- Java 21+
- Maven 3.8+

---

## Running

```bash
mvn clean package -DskipTests
java -jar target/coco-mock-1.0-SNAPSHOT.jar
```

or directly via Maven:

```bash
mvn spring-boot:run
```

The mock starts on port **8011**.

---

## druckAdapter – SOAP mock

### WSDL endpoint

```
http://localhost:8011/ws/mwsbasic.wsdl
```

### Adapter configuration

Point the adapter's configuration to the mock as its MWS server:

```
wsdlLocation = http://localhost:8011/ws/mwsbasic.wsdl
```

### Login credentials

Defaults defined in `default-data.properties`:

```properties
coco.username=CAPPDC1
coco.password=Gemini12
```

### Transient error simulation (FKAT_RESOURCENFEHLER_MWS)

The mock can simulate a transient resource error on `Process_GetInfo` to exercise
the adapter's retry logic.  
See `MockStateStore.triggerTransientError(processId)` for details.

---

## modBatchAdapter – CoCo database mock (H2)

The batch adapter (`MessageWorker`) does not call MWS SOAP at all – it inserts
processed jobs directly into the CoCo database (`AUFTRAGSEINGANG` table).  
The mock starts an embedded H2 database containing that table and exposes it over TCP.

### Adapter configuration (`adapter.ini`)

Replace the ModusOne OUT connection entries for each fachmodul (10, 20, 30, 40) with:

```properties
messagedb.driver.classname.out.ModusOne.10=org.h2.Driver
messagedb.username.out.ModusOne.10=COCO_OUT
messagedb.password.out.ModusOne.10=
messagedb.url.out.ModusOne.10=jdbc:h2:tcp://localhost:9092/mem:cocodb;SCHEMA=COCO_OUT
```

Repeat for fachmodul `20`, `30`, `40` accordingly.

> **Note:** The adapter uses `MessageObjectJdbcDAOOracle` which writes CLOBs via
> `oracle.sql.CLOB`. Since H2 is not Oracle, the OUT connection must use the base
> `MessageObjectJdbcDAO` class instead. The driver class above (`org.h2.Driver`) is
> sufficient to trigger this ? as long as `OracleDataSource` is not instantiated for
> the OUT connection, the Oracle-specific CLOB path is not reached.
>
> Also ensure `workerthread.password.encrytion=false` when using plain-text passwords
> with H2 (or leave the password empty as shown above).

### REST API for result verification

After running the adapter you can inspect what arrived in the mock:

| Method | URL | Description |
|---|---|---|
| `GET` | `/batch/auftraege` | List all rows in `AUFTRAGSEINGANG` |
| `GET` | `/batch/auftraege/{auftragsid}` | Single row including `XMLDOKUMENT` |
| `DELETE` | `/batch/auftraege` | Reset the table between test runs |
| `GET` | `/batch/status` | Row count summary |

Examples:

```bash
# Check whether a job was transferred to CoCo
curl http://localhost:8011/batch/auftraege/TEST-001

# Reset before the next test run
curl -X DELETE http://localhost:8011/batch/auftraege
```

### H2 Console (browser)

```
http://localhost:8011/h2-console
```

| Field | Value |
|---|---|
| JDBC URL | `jdbc:h2:mem:cocodb` |
| User Name | `COCO_OUT` |
| Password | *(empty)* |

---

## Project structure

```
src/main/
├── com/capgemini/futura/mws/
│   ├── config/
│   │   ├── WebServiceConfig.java            # Spring-WS configuration
│   │   └── BatchDbConfig.java               # H2 datasource + TCP server (CoCo DB mock)
│   ├── endpoint/
│   │   ├── MWSProcessServiceEndpoint.java   # All SOAP operations
│   │   └── BatchTestDataController.java     # REST API for H2 inspection
│   ├── state/
│   │   ├── MockStateStore.java              # In-memory SOAP process state
│   │   └── ProcessState.java
│   └── MwsMockServiceApplication.java
└── resources/
    ├── application.yaml
    ├── default-data.properties              # Login credentials (username/password)
    ├── mwsbasic.wsdl
    ├── db/
    │   └── coco-out-schema.sql              # DDL for AUFTRAGSEINGANG (H2)
    └── mock-responses/
        └── login-success.xml
```
