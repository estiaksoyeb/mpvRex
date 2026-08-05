# mpvRex Documentation Index

This directory contains technical documentation, architecture specifications, implementation plans, and bug-fix records for **mpvRex**.

## Directory Structure

```text
docs/
├── README.md                          # Documentation index (this file)
├── architecture/                      # Component architecture & data flow docs
│   └── media_states.md                # Media playback state management architecture
├── mini_player/                       # Direct Mini Player subsystem documentation
│   ├── architecture.md                # Mini Player overall architecture & lifecycle
│   ├── implementation_plan.md         # Original direct mini player feature specification & plan
│   └── bugfix_history.md              # Incident history, confirmed fixes & recovery guide
├── plans/                             # Project roadmaps & feature implementation plans
│   ├── ssot_implementation_plan.md    # Single Source of Truth architecture implementation plan
│   └── testing_roadmap.md             # Testing strategy and roadmap
└── reports/                           # Quality & bug audit reports
    └── bugs_opus4.8.md                # Opus 4.8 code audit and bug findings
```

## Section Summary

- **[Mini Player (`docs/mini_player/`)](mini_player/architecture.md)**
  - [Architecture Specification](mini_player/architecture.md): Lifecycle, state management, full-player handoff, native MPV singleton ownership.
  - [Implementation Plan](mini_player/implementation_plan.md): Historical design handoff for headless playback.
  - [Bugfix History & Recovery Guide](mini_player/bugfix_history.md): Technical incident records, native crash prevention, and regression testing checklist.

- **[Architecture (`docs/architecture/`)](architecture/media_states.md)**
  - [Media States Architecture](architecture/media_states.md): State flows, persistence, and UI synchronization.

- **[Plans (`docs/plans/`)](plans/testing_roadmap.md)**
  - [SSOT Implementation Plan](plans/ssot_implementation_plan.md): System-wide single source of truth design.
  - [Testing Roadmap](plans/testing_roadmap.md): Automated test coverage plan.

- **[Reports (`docs/reports/`)](reports/bugs_opus4.8.md)**
  - [Opus 4.8 Bug Audit](reports/bugs_opus4.8.md): Codebase audit findings.
