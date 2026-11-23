import type { MapeamentoConfig, RecursoFhir, SentidoTipo } from '../types/domain';
import type { ResultadoOperacao } from '../types/ui';
import { CONFIG_INICIAL_SINTETICA } from '../data/syntheticExamples';
import { httpMappingApi } from './httpMappingApi';
import { criarMockMappingApi } from './mockMappingApi';

export class SemConfigAtivaError extends Error {
  readonly codigo = 'SEM_CONFIG_ATIVA';
  constructor(mensagem = 'Nenhuma configuração ativa cadastrada no servidor.') {
    super(mensagem);
    this.name = 'SemConfigAtivaError';
  }
}

export interface MappingGateway {
  buscarConfiguracaoAtiva(): Promise<MapeamentoConfig>;
  validarConfiguracao(config: MapeamentoConfig): Promise<ResultadoOperacao>;
  testarConversao(params: {
    config: MapeamentoConfig;
    recurso: RecursoFhir;
    sentido: SentidoTipo;
    exemploJson: Record<string, unknown>;
  }): Promise<ResultadoOperacao>;
  salvarConfiguracao(
    config: MapeamentoConfig
  ): Promise<ResultadoOperacao & { configConfirmada?: MapeamentoConfig }>;
}

const emAmbienteDeTeste = import.meta.env.MODE === 'test';
const usarMock =
  emAmbienteDeTeste || import.meta.env.VITE_USE_MOCK_API === 'true';

export const mappingGateway: MappingGateway = usarMock
  ? criarMockMappingApi(CONFIG_INICIAL_SINTETICA)
  : httpMappingApi;
