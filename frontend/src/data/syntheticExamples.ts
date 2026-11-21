import type { MapeamentoConfig, RecursoFhir } from '../types/domain';

/** payloads 100% sintéticos — nenhum dado clínico real */
export const EXEMPLO_LEGADO_SINTETICO: Record<string, unknown> = {
  pedido: {
    status_legado: 'ATIVO',
    intencao: 'order',
    paciente_ref: 'Patient/DEMO-0001',
    codigo_exame: 'GLIC-01',
  },
  paciente: {
    id_sintetico: 'DEMO-0001',
    nome_completo: 'PACIENTE SINTETICO EXEMPLO',
    sexo_legado: 'M',
    data_nasc: '1990-01-15',
  },
  laudo: {
    situacao: 'LIBERADO',
    codigo_painel: 'PAINEL-GLIC',
    conclusao: 'Resultado sintetico dentro da faixa de referencia.',
    valor_glicose: 92.4,
    unidade: 'mg/dL',
  },
};

export const EXEMPLOS_FHIR_SINTETICOS: Partial<Record<RecursoFhir, Record<string, unknown>>> = {
  ServiceRequest: {
    resourceType: 'ServiceRequest',
    status: 'active',
    intent: 'order',
    subject: { reference: 'Patient/DEMO-0001', display: 'PACIENTE SINTETICO EXEMPLO' },
    code: { coding: [{ system: 'http://loinc.org', code: '2345-7' }], text: 'Glicose serica' },
  },
  DiagnosticReport: {
    resourceType: 'DiagnosticReport',
    status: 'final',
    code: { coding: [{ system: 'http://loinc.org', code: '24323-8' }], text: 'Painel Glicemia' },
    subject: { reference: 'Patient/DEMO-0001' },
    conclusion: 'Laudo sintetico de demonstracao.',
  },
  Observation: {
    resourceType: 'Observation',
    status: 'final',
    code: { coding: [{ system: 'http://loinc.org', code: '2345-7' }], text: 'Glicose' },
    valueQuantity: { value: 92.4, unit: 'mg/dL' },
  },
  Patient: {
    resourceType: 'Patient',
    identifier: [{ value: 'DEMO-0001' }],
    name: [{ text: 'PACIENTE SINTETICO EXEMPLO' }],
    gender: 'male',
    birthDate: '1990-01-15',
  },
};

export const CONFIG_INICIAL_SINTETICA: MapeamentoConfig = {
  mapeamentos: [
    {
      recurso: 'ServiceRequest',
      sentidos: [
        {
          tipo: 'LEGADO_PARA_FHIR',
          regras: [
            {
              caminhoOrigem: 'pedido.status_legado',
              caminhoDestino: 'ServiceRequest.status',
              regra: 'TABELA:TAB_STATUS_PEDIDO',
              obrigatorio: true,
            },
            {
              caminhoOrigem: 'pedido.intencao',
              caminhoDestino: 'ServiceRequest.intent',
              regra: 'COPIAR',
              obrigatorio: true,
            },
            {
              caminhoOrigem: 'pedido.paciente_ref',
              caminhoDestino: 'ServiceRequest.subject.reference',
              regra: 'COPIAR',
              obrigatorio: true,
            },
            {
              caminhoOrigem: 'pedido.codigo_exame',
              caminhoDestino: 'ServiceRequest.code.coding.code',
              regra: 'COPIAR',
              obrigatorio: true,
            },
          ],
        },
      ],
    },
  ],
  tabelas: [
    {
      nome: 'TAB_STATUS_PEDIDO',
      equivalencia: {
        ATIVO: 'active',
        CONCLUIDO: 'completed',
        CANCELADO: 'revoked',
      },
    },
    {
      nome: 'TAB_SEXO_PACIENTE',
      equivalencia: {
        M: 'male',
        F: 'female',
        O: 'other',
      },
    },
  ],
};
