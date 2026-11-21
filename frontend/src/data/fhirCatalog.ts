import type { RecursoFhir } from '../types/domain';
import type { CampoFhirSpec } from '../types/ui';

/** indica se o campo participa de alguma exigência obrigatória (direta ou por grupo FHIR R4) */
export function participaDeObrigatoriedade(campo: CampoFhirSpec): boolean {
  return (
    campo.origemObrigatoriedade === 'FHIR_R4' ||
    campo.origemObrigatoriedade === 'REGRA_PROJETO' ||
    Boolean(campo.grupoObrigatorioFhir)
  );
}

/**
 * catálogo de UX alinhado ao HL7 FHIR R4 v4.0.1:
 * - folhas primitivas 1..1 usam origemObrigatoriedade: 'FHIR_R4'.
 * - elementos complexos 1..1 usam grupoObrigatorioFhir para aceitar qualquer subcampo válido.
 * - exigências específicas do fluxo laboratorial usam origemObrigatoriedade: 'REGRA_PROJETO'.
 */
export const FHIR_CATALOG: Record<RecursoFhir, CampoFhirSpec[]> = {
  ServiceRequest: [
    {
      caminho: 'ServiceRequest.status',
      descricao: 'Situação do pedido (1..1 no FHIR R4: active | completed | revoked)',
      tipo: 'code',
      origemObrigatoriedade: 'FHIR_R4',
    },
    {
      caminho: 'ServiceRequest.intent',
      descricao: 'Intenção do pedido (1..1 no FHIR R4: order | proposal | plan)',
      tipo: 'code',
      origemObrigatoriedade: 'FHIR_R4',
    },
    {
      caminho: 'ServiceRequest.subject.reference',
      descricao: 'Referência ao paciente (satisfaz ServiceRequest.subject 1..1 no FHIR R4)',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
      grupoObrigatorioFhir: 'ServiceRequest.subject',
    },
    {
      caminho: 'ServiceRequest.subject.display',
      descricao: 'Descrição textual do paciente (alternativa válida para ServiceRequest.subject)',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
      grupoObrigatorioFhir: 'ServiceRequest.subject',
    },
    {
      caminho: 'ServiceRequest.code.coding.code',
      descricao: 'Código do exame solicitado (opcional no FHIR R4; exigido pelo fluxo laboratorial)',
      tipo: 'code',
      origemObrigatoriedade: 'REGRA_PROJETO',
      colecaoFhir: true,
    },
    {
      caminho: 'ServiceRequest.code.text',
      descricao: 'Descrição textual do exame solicitado',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
    },
    {
      caminho: 'ServiceRequest.authoredOn',
      descricao: 'Data/hora sintética de emissão do pedido',
      tipo: 'dateTime',
      origemObrigatoriedade: 'OPCIONAL',
    },
  ],
  DiagnosticReport: [
    {
      caminho: 'DiagnosticReport.status',
      descricao: 'Situação do laudo (1..1 no FHIR R4: registered | preliminary | final)',
      tipo: 'code',
      origemObrigatoriedade: 'FHIR_R4',
    },
    {
      caminho: 'DiagnosticReport.code.coding.code',
      descricao: 'Código do painel diagnóstico (satisfaz DiagnosticReport.code 1..1 no FHIR R4)',
      tipo: 'code',
      origemObrigatoriedade: 'OPCIONAL',
      grupoObrigatorioFhir: 'DiagnosticReport.code',
      colecaoFhir: true,
    },
    {
      caminho: 'DiagnosticReport.code.text',
      descricao: 'Nome textual do painel (alternativa válida para DiagnosticReport.code)',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
      grupoObrigatorioFhir: 'DiagnosticReport.code',
    },
    {
      caminho: 'DiagnosticReport.subject.reference',
      descricao: 'Referência ao paciente (opcional no FHIR R4; exigido pelo fluxo do projeto)',
      tipo: 'string',
      origemObrigatoriedade: 'REGRA_PROJETO',
    },
    {
      caminho: 'DiagnosticReport.conclusion',
      descricao: 'Conclusão textual sintética do laudo',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
    },
  ],
  Observation: [
    {
      caminho: 'Observation.status',
      descricao: 'Situação da observação (1..1 no FHIR R4: registered | preliminary | final)',
      tipo: 'code',
      origemObrigatoriedade: 'FHIR_R4',
    },
    {
      caminho: 'Observation.code.coding.code',
      descricao: 'Código do analito/exame (satisfaz Observation.code 1..1 no FHIR R4)',
      tipo: 'code',
      origemObrigatoriedade: 'OPCIONAL',
      grupoObrigatorioFhir: 'Observation.code',
      colecaoFhir: true,
    },
    {
      caminho: 'Observation.code.text',
      descricao: 'Descrição textual do analito (alternativa válida para Observation.code)',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
      grupoObrigatorioFhir: 'Observation.code',
    },
    {
      caminho: 'Observation.valueQuantity.value',
      descricao: 'Valor numérico do resultado',
      tipo: 'decimal',
      origemObrigatoriedade: 'OPCIONAL',
    },
    {
      caminho: 'Observation.valueQuantity.unit',
      descricao: 'Unidade de medida (ex.: mg/dL)',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
    },
  ],
  Patient: [
    {
      caminho: 'Patient.identifier.value',
      descricao: 'Identificador sintético (opcional no FHIR R4; exigido por regra do projeto)',
      tipo: 'string',
      origemObrigatoriedade: 'REGRA_PROJETO',
      colecaoFhir: true,
    },
    {
      caminho: 'Patient.name.text',
      descricao: 'Nome completo sintético (0..* no FHIR R4)',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
      colecaoFhir: true,
    },
    {
      caminho: 'Patient.gender',
      descricao: 'Sexo administrativo: male | female | other | unknown (0..1 no FHIR R4)',
      tipo: 'code',
      origemObrigatoriedade: 'OPCIONAL',
    },
    {
      caminho: 'Patient.birthDate',
      descricao: 'Data de nascimento sintética AAAA-MM-DD (0..1 no FHIR R4)',
      tipo: 'date',
      origemObrigatoriedade: 'OPCIONAL',
    },
  ],
  Specimen: [
    {
      caminho: 'Specimen.identifier.value',
      descricao: 'Código sintético da amostra (0..* no FHIR R4)',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
      colecaoFhir: true,
    },
    {
      caminho: 'Specimen.type.text',
      descricao: 'Tipo de material biológico (0..1 no FHIR R4)',
      tipo: 'string',
      origemObrigatoriedade: 'OPCIONAL',
    },
  ],
  Practitioner: [],
  PractitionerRole: [],
  Organization: [],
};
