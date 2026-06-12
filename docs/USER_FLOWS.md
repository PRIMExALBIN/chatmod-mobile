# User Flows

ChatMod Mobile uses a dashboard-first onboarding model. The first screen stays useful: creators see live status, setup progress, and direct actions instead of a marketing intro.

## New Creator Setup

```mermaid
flowchart TD
    A["Open ChatMod Mobile"] --> B["Read phone-hosted bot intro"]
    B --> C["Review YouTube permission explanation"]
    C --> D["Connect Google / YouTube bot channel"]
    D --> E["Use connected channel in stream selector"]
    E --> F["Confirm bot channel name"]
    F --> G["Add bot channel as YouTube moderator"]
    G --> H["Find active or scheduled livestream"]
    H --> I["Send test message"]
    I --> J["Check moderator delete action"]
    J --> K["Pick or save first rule preset"]
    K --> L["Create first command and timer"]
    L --> M["Enter live dashboard ready state"]
```

## Going Live

```mermaid
flowchart TD
    A["Creator opens dashboard"] --> B["Refresh stream detection"]
    B --> C{"Live chat found?"}
    C -- "No" --> D["Show scheduled/no-chat guidance"]
    D --> B
    C -- "Yes" --> E["Start foreground service"]
    E --> F["Poll YouTube Live chat"]
    F --> G["Apply local moderation rules"]
    G --> H["Send commands, timers, and moderation actions"]
    H --> I["Write local Room logs"]
    I --> J["Sync audit records to backend when online"]
```

## High-Pressure Moderation

```mermaid
flowchart TD
    A["Queue item appears"] --> B["Creator opens user/message context"]
    B --> C{"Action needed?"}
    C -- "Ignore" --> D["Resolve queue item"]
    C -- "Warn" --> E["Send warning and save strike history"]
    C -- "Delete" --> F["Delete YouTube message"]
    C -- "Timeout/Hide" --> G["Apply channel-level moderation action"]
    B --> H["Emergency mode or link lockdown"]
    H --> I["Tighten rules and pause timers"]
    D --> J["Save audit log"]
    E --> J
    F --> J
    G --> J
    I --> J
```

## After Stream

```mermaid
flowchart TD
    A["Stop foreground service"] --> B["Review session summary"]
    B --> C["Inspect moderation log"]
    C --> D["Mark false positives"]
    D --> E["One-tap tune affected rule preset"]
    E --> F["Export local or cloud log"]
    F --> G["Save preset for next stream"]
```

## Backend Ownership Boundaries

```mermaid
flowchart LR
    A["Android phone bot runtime"] --> B["Local Room/DataStore"]
    A --> C["YouTube Data API"]
    B --> D["Pending sync queue"]
    D --> E["Fastify backend"]
    E --> F["Postgres account data"]
    E --> G["Entitlement and support diagnostics"]
    E --> H["Account-scoped audit exports"]
```
