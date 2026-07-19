# Contributing to HFX Connect

HFX Connect is built in documented milestones. Read [docs/development-workflow.md](docs/development-workflow.md) before starting a substantial change.

## Pull-request workflow

1. Choose a task from the active milestone and confirm its acceptance criteria.
2. Branch from `main` using `milestone/NN-short-name` for milestone work or a focused `fix/` branch for corrections.
3. Use small commits with `feat:`, `fix:`, `test:`, `docs:`, `refactor:`, or `build:` prefixes.
4. Update the relevant task, milestone, architecture decision, API reference, and development log when behavior changes.
5. Run the checks below and open a pull request with the repository template.

## Checks

```bash
cd backend
./mvnw verify

cd ../frontend
npm ci
npm run lint
npm run typecheck
npm test -- --runInBand --watchman=false
npm run build
```

Backend integration tests require a working Docker daemon because they use a real PostgreSQL/PostGIS Testcontainer.

## Scope and documentation

Keep implemented behavior, known limitations, and roadmap items separate. Avoid empty package scaffolding and speculative production claims. Do not include credentials, private user data, or screenshots containing personal information.
