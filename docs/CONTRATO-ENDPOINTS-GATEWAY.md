# Contrato Coletivo de Endpoints — API / Gateway (P3)
**Projeto:** FHIR SUS+ · **Recorte:** Projeto Integrador P3  
**Frente:** API / Gateway · **Responsável:** Bruno Carvalho Santana Rocha (@BrunocsRocha)  
**Consumidores e Partes Interessadas:**
- **Clínica / Integração Externa:** Sistema solicitante do exame (UC-01, RF001)
- **Frente Motor / Arquitetura (Luiz Manoel):** Integração dos payloads de conversão (CT-01, CT-02)
- **Frente Validação / QA / CI (Filipe Ciríaco):** Testes de contrato, validação FHIR e CI (CT-04, CT-05)
- **Frente Interface / UI (Arthur Barreto):** Acompanhamento do fluxo e dados sintéticos
- **Frente Configuração / Dados (Danilo Campos):** Alinhamento com auditoria de transmissão (RF004)

---

## 1. Visão Geral dos Endpoints do Gateway

O Gateway opera como ponto focal de entrada REST síncrono (ADR03) para as solicitações clínicas e disponibiliza rotas operacionais de diagnóstico e saúde.

| Método HTTP | Rota (URI) | Finalidade | Content-Type Aceito | Content-Type Retornado |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/health` | Verificação de saúde e metadados operacionais do Gateway | N/A | `application/json` |
| `GET` | `/actuator/health` | Health check nativo do Spring Actuator | N/A | `application/json` |
| `POST` | `/api/v1/clinical/requests` | Recepção da solicitação clínica para validação, conversão e envio ao laboratório | `application/json`, `application/fhir+json` | `application/json`, `application/fhir+json` |

---

## 2. Especificação Detalhada dos Endpoints

### 2.1 Rota de Saúde: `GET /api/v1/health`

* **Objetivo:** Permitir que clientes, proxies e sistemas de monitoramento verifiquem se a instância da API está ativa e operando no recorte P3.
* **Autenticação:** Aberta (no recorte P3).
* **Parâmetros:** Nenhum.
* **Resposta de Sucesso (HTTP 200 OK):**
```json
{
  "status": "UP",
  "timestamp": "2026-11-22T15:15:00.000Z",
  "gateway": "FHIR-SUS+",
  "versao": "1.0.0-P3",
  "detalhes": {
    "recorte": "P3-Demonstracao",
    "fluxo": "ServiceRequest -> Legado -> Lab -> DiagnosticReport/Observation",
    "padrao": "HL7 FHIR R4"
  }
}
```

---

### 2.2 Rota Clínica Principal: `POST /api/v1/clinical/requests`

* **Objetivo:** Ponto de entrada do fluxo síncrono de solicitação de exames laboratoriais (UC-01, SD01).
* **Headers Aceitos:**
  * `Content-Type: application/json` ou `application/fhir+json`
  * `Accept: application/json` ou `application/fhir+json`

#### Formato do Payload de Entrada (Acordo CT-01 / Luiz Manoel)
O Gateway aceita tanto o **Bundle `collection`** contendo o `ServiceRequest` e o `Patient` (modelo oficial de integração do P3), quanto um `ServiceRequest` isolado:

```json
{
  "resourceType": "Bundle",
  "type": "collection",
  "entry": [
    {
      "fullUrl": "urn:uuid:d24c3ced-2413-4dbd-9cd1-f389253e441e",
      "resource": {
        "resourceType": "ServiceRequest",
        "identifier": [
          {
            "system": "https://clinica.example/identificadores/pedidos",
            "value": "PED-001"
          }
        ],
        "status": "active",
        "intent": "order",
        "code": {
          "coding": [
            {
              "system": "https://clinica.example/codigos/exames",
              "code": "EXAME-001"
            }
          ]
        },
        "subject": {
          "reference": "urn:uuid:54814e57-6be4-4469-a052-7ce6aef6a1f2"
        }
      }
    },
    {
      "fullUrl": "urn:uuid:54814e57-6be4-4469-a052-7ce6aef6a1f2",
      "resource": {
        "resourceType": "Patient",
        "identifier": [
          {
            "system": "https://clinica.example/identificadores/pacientes",
            "value": "PAC-001"
          }
        ]
      }
    }
  ]
}
```

---

## 3. Matriz de Respostas HTTP e Tratamento de Erros (CT-04 / ADR03)

Todas as respostas da API Gateway seguem estritamente os códigos HTTP acordados no **CT-04**. Em caso de qualquer falha ou rejeição clínica, o corpo retornado é obrigatoriamente um recurso HL7 FHIR **`OperationOutcome`**:

| Código HTTP | Cenário Clínico / Causa | Formato do Corpo |
| :---: | :--- | :--- |
| **`200 OK`** | Fluxo completo processado com sucesso. Retorna o laudo laboratorial estruturado. | `Bundle` FHIR R4 (`type: collection` contendo `DiagnosticReport` e `Observation`) |
| **`400 Bad Request`** | JSON sintaticamente quebrado, malformado ou payload vazio. | `OperationOutcome` FHIR R4 |
| **`422 Unprocessable Entity`** | Recurso semanticamente inválido, tipo não suportado, campos obrigatórios ausentes (`identifier`, `code`) ou falha nas regras de mapeamento. | `OperationOutcome` FHIR R4 |
| **`502 Bad Gateway`** | Falha remota no laboratório (ex.: 500 do mock) ou laboratório indisponível/fora do ar. | `OperationOutcome` FHIR R4 |
| **`504 Gateway Timeout`** | O laboratório não respondeu dentro do tempo limite configurado (`lab.client.timeout-ms`). | `OperationOutcome` FHIR R4 |
| **`500 Internal Server Error`** | Bundle de saída gerado inválido ou falha grave na infraestrutura. | `OperationOutcome` FHIR R4 |

---

## 4. Esquema Padrão de Erro (`OperationOutcome`)

Conforme padronizado na ADR03 e no CT-04, qualquer falha resulta na seguinte estrutura:

```json
{
  "resourceType": "OperationOutcome",
  "issue": [
    {
      "severity": "error",
      "code": "invalid",
      "diagnostics": "Descrição clara e objetiva da causa do erro para o cliente",
      "expression": [
        "ServiceRequest.code"
      ]
    }
  ]
}
```

* `severity`: `"error"` ou `"fatal"`
* `code`: Código FHIR padrão (`"structure"`, `"required"`, `"invalid"`, `"timeout"`, etc.)
* `diagnostics`: Mensagem explicativa
* `expression`: Caminho do campo ou recurso que originou o erro quando aplicável

---

## 5. Relação com as Outras Frentes

1. **Frente Motor (Luiz Manoel):**
   * O Gateway desmembra o Bundle de entrada e chama `motor.converter(serviceRequestNode, "ServiceRequest", SentidoConversao.TO_LEGADO, config)`.
   * Na volta, o Gateway chama `motor.converter(itemLegado, "Observation", SentidoConversao.TO_FHIR, config)` e monta o Bundle `collection` de saída.
2. **Frente QA (Filipe Ciríaco):**
   * A suíte de testes do CI deve validar as asserções de cabeçalhos e códigos 200, 400, 422, 502 e 504 contra essa especificação.
3. **Frente Configuração (Danilo Campos):**
   * Os endpoints de configuração (salvar/carregar regras De-Para) pertencem ao contrato CT-04 sob responsabilidade de Danilo/Arthur e rodam em rotas separadas (ex.: `/api/v1/configuracoes`), não interferindo no tráfego clínico do Gateway.
