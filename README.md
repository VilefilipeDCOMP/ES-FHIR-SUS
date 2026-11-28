# ES-FHIR-SUS

Plataforma de interoperabilidade clínica em **HL7 FHIR R4** composta por dois módulos:

- `fhirsus/`: backend Java 17 / Spring Boot 4.0.10 (API Gateway clínico, motor de conversão, validação HAPI FHIR R4, persistência de configurações e mock de laboratório).
- `frontend/`: interface React 19 + TypeScript + Vite para edição, validação e importação/exportação de regras de mapeamento.

## Versões de referência (alinhadas à CI)

- **Java:** 17 (OpenJDK / Temurin)
- **Node.js:** 24 LTS (compatível com `>=20.19.0 <25.0.0`; Node 25+ falha no Vitest sem `--localstorage-file`)
- **Maven:** 3.10+ (ou wrapper `fhirsus/mvnw`)

## Execução e verificação local

### Backend (`fhirsus/`)

```bash
cd fhirsus
mvn --batch-mode --no-transfer-progress verify
```

### Frontend (`frontend/`)

```bash
cd frontend
npm ci
npm run check
```

O comando `npm run check` executa em sequência sem watch mode: `typecheck`, `lint`, `test:ci` e `build`.

## Integração contínua e fluxo de branches

- **Fluxo de branches (`.github/workflows/validate-branch-flow.yml`):** aceita PRs `feature/* -> dev` e `dev -> main` (mesmo repositório).
- **CI (`.github/workflows/ci.yml`):** executa em `push` e `pull_request` para `dev` e `main`:
  1. `Estrutura do repositório`: falha se módulos obrigatórios (`fhirsus/pom.xml`, `frontend/package.json`, `frontend/package-lock.json` versionado) ou scripts obrigatórios (`typecheck`, `lint`, `test:ci`/`test` sem watch, `build`) estiverem ausentes.
  2. `Backend (fhirsus)`: executa `mvn --batch-mode --no-transfer-progress verify` em Java 17 e publica resumo Surefire no Job Summary.
  3. `Frontend`: executa `npm ci`, `typecheck`, `lint`, `test:ci` (com relatório JUnit sem watch) e `build` em Node 24, publicando resumo no Job Summary.
