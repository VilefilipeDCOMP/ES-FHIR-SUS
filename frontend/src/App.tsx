import { useRef, useState } from 'react';
import { AppShell } from './components/layout/AppShell';
import { JsonPreviewTab } from './components/mapping/JsonPreviewTab';
import { LoadJsonModal } from './components/mapping/LoadJsonModal';
import { MappingCanvas } from './components/mapping/MappingCanvas';
import { ProgressBanner } from './components/mapping/ProgressBanner';
import { ResultCard } from './components/mapping/ResultCard';
import { RuleDrawer } from './components/mapping/RuleDrawer';
import { UnsavedChangesDialog } from './components/mapping/UnsavedChangesDialog';
import { FHIR_CATALOG } from './data/fhirCatalog';
import { useMappingWorkspace } from './hooks/useMappingWorkspace';
import { mappingGateway } from './services/mappingGateway';
import { RECURSOS_ATIVOS_P3, type RecursoFhir, type RegraCampo } from './types/domain';
import { obterRegrasDoSentido } from './utils/mappingValidation';

export function App() {
  const ws = useMappingWorkspace(mappingGateway, FHIR_CATALOG);
  const [abaAtiva, setAbaAtiva] = useState<'CAMPOS' | 'PREVIA'>('CAMPOS');
  const [modalJsonAberto, setModalJsonAberto] = useState(false);
  const [regraEmEdicao, setRegraEmEdicao] = useState<RegraCampo | null>(null);
  const resultRef = useRef<HTMLDivElement>(null);

  const regrasAtuais = obterRegrasDoSentido(ws.draftConfig, ws.recursoAtual, ws.sentidoAtual);

  const tabelaDaRegra = regraEmEdicao?.regra.startsWith('TABELA:')
    ? ws.draftConfig.tabelas.find((t) => t.nome === regraEmEdicao.regra.slice('TABELA:'.length))
    : undefined;

  return (
    <AppShell>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-bold text-slate-900">Mapeamento De-Para</h1>
        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            onClick={() => setModalJsonAberto(true)}
            className="px-3 py-2 text-xs font-medium border rounded bg-white hover:bg-slate-50"
          >
            Carregar JSON
          </button>
          <button
            type="button"
            disabled={ws.carregando}
            onClick={ws.validarMapeamento}
            className="px-3 py-2 text-xs font-medium border rounded bg-white hover:bg-slate-50"
          >
            Validar
          </button>
          <button
            type="button"
            disabled={ws.carregando}
            onClick={ws.testarMapeamento}
            className="px-3 py-2 text-xs font-medium border rounded bg-white hover:bg-slate-50"
          >
            Testar
          </button>
          <button
            type="button"
            disabled={ws.carregando}
            onClick={ws.salvarMapeamento}
            className="px-3.5 py-2 text-xs font-semibold rounded bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50"
          >
            Salvar mapeamento
          </button>
        </div>
      </div>

      <div className="bg-white border rounded-lg p-3 flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-3">
          <label className="text-xs font-medium text-slate-700 flex items-center gap-2">
            Recurso FHIR:
            <select
              value={ws.recursoAtual}
              onChange={(e) => ws.solicitarTrocaRecurso(e.target.value as RecursoFhir)}
              className="border rounded px-2.5 py-1.5 text-xs bg-white font-semibold"
            >
              {RECURSOS_ATIVOS_P3.map((rec) => (
                <option key={rec} value={rec}>
                  {rec}
                </option>
              ))}
            </select>
          </label>

          <div className="inline-flex rounded border overflow-hidden text-xs">
            <button
              type="button"
              onClick={() => ws.setSentidoAtual('LEGADO_PARA_FHIR')}
              className={`px-3 py-1.5 ${
                ws.sentidoAtual === 'LEGADO_PARA_FHIR'
                  ? 'bg-slate-900 text-white font-medium'
                  : 'bg-white text-slate-700'
              }`}
            >
              Legado → FHIR
            </button>
            <button
              type="button"
              onClick={() => ws.setSentidoAtual('FHIR_PARA_LEGADO')}
              className={`px-3 py-1.5 ${
                ws.sentidoAtual === 'FHIR_PARA_LEGADO'
                  ? 'bg-slate-900 text-white font-medium'
                  : 'bg-white text-slate-700'
              }`}
            >
              FHIR → Legado
            </button>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <span
            className={`text-xs px-2.5 py-1 rounded border ${
              ws.isDirty
                ? 'bg-amber-50 border-amber-300 text-amber-800 font-medium'
                : 'bg-slate-50 border-slate-200 text-slate-600'
            }`}
          >
            {ws.isDirty ? 'Rascunho (alterações não salvas)' : 'Rascunho sincronizado'}
          </span>
          {ws.isDirty && (
            <button
              type="button"
              onClick={ws.descartarRascunho}
              className="text-xs text-slate-600 hover:underline"
            >
              Descartar
            </button>
          )}
        </div>
      </div>

      <ProgressBanner
        resumo={ws.resumoObrigatorios}
        onVerPendencias={() => {
          ws.validarMapeamento();
          resultRef.current?.scrollIntoView({ behavior: 'smooth' });
        }}
      />

      <div ref={resultRef}>
        <ResultCard resultado={ws.resultadoOperacao} />
      </div>

      <div className="border-b flex gap-4 text-xs font-semibold">
        <button
          type="button"
          onClick={() => setAbaAtiva('CAMPOS')}
          className={`pb-2 border-b-2 ${
            abaAtiva === 'CAMPOS'
              ? 'border-blue-600 text-blue-700'
              : 'border-transparent text-slate-500'
          }`}
        >
          Mapeamento de campos
        </button>
        <button
          type="button"
          onClick={() => setAbaAtiva('PREVIA')}
          className={`pb-2 border-b-2 ${
            abaAtiva === 'PREVIA'
              ? 'border-blue-600 text-blue-700'
              : 'border-transparent text-slate-500'
          }`}
        >
          Prévia JSON
        </button>
      </div>

      {abaAtiva === 'CAMPOS' ? (
        <MappingCanvas
          sentido={ws.sentidoAtual}
          camposLegado={ws.camposLegadoAtuais}
          camposFhir={FHIR_CATALOG[ws.recursoAtual] ?? []}
          regras={regrasAtuais}
          onCriarConexao={(origem, destino, obrigatorio) =>
            ws.salvarRegraNoRascunho({
              regra: {
                caminhoOrigem: origem,
                caminhoDestino: destino,
                regra: 'COPIAR',
                obrigatorio,
              },
              aplicarInversa: false,
            })
          }
          onSelecionarRegra={(r) => setRegraEmEdicao(r)}
        />
      ) : (
        <JsonPreviewTab
          config={ws.draftConfig}
          catalogoFhir={FHIR_CATALOG}
          onImportarEAtivar={ws.importarEAtivarConfiguracao}
          onCarregarNoRascunho={ws.carregarImportadaNoRascunho}
          onImportarErro={ws.registrarErroImportacao}
        />
      )}

      {regraEmEdicao && (
        <RuleDrawer
          regra={regraEmEdicao}
          tabelaAtual={tabelaDaRegra}
          onAplicar={(params) => {
            const res = ws.salvarRegraNoRascunho(params);
            if (res.ok) setRegraEmEdicao(null);
            return res;
          }}
          onRemover={() => {
            ws.removerConexao(regraEmEdicao.caminhoDestino);
            setRegraEmEdicao(null);
          }}
          onCancelar={() => setRegraEmEdicao(null)}
        />
      )}

      <LoadJsonModal
        aberto={modalJsonAberto}
        recursoAtual={ws.recursoAtual}
        sentidoAtual={ws.sentidoAtual}
        onFechar={() => setModalJsonAberto(false)}
        onConcluirMoldeLegado={ws.registrarMoldeLegado}
        onConcluirExemploFhir={ws.registrarExemploFhirTeste}
      />

      <UnsavedChangesDialog
        recursoDestino={ws.recursoPendenteTroca}
        onConfirmarDescarte={ws.confirmarTrocaDescartandoRascunho}
        onCancelar={ws.cancelarTrocaRecurso}
      />
    </AppShell>
  );
}

export default App;
