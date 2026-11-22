import { useCallback, useEffect, useMemo, useState } from 'react';
import type {
  MapeamentoConfig,
  RecursoFhir,
  RegraCampo,
  SentidoTipo,
  Tabela,
} from '../types/domain';
import type {
  CampoFhirSpec,
  CampoLegado,
  ContextoTesteKey,
  ErroValidacao,
  ResultadoAcaoLocal,
  ResultadoOperacao,
} from '../types/ui';
import type { MappingGateway } from '../services/mappingGateway';
import { validarConfiguracaoDesconhecida } from '../utils/configSchema';
import { avaliarPendenciasRecurso, criarTabelaInversa } from '../utils/mappingValidation';

const CONFIG_VAZIA: MapeamentoConfig = { mapeamentos: [], tabelas: [] };

export function useMappingWorkspace(
  gateway: MappingGateway,
  catalogoFhir: Record<RecursoFhir, CampoFhirSpec[]>
) {
  const [activeConfig, setActiveConfig] = useState<MapeamentoConfig>(CONFIG_VAZIA);
  const [draftConfig, setDraftConfig] = useState<MapeamentoConfig>(CONFIG_VAZIA);

  const [recursoAtual, setRecursoAtual] = useState<RecursoFhir>('ServiceRequest');
  const [sentidoAtual, setSentidoAtual] = useState<SentidoTipo>('LEGADO_PARA_FHIR');
  const [recursoPendenteTroca, setRecursoPendenteTroca] = useState<RecursoFhir | null>(null);

  const [camposLegadoPorRecurso, setCamposLegadoPorRecurso] = useState<
    Partial<Record<RecursoFhir, CampoLegado[]>>
  >({});
  const [exemplosTeste, setExemplosTeste] = useState<
    Partial<Record<ContextoTesteKey, Record<string, unknown>>>
  >({});

  const [resultadoOperacao, setResultadoOperacao] = useState<ResultadoOperacao | null>(null);
  const [carregando, setCarregando] = useState(false);

  const chaveContexto: ContextoTesteKey = `${recursoAtual}:${sentidoAtual}`;
  const camposLegadoAtuais = camposLegadoPorRecurso[recursoAtual] ?? [];
  const exemploTesteAtual = exemplosTeste[chaveContexto] ?? null;

  useEffect(() => {
    let montado = true;
    setCarregando(true);
    gateway
      .buscarConfiguracaoAtiva()
      .then((cfg) => {
        if (!montado) return;
        setActiveConfig(structuredClone(cfg));
        setDraftConfig(structuredClone(cfg));
      })
      .catch(() => {
        if (!montado) return;
        setResultadoOperacao({
          acao: 'VALIDAR',
          sucesso: false,
          timestamp: new Date().toISOString(),
          mensagemResumo: 'Não foi possível carregar a configuração ativa do servidor.',
          erros: [
            {
              local: 'API /config',
              motivo: 'Falha de comunicação ao inicializar a interface.',
              severidade: 'ERRO',
            },
          ],
          avisos: [],
        });
      })
      .finally(() => {
        if (montado) setCarregando(false);
      });
    return () => {
      montado = false;
    };
  }, [gateway]);

  const isDirty = useMemo(
    () => JSON.stringify(activeConfig) !== JSON.stringify(draftConfig),
    [activeConfig, draftConfig]
  );

  const resumoObrigatorios = useMemo(
    () =>
      avaliarPendenciasRecurso(
        draftConfig,
        recursoAtual,
        sentidoAtual,
        catalogoFhir[recursoAtual] ?? []
      ),
    [draftConfig, recursoAtual, sentidoAtual, catalogoFhir]
  );

  /** carrega campos legados (molde) e registra exemplo de teste para LEGADO_PARA_FHIR */
  const registrarMoldeLegado = useCallback(
    (campos: CampoLegado[], objetoBruto: Record<string, unknown>) => {
      setCamposLegadoPorRecurso((prev) => ({ ...prev, [recursoAtual]: campos }));
      setExemplosTeste((prev) => ({
        ...prev,
        [`${recursoAtual}:LEGADO_PARA_FHIR`]: objetoBruto,
      }));
    },
    [recursoAtual]
  );

  /** registra exemplo FHIR R4 apenas para teste de FHIR_PARA_LEGADO */
  const registrarExemploFhirTeste = useCallback(
    (objetoFhir: Record<string, unknown>) => {
      setExemplosTeste((prev) => ({
        ...prev,
        [`${recursoAtual}:FHIR_PARA_LEGADO`]: objetoFhir,
      }));
    },
    [recursoAtual]
  );

  const solicitarTrocaRecurso = useCallback(
    (novoRecurso: RecursoFhir) => {
      if (novoRecurso === recursoAtual) return;
      if (isDirty) {
        setRecursoPendenteTroca(novoRecurso);
      } else {
        setRecursoAtual(novoRecurso);
        setResultadoOperacao(null);
      }
    },
    [isDirty, recursoAtual]
  );

  const confirmarTrocaDescartandoRascunho = useCallback(() => {
    if (!recursoPendenteTroca) return;
    setDraftConfig(structuredClone(activeConfig));
    setRecursoAtual(recursoPendenteTroca);
    setRecursoPendenteTroca(null);
    setResultadoOperacao(null);
  }, [activeConfig, recursoPendenteTroca]);

  const cancelarTrocaRecurso = useCallback(() => {
    setRecursoPendenteTroca(null);
  }, []);

  const salvarRegraNoRascunho = useCallback(
    (params: {
      regra: RegraCampo;
      tabela?: Tabela;
      aplicarInversa: boolean;
    }): ResultadoAcaoLocal => {
      let tabelaInversaGerada: Tabela | undefined;

      if (params.aplicarInversa && params.tabela) {
        const { tabelaInversa, colisoes } = criarTabelaInversa(params.tabela);
        if (colisoes.length > 0) {
          return {
            ok: false,
            erro: `Inversão automática bloqueada: códigos de destino duplicados [${colisoes.join(', ')}] tornam o mapeamento inverso ambíguo.`,
          };
        }
        tabelaInversaGerada = tabelaInversa;
      }

      setDraftConfig((prev) => {
        const next = structuredClone(prev);

        const upsertRegra = (sentido: SentidoTipo, regraAlvo: RegraCampo) => {
          let map = next.mapeamentos.find((m) => m.recurso === recursoAtual);
          if (!map) {
            map = { recurso: recursoAtual, sentidos: [] };
            next.mapeamentos.push(map);
          }
          let s = map.sentidos.find((item) => item.tipo === sentido);
          if (!s) {
            s = { tipo: sentido, regras: [] };
            map.sentidos.push(s);
          }
          const idx = s.regras.findIndex(
            (r) => r.caminhoDestino === regraAlvo.caminhoDestino
          );
          if (idx >= 0) s.regras[idx] = regraAlvo;
          else s.regras.push(regraAlvo);
        };

        const upsertTabela = (tab: Tabela) => {
          const idx = next.tabelas.findIndex((t) => t.nome === tab.nome);
          if (idx >= 0) next.tabelas[idx] = tab;
          else next.tabelas.push(tab);
        };

        if (params.tabela) upsertTabela(params.tabela);
        upsertRegra(sentidoAtual, params.regra);

        if (params.aplicarInversa) {
          const sentidoOposto: SentidoTipo =
            sentidoAtual === 'LEGADO_PARA_FHIR'
              ? 'FHIR_PARA_LEGADO'
              : 'LEGADO_PARA_FHIR';

          let regraInversaStr = params.regra.regra;
          if (tabelaInversaGerada) {
            upsertTabela(tabelaInversaGerada);
            regraInversaStr = `TABELA:${tabelaInversaGerada.nome}`;
          }

          upsertRegra(sentidoOposto, {
            caminhoOrigem: params.regra.caminhoDestino,
            caminhoDestino: params.regra.caminhoOrigem,
            regra: regraInversaStr,
            obrigatorio: params.regra.obrigatorio,
          });
        }

        return next;
      });

      return { ok: true };
    },
    [recursoAtual, sentidoAtual]
  );

  const removerConexao = useCallback(
    (caminhoDestino: string) => {
      setDraftConfig((prev) => {
        const next = structuredClone(prev);
        const map = next.mapeamentos.find((m) => m.recurso === recursoAtual);
        const s = map?.sentidos.find((item) => item.tipo === sentidoAtual);
        if (s) {
          s.regras = s.regras.filter((r) => r.caminhoDestino !== caminhoDestino);
        }
        return next;
      });
    },
    [recursoAtual, sentidoAtual]
  );

  const descartarRascunho = useCallback(() => {
    setDraftConfig(structuredClone(activeConfig));
    setResultadoOperacao(null);
  }, [activeConfig]);

  /**
   * UC-04 completo: valida e aciona persistência + ativação na API.
   * em caso de recusa/erro da API, activeConfig permanece intocada (RF012).
   */
  const importarEAtivarConfiguracao = useCallback(
    async (configImportada: MapeamentoConfig) => {
      const validacao = validarConfiguracaoDesconhecida(configImportada, catalogoFhir);
      if (!validacao.ok) {
        setResultadoOperacao({
          acao: 'IMPORTAR',
          sucesso: false,
          timestamp: new Date().toISOString(),
          mensagemResumo:
            'Importação rejeitada (RF012). A configuração ativa não foi alterada.',
          erros: validacao.erros,
          avisos: validacao.avisos,
        });
        return;
      }

      setCarregando(true);
      try {
        const res = await gateway.salvarConfiguracao(configImportada);
        if (res.sucesso) {
          setActiveConfig(structuredClone(configImportada));
          setDraftConfig(structuredClone(configImportada));
        }
        setResultadoOperacao({
          ...res,
          acao: 'IMPORTAR',
        });
      } catch {
        setResultadoOperacao({
          acao: 'IMPORTAR',
          sucesso: false,
          timestamp: new Date().toISOString(),
          mensagemResumo:
            'Falha ao importar configuração. A configuração anterior permanece ativa.',
          erros: [
            {
              local: 'API (Importar e Ativar)',
              motivo: 'Servidor indisponível ou erro de rede.',
              severidade: 'ERRO',
            },
          ],
          avisos: [],
        });
      } finally {
        setCarregando(false);
      }
    },
    [catalogoFhir, gateway]
  );

  /** carrega configuração importada como rascunho para revisão antes de salvar */
  const carregarImportadaNoRascunho = useCallback(
    (config: MapeamentoConfig, avisos: ErroValidacao[]) => {
      setDraftConfig(structuredClone(config));
      setResultadoOperacao({
        acao: 'IMPORTAR',
        sucesso: true,
        timestamp: new Date().toISOString(),
        mensagemResumo:
          'Configuração carregada no rascunho para revisão. Salve quando estiver pronto.',
        erros: [],
        avisos,
      });
    },
    []
  );

  /** registra erro de importação sem alterar nenhuma configuração */
  const registrarErroImportacao = useCallback(
    (erros: ErroValidacao[], avisos: ErroValidacao[]) => {
      setResultadoOperacao({
        acao: 'IMPORTAR',
        sucesso: false,
        timestamp: new Date().toISOString(),
        mensagemResumo:
          'Importação rejeitada. A configuração ativa não foi alterada.',
        erros,
        avisos,
      });
    },
    []
  );

  const validarMapeamento = useCallback(async () => {
    setCarregando(true);
    try {
      const res = await gateway.validarConfiguracao(draftConfig);
      const resumoLocal = avaliarPendenciasRecurso(
        draftConfig,
        recursoAtual,
        sentidoAtual,
        catalogoFhir[recursoAtual] ?? []
      );
      const errosCombinados = [...resumoLocal.errosUx, ...res.erros];
      setResultadoOperacao({
        ...res,
        sucesso: res.sucesso && errosCombinados.length === 0,
        erros: errosCombinados,
      });
    } catch {
      setResultadoOperacao({
        acao: 'VALIDAR',
        sucesso: false,
        timestamp: new Date().toISOString(),
        mensagemResumo: 'Falha ao validar a configuração.',
        erros: [
          {
            local: 'API (/validar)',
            motivo: 'Servidor indisponível ou erro de rede.',
            severidade: 'ERRO',
          },
        ],
        avisos: [],
      });
    } finally {
      setCarregando(false);
    }
  }, [catalogoFhir, draftConfig, gateway, recursoAtual, sentidoAtual]);

  const testarMapeamento = useCallback(async () => {
    if (!exemploTesteAtual) {
      setResultadoOperacao({
        acao: 'TESTAR',
        sucesso: false,
        timestamp: new Date().toISOString(),
        mensagemResumo: `Nenhum JSON de exemplo carregado para ${recursoAtual} (${sentidoAtual}).`,
        erros: [
          {
            local: `${recursoAtual} > ${sentidoAtual}`,
            motivo:
              'Carregue um JSON de exemplo antes de executar o teste de conversão.',
            severidade: 'ERRO',
          },
        ],
        avisos: [],
      });
      return;
    }

    setCarregando(true);
    try {
      const res = await gateway.testarConversao({
        config: draftConfig,
        recurso: recursoAtual,
        sentido: sentidoAtual,
        exemploJson: exemploTesteAtual,
      });
      const resumoLocal = avaliarPendenciasRecurso(
        draftConfig,
        recursoAtual,
        sentidoAtual,
        catalogoFhir[recursoAtual] ?? []
      );
      const errosCombinados = [...resumoLocal.errosUx, ...res.erros];
      setResultadoOperacao({
        ...res,
        sucesso: res.sucesso && errosCombinados.length === 0,
        erros: errosCombinados,
      });
    } catch {
      setResultadoOperacao({
        acao: 'TESTAR',
        sucesso: false,
        timestamp: new Date().toISOString(),
        mensagemResumo: 'Falha ao executar teste de conversão.',
        erros: [
          {
            local: 'API (/testar)',
            motivo: 'Servidor indisponível ou erro de rede.',
            severidade: 'ERRO',
          },
        ],
        avisos: [],
      });
    } finally {
      setCarregando(false);
    }
  }, [catalogoFhir, draftConfig, exemploTesteAtual, gateway, recursoAtual, sentidoAtual]);

  const salvarMapeamento = useCallback(async () => {
    setCarregando(true);
    try {
      const res = await gateway.salvarConfiguracao(draftConfig);
      if (res.sucesso) {
        setActiveConfig(structuredClone(draftConfig));
      }
      setResultadoOperacao(res);
    } catch {
      setResultadoOperacao({
        acao: 'SALVAR',
        sucesso: false,
        timestamp: new Date().toISOString(),
        mensagemResumo:
          'Falha ao salvar. A configuração anterior permanece ativa.',
        erros: [
          {
            local: 'API (PUT /config)',
            motivo: 'Servidor indisponível ou erro de rede.',
            severidade: 'ERRO',
          },
        ],
        avisos: [],
      });
    } finally {
      setCarregando(false);
    }
  }, [draftConfig, gateway]);

  return {
    activeConfig,
    draftConfig,
    recursoAtual,
    sentidoAtual,
    setSentidoAtual,
    recursoPendenteTroca,
    camposLegadoAtuais,
    exemploTesteAtual,
    resultadoOperacao,
    carregando,
    isDirty,
    resumoObrigatorios,
    registrarMoldeLegado,
    registrarExemploFhirTeste,
    solicitarTrocaRecurso,
    confirmarTrocaDescartandoRascunho,
    cancelarTrocaRecurso,
    salvarRegraNoRascunho,
    removerConexao,
    descartarRascunho,
    importarEAtivarConfiguracao,
    carregarImportadaNoRascunho,
    registrarErroImportacao,
    validarMapeamento,
    testarMapeamento,
    salvarMapeamento,
  };
}
