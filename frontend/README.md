# Front-end FHIR SUS+ (Incremento P3)

Módulo 04: Front-end e Interface de Mapeamento De-Para (`FHIR R4 <-> JSON Legado`).

## Visão Geral
Este módulo implementa a interface do usuário para mapeamento bidirecional de dados laboratoriais, edição, validação estrutural em tempo de execução via Zod, teste de conversão e importação/exportação de configurações (UC-02, UC-04, RF002, RF006, RF009, RF011, RF012, RF013).

- **Ambiente:** Local com dados sintéticos (`sistema-demo`).
- **Autenticação:** Sem autenticação (`DT01` adiada para P4).
- **Regras do Motor:** `COPIAR` e `TABELA` em caminhos simples (`DT05`).
- **Recursos FHIR R4:** 8 recursos oficiais (`ServiceRequest`, `DiagnosticReport`, `Observation`, `Patient`, etc.).

## Requisitos
- Node.js 24 LTS (referência da CI, arquivo `.nvmrc`; compatível com `>=20.19.0 <25.0.0`, pois Node 25+ exige `--localstorage-file` para o Web Storage nos testes)
- npm 10+

## Execução Local

```bash
# Entrar na pasta do front-end
cd frontend

# Instalar dependências com lockfile
npm ci

# Iniciar servidor de desenvolvimento (porta 5173; usa API HTTP por padrão conforme .env, ou VITE_USE_MOCK_API=true para mock)
npm run dev

# Checagem estrita de tipos TypeScript (tsc -b)
npm run typecheck

# Análise estática de código (ESLint)
npm run lint

# Execução da suíte de testes sem watch (Vitest)
npm run test:ci

# Build de produção
npm run build

# Verificação completa em sequência (typecheck + lint + test:ci + build)
npm run check
```

## Arquitetura de Comunicação e Mock Fail-Closed
- `VITE_USE_MOCK_API=true`: Opera com o mock local resiliente `mockMappingApi.ts` persistido no `localStorage`.
- `VITE_USE_MOCK_API=false`: Conecta ao servidor Spring Boot via proxy `/api` conforme contrato `CT-04` em `httpMappingApi.ts`.
