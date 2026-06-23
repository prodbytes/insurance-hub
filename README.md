# InsuranceHub

A demonstration of **business decision modeling** and **accountable AI** in a modern,
full-stack Java application.

InsuranceHub is not a real insurance product. It is a reference application that shows how
the rules driving eligibility, pricing, and risk can be modeled as **explicit, governable
business decisions** instead of being scattered through application code.

## What it demonstrates

When a customer requests a car-insurance quote, the application collects driver, vehicle, and
coverage details and asks a **decision model** — not hand-written Java — to compute:

- an **Estimated Vehicle Value**, and
- a **Risk Rate**, derived from a *Risk Index* to which mileage, driver age, accidents, and
  tickets each contribute independently.

The yearly premium is then simply *estimated value × risk rate*.

### Why this is "accountable AI"

The decision logic lives in a **DMN model** ([ih-models/car/car-quote.dmn](ih-models/car/car-quote.dmn)) —
a standardized, human-readable set of decision tables — that is authored, versioned, and
governed independently of the application:

- **Transparent** — every rule is an explicit row in a decision table, not weights buried in
  code or a black-box model. Anyone can read *why* a given quote came out the way it did.
- **Governable** — models are uploaded, versioned, and enabled through Decision Control's
  management API, so changes are deliberate and auditable rather than code edits.
- **Separable** — business analysts own the rules; engineers own the application. The app
  calls the model over a REST runtime endpoint and never hard-codes its logic or even its
  generated model id (see [QuoteService.java](ih-vdn/src/main/java/ih/service/QuoteService.java)).

This is the pattern for putting automated decisioning into production *accountably*: the
decision is an asset you can inspect, test, version, and explain.

## Architecture

```
Browser ──▶ ih-vdn (Vaadin UI + Quarkus backend) ──▶ Aletyx Decision Control (KIE runtime)
                         │                                        │
                         └────────────▶ PostgreSQL ◀─────────────┘
```

| Component | Role |
|-----------|------|
| **ih-vdn** | The application: a [Vaadin](https://vaadin.com) Flow UI on a [Quarkus](https://quarkus.io) backend. Collects the quote request, calls the decision model, persists the request/response, shows the result. |
| **ih-models** | The decision models. `car/car-quote.dmn` is the DMN model evaluated for each quote. |
| **Aletyx Decision Control** | The decision engine that hosts and executes the DMN model. It is based on [KIE Server](https://www.kie.org) / [Kogito](https://kogito.kie.org) and exposes the same KIE-compatible REST runtime interface. |
| **PostgreSQL** | Shared datastore. ih-vdn owns its tables via Flyway migrations; Decision Control creates its own. |

### Technology

- **[Quarkus](https://quarkus.io)** — supersonic, subatomic Java; the application runtime.
- **[Vaadin Flow](https://vaadin.com)** — full-stack, type-safe Java UI (no separate frontend stack to maintain).
- **[KIE](https://www.kie.org) / [Kogito](https://kogito.kie.org) / [Drools](https://www.drools.org)** — the decision-automation
  platform. **Aletyx Decision Control** is built on KIE Server and uses the same REST interface
  and integration mechanisms, so the DMN model is served over a standard KIE runtime endpoint.
- **[DMN](https://www.omg.org/dmn/)** — the OMG Decision Model and Notation standard the rules are authored in.
- **PostgreSQL**, **Hibernate ORM (Panache)**, and **Flyway** for persistence.
- **[Devbox](https://www.jetify.com/devbox)** — provides the GraalVM/Quarkus toolchain.
- **[process-compose](https://github.com/F1bonacc1/process-compose)** — orchestrates the local processes (database, Decision Control, app).

## Running on GitHub Codespaces

The repository is configured to run end-to-end in Codespaces with no local setup. The dev
container provides the toolchain (Devbox), runs Docker-in-Docker for the database and Decision
Control containers, and starts every service automatically on boot.

### 1. Set the Codespaces secrets

This demo is built upon Aletyx Decision Control. If you don't have Aletyx credentials yet, request your free trial at https://aletyx.ai/free-trial/

Decision Control is pulled from a private Aletyx registry, so before creating the Codespace you
must provide credentials as **Codespaces secrets** (GitHub → *Settings* → *Codespaces* →
*Secrets*), granting each one access to this repository:

| Secret | Purpose |
|--------|---------|
| `ALETYX_REGISTRY` | Private registry host (e.g. `registry-innovator.aletyx.services`) |
| `ALETYX_USERNAME` | Registry username |
| `ALETYX_PASSWORD` | Registry password / token |
| `DECISION_CONTROL_IMAGE` | Decision Control image, e.g. `registry-innovator.aletyx.services/decision-control-innovator:1.4.0` |

> These are injected straight into the container environment by the platform — they must **not**
> be declared in `devcontainer.json`. If they are missing or not granted to this repo, the app
> still starts but the Decision Control service stays red (the boot log explains how to fix it).

### 2. Launch the Codespace

Create a Codespace on this repository (*Code* → *Codespaces* → *Create codespace*). On boot the
container will:

- log in to the Aletyx registry with the secrets above,
- start PostgreSQL, Decision Control, and ih-vdn via process-compose,
- upload the DMN model(s) to Decision Control and wire the runtime URL into the app,
- make ports **8080** (the app) and **8081** (Decision Control) public.

When the services are healthy, open the forwarded URL for port **8080** (the **Ports** panel
lists both, or the boot log prints the full URLs).

### 3. Attach to the running processes

The services start in the background. To watch logs, restart a process, or check status, attach
to the process-compose TUI:

```bash
devbox services attach
```

You can also see a periodic health summary (registry auth, database, Decision Control, app) in
the `0-health` process.

## Running locally

You need **Docker** (for the PostgreSQL and Decision Control containers) and
**[Devbox](https://www.jetify.com/devbox/docs/installing_devbox/)** (which provides the
GraalVM/Quarkus toolchain and process-compose). Everything else is fetched by Devbox.

### 1. Set the environment variables

Copy the example env file and fill in the Aletyx registry credentials:

```bash
cp .env.example .env
```

`.env` is gitignored and is sourced automatically by the start scripts. It carries two groups
of variables:

- **Datasource** — `QUARKUS_DATASOURCE_*` and `DC_DATASOURCE_*` plus `DECISION_CONTROL_IMAGE`.
  The committed defaults work as-is for local development.
- **Private registry** — add the same credentials used in Codespaces so Decision Control can be
  pulled:

  ```bash
  ALETYX_REGISTRY=registry-innovator.aletyx.services
  ALETYX_USERNAME=your-username
  ALETYX_PASSWORD=your-password
  ```

  (Locally there are no Codespaces secrets, so these live in `.env`. A value already present in
  your shell environment always takes precedence over `.env`.)

### 2. Start everything

```bash
devbox services up        # or: scripts/process-start.sh
```

This logs in to the registry, brings up PostgreSQL and Decision Control, uploads the decision
model, and runs ih-vdn in Quarkus dev mode. Then open:

- **App:** http://localhost:8080
- **Decision Control:** http://localhost:8081
- **Swagger UI:** http://localhost:8080/q/swagger-ui

To stop the stack, quit process-compose (`Ctrl-C`, or `q` in the TUI); the throwaway containers
are stopped on shutdown.

### Running just the app

If the database and Decision Control are already up, you can run the Quarkus app directly:

```bash
cd ih-vdn
./scripts/dev-start.sh    # uploads models, then runs ./mvnw quarkus:dev
```

## Working with the decision model

The model lives at [ih-models/car/car-quote.dmn](ih-models/car/car-quote.dmn). Each direct
subdirectory of `ih-models/` is a "unit" that is zipped and uploaded to Decision Control by
[scripts/models-upload.sh](scripts/models-upload.sh) on startup.

> **Note:** the upload script *skips units that already exist* in Decision Control. After
> editing a model, deploy the change by uploading a new version manually (e.g. through the
> Decision Control UI at :8081) — re-running the startup upload will not replace an existing
> unit.

## Repository layout

```
insurance-hub/
├── ih-vdn/              # Vaadin + Quarkus application
│   └── src/main/java/ih/
│       ├── domain/      # QuoteRequest / QuoteResponse entities, CarMaker
│       ├── service/     # QuoteService — calls the DMN model, derives the premium
│       ├── rs/          # REST resources
│       └── vdn/         # Vaadin views (Quote form, result, about, ...)
├── ih-models/           # Decision models
│   └── car/car-quote.dmn
├── scripts/             # health, model upload, process start/build helpers
├── .devcontainer/       # Codespaces / Dev Container setup
├── process-compose.yaml # Local process orchestration
└── .env.example         # Environment variable template
```

## License

See [LICENSE](LICENSE).
