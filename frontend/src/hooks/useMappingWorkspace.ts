import { useCallback, useEffect, useMemo, useState } from 'react';
import type {
  DominioValores,
  MapeamentoConfig,
  RecursoFhir,
  RegraCampo,
  SentidoTipo,
} from '../types/domain';
import type {
  CampoFhirSpec,
  CampoLegado,
  ContextoTesteKey,
  ErroValidacao,
  EstadoConexaoApi,
  ResultadoAcaoLocal,
  ResultadoOperacao,
} from '../types/ui';
import { SemConfigAtivaError, type MappingGateway } from '../services/mappingGateway';
import { validarConfiguracaoDesconhecida } from '../utils/configSchema';
import {
  analisarCaminhoDestino,
  analisarCaminhoOrigem,
  avaliarPendenciasRecurso,
  criarTabelaInversa,
  listarReferenciasDominio,
  renomearDominioNaConfig,
  serializarConfigParaContrato,
  serializarRegraParaContrato,
  validarDestinosDoSentido,
} from '../utils/mappingValidation';

const CONFIG_VAZIA: MapeamentoConfig = { mapeamentos: [], dominios: [] };

function converterOrigemParaDestinoInverso(caminhoOrigem: string): string {
  const limpo = caminhoOrigem.trim();
  if (limpo.startsWith('$.')) return limpo.slice(2);
  if (limpo === '$') return '';
  return limpo;
}

function converterDestinoParaOrigemInversa(caminhoDestino: string): string {
  const limpo = caminhoDestino.trim();
  if (!limpo) return '$';
  if (limpo.startsWith('$')) return limpo;
  return `$.${limpo}`;
}

export function useMappingWorkspace(
  gateway: MappingGateway,
  catalogoFhir: Record<RecursoFhir, CampoFhirSpec[]>
) {
  const [activeConfig, setActiveConfig] = useState<MapeamentoConfig>(CONFIG_VAZIA);
  const [draftConfig, setDraftConfig] = useState<MapeamentoConfig>(CONFIG_VAZIA);
  const [estadoConexao, setEstadoConexao] = useState<EstadoConexaoApi>('SINCRONIZADO');

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
        const canonica = serializarConfigParaContrato(cfg);
        setActiveConfig(structuredClone(canonica));
        setDraftConfig(structuredClone(canonica));
        setEstadoConexao('SINCRONIZADO');
        const primeiroRecurso = canonica.mapeamentos[0];
        if (primeiroRecurso) {
          setRecursoAtual(primeiroRecurso.recurso);
          if (primeiroRecurso.toFHIR.length === 0 && primeiroRecurso.toLegado.length > 0) {
            setSentidoAtual('FHIR_PARA_LEGADO');
          }
        }
      })
      .catch((err: unknown) => {
        if (!montado) return;
        if (err instanceof SemConfigAtivaError) {
          setEstadoConexao('SEM_CONFIG_ATIVA');
          setActiveConfig(CONFIG_VAZIA);
          setDraftConfig(CONFIG_VAZIA);
          setResultadoOperacao({
            acao: 'VALIDAR',
            sucesso: true,
            timestamp: new Date().toISOString(),
            mensagemResumo:
              'Nenhuma configuração ativa publicada no servidor. Inicie um novo rascunho ou importe um arquivo.',
            erros: [],
            avisos: [
              {
                local: 'API /api/config/ativa (HTTP 404)',
                motivo: err.message,
                severidade: 'AVISO',
              },
            ],
          });
          return;
        }
        setEstadoConexao('ERRO_CONSULTA');
        setResultadoOperacao({
          acao: 'VALIDAR',
          sucesso: false,
          timestamp: new Date().toISOString(),
          mensagemResumo: 'Não foi possível carregar a configuração ativa do servidor.',
          erros: [
            {
              local: 'API /api/config/ativa',
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
    () =>
      JSON.stringify(serializarConfigParaContrato(activeConfig)) !==
      JSON.stringify(serializarConfigParaContrato(draftConfig)),
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

  const registrarExemploFhirTeste = useCallback(
    (objetoFhir: Record<string, unknown>) => {
      setExemplosTeste((prev) => ({
        ...prev,
        [`${recursoAtual}:FHIR_PARA_LEGADO`]: objetoFhir,
      }));
    },
    [recursoAtual]
  );

  /**
   * Seção 6 do guia: mantém as alterações do rascunho ao trocar de recurso ou direção.
   * Caso o usuário deseje descartar explicitamente ao trocar, o diálogo de descarte continua disponível.
   */
  const selecionarRecursoPreservandoRascunho = useCallback(
    (novoRecurso: RecursoFhir) => {
      if (novoRecurso === recursoAtual) return;
      setRecursoAtual(novoRecurso);
    },
    [recursoAtual]
  );

  const solicitarTrocaRecurso = useCallback(
    (novoRecurso: RecursoFhir, exigirConfirmacaoSeSujo = false) => {
      if (novoRecurso === recursoAtual) return;
      if (exigirConfirmacaoSeSujo && isDirty) {
        setRecursoPendenteTroca(novoRecurso);
      } else {
        setRecursoAtual(novoRecurso);
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

  const navegarParaContexto = useCallback(
    (recurso: RecursoFhir, sentido: SentidoTipo) => {
      setRecursoAtual(recurso);
      setSentidoAtual(sentido);
    },
    []
  );

  const salvarRegraNoRascunho = useCallback(
    (params: {
      regra: RegraCampo;
      caminhoDestinoOriginal?: string;
      tabela?: DominioValores;
      nomeDominioAnterior?: string;
      aplicarInversa: boolean;
    }): ResultadoAcaoLocal => {
      const regraNormalizada = serializarRegraParaContrato(params.regra);

      const analiseDest = analisarCaminhoDestino(regraNormalizada.caminhoDestino);
      if (!analiseDest.ok) {
        return {
          ok: false,
          erro: analiseDest.erro ?? 'Caminho de destino inválido.',
        };
      }

      if (regraNormalizada.tipoConversao !== 'CONSTANTE') {
        const analiseOrig = analisarCaminhoOrigem(regraNormalizada.caminhoOrigem);
        if (!analiseOrig.ok) {
          return {
            ok: false,
            erro: analiseOrig.erro ?? 'Caminho de origem inválido.',
          };
        }
      } else if (!regraNormalizada.parametroConversao.trim()) {
        return {
          ok: false,
          erro: 'Informe o valor literal para a regra CONSTANTE.',
        };
      }

      // valida conflitos de destino no sentido atual antes de comitar no rascunho
      const mapAtual = draftConfig.mapeamentos.find((m) => m.recurso === recursoAtual);
      const listaAtual =
        sentidoAtual === 'LEGADO_PARA_FHIR'
          ? (mapAtual?.toFHIR ?? [])
          : (mapAtual?.toLegado ?? []);
      const chaveSubstituicao = params.caminhoDestinoOriginal ?? regraNormalizada.caminhoDestino;
      const idxExistente = listaAtual.findIndex((r) => r.caminhoDestino === chaveSubstituicao);
      const destinosSimulados = listaAtual.map((r) => r.caminhoDestino);
      if (idxExistente >= 0) {
        destinosSimulados[idxExistente] = regraNormalizada.caminhoDestino;
      } else {
        destinosSimulados.push(regraNormalizada.caminhoDestino);
      }

      const conflitos = validarDestinosDoSentido(destinosSimulados);
      if (conflitos.size > 0) {
        const primeiraMsg = conflitos.values().next().value as string;
        return {
          ok: false,
          erro: primeiraMsg,
        };
      }

      let tabelaInversaGerada: DominioValores | undefined;

      if (params.aplicarInversa) {
        if (regraNormalizada.tipoConversao === 'CONSTANTE') {
          return {
            ok: false,
            erro: 'Regras do tipo CONSTANTE não possuem caminho de origem para gerar regra inversa automática.',
          };
        }
        const destinoInverso = converterOrigemParaDestinoInverso(regraNormalizada.caminhoOrigem);
        if (!destinoInverso) {
          return {
            ok: false,
            erro: 'Não é possível inverter uma regra cuja origem é a raiz "$".',
          };
        }
        if (params.tabela) {
          const { tabelaInversa, colisoes } = criarTabelaInversa(params.tabela);
          if (colisoes.length > 0) {
            return {
              ok: false,
              erro: `Inversão automática bloqueada: códigos de destino duplicados [${colisoes.join(', ')}] tornam o mapeamento inverso ambíguo.`,
            };
          }
          tabelaInversaGerada = tabelaInversa;
        }
      }

      setDraftConfig((prev) => {
        let next = structuredClone(prev);

        if (
          params.tabela &&
          params.nomeDominioAnterior &&
          params.nomeDominioAnterior !== params.tabela.nome
        ) {
          next = renomearDominioNaConfig(
            next,
            params.nomeDominioAnterior,
            params.tabela.nome
          ).configAtualizada;
        }

        const upsertDominio = (tab: DominioValores) => {
          const idx = next.dominios.findIndex((d) => d.nome === tab.nome);
          if (idx >= 0) next.dominios[idx] = tab;
          else next.dominios.push(tab);
        };

        const upsertRegra = (
          sentido: SentidoTipo,
          regraAlvo: RegraCampo,
          destinoSubstituir?: string
        ) => {
          let map = next.mapeamentos.find((m) => m.recurso === recursoAtual);
          if (!map) {
            map = { recurso: recursoAtual, toLegado: [], toFHIR: [] };
            next.mapeamentos.push(map);
          }
          const lista = sentido === 'LEGADO_PARA_FHIR' ? map.toFHIR : map.toLegado;
          const alvoBusca = destinoSubstituir ?? regraAlvo.caminhoDestino;
          const idx = lista.findIndex((r) => r.caminhoDestino === alvoBusca);
          if (idx >= 0) lista[idx] = regraAlvo;
          else lista.push(regraAlvo);
        };

        if (params.tabela) upsertDominio(params.tabela);
        upsertRegra(sentidoAtual, regraNormalizada, params.caminhoDestinoOriginal);

        if (params.aplicarInversa && regraNormalizada.tipoConversao !== 'CONSTANTE') {
          const sentidoOposto: SentidoTipo =
            sentidoAtual === 'LEGADO_PARA_FHIR'
              ? 'FHIR_PARA_LEGADO'
              : 'LEGADO_PARA_FHIR';

          const origemInversa = converterDestinoParaOrigemInversa(
            regraNormalizada.caminhoDestino
          );
          const destinoInverso = converterOrigemParaDestinoInverso(
            regraNormalizada.caminhoOrigem
          );

          if (regraNormalizada.tipoConversao === 'DIRETA') {
            upsertRegra(sentidoOposto, {
              tipoConversao: 'DIRETA',
              caminhoOrigem: origemInversa,
              caminhoDestino: destinoInverso,
              parametroConversao: null,
              obrigatorio: regraNormalizada.obrigatorio,
            });
          } else if (tabelaInversaGerada) {
            upsertDominio(tabelaInversaGerada);
            upsertRegra(sentidoOposto, {
              tipoConversao: 'DOMINIO',
              caminhoOrigem: origemInversa,
              caminhoDestino: destinoInverso,
              parametroConversao: tabelaInversaGerada.nome,
              obrigatorio: regraNormalizada.obrigatorio,
            });
          }
        }

        return next;
      });

      return { ok: true };
    },
    [draftConfig.mapeamentos, recursoAtual, sentidoAtual]
  );

  const removerConexao = useCallback(
    (caminhoDestino: string) => {
      setDraftConfig((prev) => {
        const next = structuredClone(prev);
        const map = next.mapeamentos.find((m) => m.recurso === recursoAtual);
        if (!map) return next;
        if (sentidoAtual === 'LEGADO_PARA_FHIR') {
          map.toFHIR = map.toFHIR.filter((r) => r.caminhoDestino !== caminhoDestino);
        } else {
          map.toLegado = map.toLegado.filter((r) => r.caminhoDestino !== caminhoDestino);
        }
        return next;
      });
    },
    [recursoAtual, sentidoAtual]
  );

  const renomearDominio = useCallback(
    (nomeAntigo: string, nomeNovo: string): ResultadoAcaoLocal => {
      const novoLimpo = nomeNovo.trim();
      if (!novoLimpo) {
        return { ok: false, erro: 'O novo nome do domínio não pode ser vazio.' };
      }
      if (
        novoLimpo !== nomeAntigo &&
        draftConfig.dominios.some((d) => d.nome === novoLimpo)
      ) {
        return {
          ok: false,
          erro: `Já existe um domínio com o nome "${novoLimpo}".`,
        };
      }
      setDraftConfig((prev) => renomearDominioNaConfig(prev, nomeAntigo, novoLimpo).configAtualizada);
      return { ok: true };
    },
    [draftConfig.dominios]
  );

  const excluirDominio = useCallback(
    (nomeDominio: string): ResultadoAcaoLocal => {
      const refs = listarReferenciasDominio(draftConfig, nomeDominio);
      if (refs.length > 0) {
        const detalhes = refs
          .map((r) => `${r.recurso}.${r.lista}[${r.indiceRegra}] (${r.caminhoDestino})`)
          .join(', ');
        return {
          ok: false,
          erro: `Não é possível excluir o domínio "${nomeDominio}" pois ele é referenciado em ${refs.length} regra(s): ${detalhes}.`,
        };
      }
      setDraftConfig((prev) => ({
        ...prev,
        dominios: prev.dominios.filter((d) => d.nome !== nomeDominio),
      }));
      return { ok: true };
    },
    [draftConfig]
  );

  const descartarRascunho = useCallback(() => {
    setDraftConfig(structuredClone(activeConfig));
    setResultadoOperacao(null);
  }, [activeConfig]);

  const importarEAtivarConfiguracao = useCallback(
    async (configImportada: MapeamentoConfig) => {
      const validacao = validarConfiguracaoDesconhecida(configImportada, catalogoFhir);
      if (!validacao.ok || !validacao.config) {
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
        const res = await gateway.salvarConfiguracao(validacao.config);
        if (res.sucesso) {
          const confirmada = serializarConfigParaContrato(
            res.configConfirmada ?? validacao.config
          );
          setActiveConfig(structuredClone(confirmada));
          setDraftConfig(structuredClone(confirmada));
          setEstadoConexao('SINCRONIZADO');
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

  const carregarImportadaNoRascunho = useCallback(
    (config: MapeamentoConfig, avisos: ErroValidacao[]) => {
      setDraftConfig(structuredClone(serializarConfigParaContrato(config)));
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
      const canonica = serializarConfigParaContrato(draftConfig);
      const res = await gateway.validarConfiguracao(canonica);
      const resumoLocal = avaliarPendenciasRecurso(
        canonica,
        recursoAtual,
        sentidoAtual,
        catalogoFhir[recursoAtual] ?? []
      );
      const chavesVistas = new Set(res.erros.map((e) => `${e.local}|${e.motivo}`));
      const errosLocaisExtras = resumoLocal.errosUx.filter(
        (e) => !chavesVistas.has(`${e.local}|${e.motivo}`)
      );
      const errosCombinados = [...res.erros, ...errosLocaisExtras];
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
            local: 'API (/api/config/validar)',
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
      const canonica = serializarConfigParaContrato(draftConfig);
      const res = await gateway.testarConversao({
        config: canonica,
        recurso: recursoAtual,
        sentido: sentidoAtual,
        exemploJson: exemploTesteAtual,
      });
      setResultadoOperacao(res);
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
  }, [draftConfig, exemploTesteAtual, gateway, recursoAtual, sentidoAtual]);

  const salvarMapeamento = useCallback(async () => {
    setCarregando(true);
    try {
      const canonica = serializarConfigParaContrato(draftConfig);
      const res = await gateway.salvarConfiguracao(canonica);
      if (res.sucesso) {
        const confirmada = serializarConfigParaContrato(
          res.configConfirmada ?? canonica
        );
        setActiveConfig(structuredClone(confirmada));
        setDraftConfig(structuredClone(confirmada));
        setEstadoConexao('SINCRONIZADO');
      }
      setResultadoOperacao(res);
    } catch {
      setResultadoOperacao({
        acao: 'SALVAR',
        sucesso: false,
        timestamp: new Date().toISOString(),
        mensagemResumo:
          'Falha ao salvar. O rascunho foi preservado e a configuração anterior permanece ativa.',
        erros: [
          {
            local: 'API (POST /api/config)',
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
    estadoConexao,
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
    selecionarRecursoPreservandoRascunho,
    solicitarTrocaRecurso,
    confirmarTrocaDescartandoRascunho,
    cancelarTrocaRecurso,
    navegarParaContexto,
    salvarRegraNoRascunho,
    removerConexao,
    renomearDominio,
    excluirDominio,
    descartarRascunho,
    importarEAtivarConfiguracao,
    carregarImportadaNoRascunho,
    registrarErroImportacao,
    validarMapeamento,
    testarMapeamento,
    salvarMapeamento,
  };
}
