import type { MapeamentoConfig, RecursoFhir, SentidoTipo } from '../types/domain';
import type { ResultadoOperacao } from '../types/ui';
import { CONFIG_INICIAL_SINTETICA } from '../data/syntheticExamples';
import { httpMappingApi } from './httpMappingApi';
import { criarMockMappingApi } from './mockMappingApi';

export interface MappingGateway {
  buscarConfiguracaoAtiva(): Promise<MapeamentoConfig>;
  validarConfiguracao(config: MapeamentoConfig): Promise<ResultadoOperacao>;
  testarConversao(params: {
    config: MapeamentoConfig;
    recurso: RecursoFhir;
    sentido: SentidoTipo;
    exemploJson: Record<string, unknown>;
  }): Promise<ResultadoOperacao>;
  salvarConfiguracao(config: MapeamentoConfig): Promise<ResultadoOperacao>;
}

const usarMock = import.meta.env.VITE_USE_MOCK_API !== 'false';

export const mappingGateway: MappingGateway = usarMock
  ? criarMockMappingApi(CONFIG_INICIAL_SINTETICA)
  : httpMappingApi;
