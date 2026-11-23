import type React from 'react';
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import type { RecursoFhir, RegraCampo, SentidoTipo } from '../../types/domain';
import type { CampoFhirSpec, CampoLegado } from '../../types/ui';
import {
  normalizarCaminhoFhirParaCatalogo,
  participaDeObrigatoriedade,
} from '../../data/fhirCatalog';

interface LinhaSvg {
  id: string;
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  regra: RegraCampo;
}

interface Props {
  recurso: RecursoFhir;
  sentido: SentidoTipo;
  camposLegado: CampoLegado[];
  camposFhir: CampoFhirSpec[];
  regras: RegraCampo[];
  onCriarConexao: (origem: string, destino: string, obrigatorio: boolean) => void;
  onSelecionarRegra: (regra: RegraCampo) => void;
}

function normalizarCaminhoLegado(caminho: string | undefined): string {
  if (!caminho) return '';
  const limpo = caminho.trim();
  if (limpo.startsWith('$.')) return limpo.slice(2);
  return limpo;
}

export const MappingCanvas: React.FC<Props> = ({
  recurso,
  sentido,
  camposLegado,
  camposFhir,
  regras,
  onCriarConexao,
  onSelecionarRegra,
}) => {
  const [buscaLegado, setBuscaLegado] = useState('');
  const [buscaFhir, setBuscaFhir] = useState('');
  const [origemSelecionada, setOrigemSelecionada] = useState<{
    lado: 'LEGADO' | 'FHIR';
    caminho: string;
    obrigatorio: boolean;
  } | null>(null);
  const [linhas, setLinhas] = useState<LinhaSvg[]>([]);

  const containerRef = useRef<HTMLDivElement>(null);
  const legadoRefs = useRef<Record<string, HTMLElement | null>>({});
  const fhirRefs = useRef<Record<string, HTMLElement | null>>({});

  useEffect(() => {
    setOrigemSelecionada(null);
  }, [sentido, recurso]);

  const legadoFiltrado = camposLegado.filter((c) =>
    c.caminho.toLowerCase().includes(buscaLegado.toLowerCase())
  );
  const fhirFiltrado = camposFhir.filter(
    (c) =>
      c.caminho.toLowerCase().includes(buscaFhir.toLowerCase()) ||
      c.descricao.toLowerCase().includes(buscaFhir.toLowerCase())
  );

  const legadoMapeados = new Set(
    regras
      .map((r) =>
        sentido === 'LEGADO_PARA_FHIR'
          ? normalizarCaminhoLegado(r.tipoConversao === 'CONSTANTE' ? '' : r.caminhoOrigem)
          : normalizarCaminhoLegado(r.caminhoDestino)
      )
      .filter(Boolean)
  );
  const fhirMapeados = new Set(
    regras
      .map((r) =>
        sentido === 'LEGADO_PARA_FHIR'
          ? normalizarCaminhoFhirParaCatalogo(recurso, r.caminhoDestino)
          : normalizarCaminhoFhirParaCatalogo(
              recurso,
              r.tipoConversao === 'CONSTANTE' ? '' : r.caminhoOrigem
            )
      )
      .filter(Boolean)
  );

  const recalcularLinhas = useCallback(() => {
    const container = containerRef.current;
    if (!container) return;
    const baseRect = container.getBoundingClientRect();

    const novas: LinhaSvg[] = [];
    for (const regra of regras) {
      if (regra.tipoConversao === 'CONSTANTE') continue;

      const chaveLegado =
        sentido === 'LEGADO_PARA_FHIR'
          ? normalizarCaminhoLegado(regra.caminhoOrigem)
          : normalizarCaminhoLegado(regra.caminhoDestino);
      const chaveFhir =
        sentido === 'LEGADO_PARA_FHIR'
          ? normalizarCaminhoFhirParaCatalogo(recurso, regra.caminhoDestino)
          : normalizarCaminhoFhirParaCatalogo(recurso, regra.caminhoOrigem);

      const elLegado = legadoRefs.current[chaveLegado];
      const elFhir = fhirRefs.current[chaveFhir];
      if (!elLegado || !elFhir) continue;

      const rLegado = elLegado.getBoundingClientRect();
      const rFhir = elFhir.getBoundingClientRect();

      const xLegado = rLegado.right - baseRect.left;
      const yLegado = rLegado.top + rLegado.height / 2 - baseRect.top;
      const xFhir = rFhir.left - baseRect.left;
      const yFhir = rFhir.top + rFhir.height / 2 - baseRect.top;

      novas.push({
        id: `${regra.caminhoOrigem}->${regra.caminhoDestino}`,
        x1: sentido === 'LEGADO_PARA_FHIR' ? xLegado : xFhir,
        y1: sentido === 'LEGADO_PARA_FHIR' ? yLegado : yFhir,
        x2: sentido === 'LEGADO_PARA_FHIR' ? xFhir : xLegado,
        y2: sentido === 'LEGADO_PARA_FHIR' ? yFhir : yLegado,
        regra,
      });
    }
    setLinhas(novas);
  }, [recurso, regras, sentido]);

  useLayoutEffect(() => {
    recalcularLinhas();
  }, [recalcularLinhas, buscaLegado, buscaFhir, camposLegado, camposFhir]);

  useEffect(() => {
    let observer: ResizeObserver | null = null;
    if (typeof ResizeObserver !== 'undefined' && containerRef.current) {
      observer = new ResizeObserver(recalcularLinhas);
      observer.observe(containerRef.current);
    }
    window.addEventListener('resize', recalcularLinhas);
    return () => {
      observer?.disconnect();
      window.removeEventListener('resize', recalcularLinhas);
    };
  }, [recalcularLinhas]);

  const handleCliqueLegado = (caminhoLegado: string) => {
    const dotPath = normalizarCaminhoLegado(caminhoLegado);
    if (sentido === 'LEGADO_PARA_FHIR') {
      const jsonPath = `$.${dotPath}`;
      setOrigemSelecionada((prev) =>
        prev?.caminho === jsonPath
          ? null
          : { lado: 'LEGADO', caminho: jsonPath, obrigatorio: false }
      );
    } else if (origemSelecionada?.lado === 'FHIR') {
      onCriarConexao(origemSelecionada.caminho, dotPath, origemSelecionada.obrigatorio);
      setOrigemSelecionada(null);
    }
  };

  const handleCliqueFhir = (campo: CampoFhirSpec) => {
    const obg = participaDeObrigatoriedade(campo);
    const dotFhir = normalizarCaminhoFhirParaCatalogo(recurso, campo.caminho);
    if (sentido === 'FHIR_PARA_LEGADO') {
      const jsonPathFhir = `$.${dotFhir}`;
      setOrigemSelecionada((prev) =>
        prev?.caminho === jsonPathFhir
          ? null
          : { lado: 'FHIR', caminho: jsonPathFhir, obrigatorio: obg }
      );
    } else if (origemSelecionada?.lado === 'LEGADO') {
      onCriarConexao(origemSelecionada.caminho, dotFhir, obg);
      setOrigemSelecionada(null);
    }
  };

  const listaNome = sentido === 'LEGADO_PARA_FHIR' ? 'toFHIR' : 'toLegado';

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between text-xs text-slate-600 bg-slate-50 px-3 py-2 rounded border">
        <div className="flex flex-wrap items-center gap-4">
          <span className="flex items-center gap-1.5">
            <span className="h-2.5 w-2.5 rounded-full bg-emerald-500" /> Mapeado
          </span>
          <span className="flex items-center gap-1.5">
            <span className="h-2.5 w-2.5 rounded-full bg-slate-300" /> Não mapeado
          </span>
          <span className="text-red-600 font-medium">
            * Obrigatório (FHIR R4 / Grupo FHIR / Regra do Projeto)
          </span>
        </div>
        <span className="font-semibold text-slate-800">{regras.length} conexões ativas</span>
      </div>

      {/* Lista interativa de regras do sentido atual (inclui DIRETA, DOMINIO e CONSTANTE) */}
      <div className="bg-white border rounded-lg p-3 space-y-2">
        <div className="flex items-center justify-between">
          <span className="text-xs font-semibold text-slate-700">
            Regras configuradas em <code>{recurso}.{listaNome}</code>
          </span>
          <span className="text-[11px] text-slate-500">
            Clique em uma regra (ou na curva SVG) para editar no painel lateral
          </span>
        </div>
        {regras.length === 0 ? (
          <p className="text-xs text-slate-500 italic">
            Nenhuma regra neste sentido (`[]`). Clique nos campos abaixo para conectar.
          </p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {regras.map((regra) => {
              const origemTexto =
                regra.tipoConversao === 'CONSTANTE'
                  ? `"${regra.parametroConversao}"`
                  : regra.caminhoOrigem;
              const badgeColor =
                regra.tipoConversao === 'DOMINIO'
                  ? 'bg-blue-100 text-blue-800 border-blue-300'
                  : regra.tipoConversao === 'CONSTANTE'
                  ? 'bg-purple-100 text-purple-800 border-purple-300'
                  : 'bg-emerald-100 text-emerald-800 border-emerald-300';
              return (
                <button
                  key={`${regra.tipoConversao}:${origemTexto}->${regra.caminhoDestino}`}
                  type="button"
                  onClick={() => onSelecionarRegra(regra)}
                  className="text-xs font-mono px-2.5 py-1.5 rounded border bg-slate-50 hover:bg-slate-100 flex items-center gap-2 transition"
                >
                  <span className={`text-[10px] font-sans font-bold px-1.5 py-0.5 rounded border ${badgeColor}`}>
                    {regra.tipoConversao}
                    {regra.tipoConversao === 'DOMINIO' ? `:${regra.parametroConversao}` : ''}
                  </span>
                  <span>
                    {origemTexto} → <strong>{regra.caminhoDestino}</strong>
                  </span>
                </button>
              );
            })}
          </div>
        )}
      </div>

      <div
        ref={containerRef}
        className="relative grid grid-cols-[1fr_140px_1fr] border rounded-lg bg-white overflow-hidden"
      >
        {/* Coluna Esquerda: SEMPRE Campos Legados */}
        <div className="border-r flex flex-col max-h-[500px]">
          <div className="p-3 border-b bg-slate-50 space-y-2">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold text-slate-800">JSON Legado</h3>
              <span className="text-[10px] font-bold uppercase px-2 py-0.5 rounded bg-slate-200 text-slate-700">
                {sentido === 'LEGADO_PARA_FHIR' ? 'Origem ($.)' : 'Destino (Dot)'}
              </span>
            </div>
            <input
              type="search"
              value={buscaLegado}
              onChange={(e) => setBuscaLegado(e.target.value)}
              placeholder="Buscar campo legado..."
              className="w-full px-2.5 py-1.5 text-xs border rounded bg-white"
            />
          </div>
          <div className="overflow-y-auto p-2 space-y-1.5" onScroll={recalcularLinhas}>
            {legadoFiltrado.length === 0 ? (
              <p className="text-xs text-slate-500 p-4 text-center">
                Clique em "Carregar JSON" (modo Molde Legado) para listar os campos legados.
              </p>
            ) : (
              legadoFiltrado.map((campo) => {
                const dotPath = normalizarCaminhoLegado(campo.caminho);
                const mapeado = legadoMapeados.has(dotPath);
                const selecionado =
                  origemSelecionada?.lado === 'LEGADO' &&
                  origemSelecionada.caminho === `$.${dotPath}`;
                return (
                  <button
                    key={campo.caminho}
                    ref={(el) => {
                      legadoRefs.current[dotPath] = el;
                    }}
                    type="button"
                    onClick={() => handleCliqueLegado(dotPath)}
                    className={`w-full text-left p-2.5 rounded border text-xs transition ${
                      selecionado
                        ? 'border-blue-600 bg-blue-50 ring-1 ring-blue-600'
                        : mapeado
                        ? 'border-emerald-300 bg-emerald-50/40'
                        : 'border-slate-200 hover:bg-slate-50'
                    }`}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-mono font-semibold text-slate-800 truncate">
                        {sentido === 'LEGADO_PARA_FHIR' ? `$.${dotPath}` : dotPath}
                      </span>
                      <span className="px-1.5 py-0.5 rounded bg-slate-100 text-[10px] uppercase">
                        {campo.tipo}
                      </span>
                    </div>
                    <p className="text-slate-500 truncate mt-1">Ex: {campo.valorExemplo}</p>
                  </button>
                );
              })
            )}
          </div>
        </div>

        <div className="bg-slate-50/40 pointer-events-none" />

        {/* Coluna Direita: SEMPRE Recurso FHIR R4 */}
        <div className="border-l flex flex-col max-h-[500px]">
          <div className="p-3 border-b bg-slate-50 space-y-2">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-semibold text-slate-800">Recurso FHIR R4</h3>
              <span className="text-[10px] font-bold uppercase px-2 py-0.5 rounded bg-slate-200 text-slate-700">
                {sentido === 'FHIR_PARA_LEGADO' ? 'Origem ($.)' : 'Destino (Dot)'}
              </span>
            </div>
            <input
              type="search"
              value={buscaFhir}
              onChange={(e) => setBuscaFhir(e.target.value)}
              placeholder="Buscar campo FHIR..."
              className="w-full px-2.5 py-1.5 text-xs border rounded bg-white"
            />
          </div>
          <div className="overflow-y-auto p-2 space-y-1.5" onScroll={recalcularLinhas}>
            {fhirFiltrado.map((campo) => {
              const dotFhir = normalizarCaminhoFhirParaCatalogo(recurso, campo.caminho);
              const mapeado = fhirMapeados.has(dotFhir);
              const obrigatorio = participaDeObrigatoriedade(campo);
              const selecionado =
                origemSelecionada?.lado === 'FHIR' &&
                origemSelecionada.caminho === `$.${dotFhir}`;
              return (
                <button
                  key={campo.caminho}
                  ref={(el) => {
                    fhirRefs.current[dotFhir] = el;
                  }}
                  type="button"
                  onClick={() => handleCliqueFhir(campo)}
                  className={`w-full text-left p-2.5 rounded border text-xs transition ${
                    selecionado
                      ? 'border-blue-600 bg-blue-50 ring-1 ring-blue-600'
                      : mapeado
                      ? 'border-emerald-300 bg-emerald-50/40'
                      : obrigatorio
                      ? 'border-amber-300 bg-amber-50/40'
                      : 'border-slate-200 hover:bg-slate-50'
                  }`}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-mono font-semibold text-slate-800 truncate">
                      {sentido === 'FHIR_PARA_LEGADO' ? `$.${dotFhir}` : dotFhir}
                      {obrigatorio && <span className="text-red-600 ml-1">*</span>}
                    </span>
                    <div className="flex items-center gap-1 shrink-0">
                      {campo.origemObrigatoriedade === 'FHIR_R4' && (
                        <span className="px-1.5 py-0.5 rounded bg-red-100 text-red-800 text-[9px] uppercase font-semibold">
                          FHIR R4
                        </span>
                      )}
                      {campo.grupoObrigatorioFhir && (
                        <span className="px-1.5 py-0.5 rounded bg-orange-100 text-orange-800 text-[9px] uppercase font-semibold">
                          grupo FHIR
                        </span>
                      )}
                      {campo.origemObrigatoriedade === 'REGRA_PROJETO' && (
                        <span className="px-1.5 py-0.5 rounded bg-amber-100 text-amber-800 text-[9px] uppercase font-semibold">
                          projeto
                        </span>
                      )}
                      <span className="px-1.5 py-0.5 rounded bg-slate-100 text-[10px]">
                        {campo.tipo}
                      </span>
                    </div>
                  </div>
                  <p className="text-slate-500 mt-1">{campo.descricao}</p>
                </button>
              );
            })}
          </div>
        </div>

        {/* Overlay SVG com marcador direcional */}
        <svg className="absolute inset-0 w-full h-full pointer-events-none">
          <defs>
            <marker
              id="seta-mapeamento"
              viewBox="0 0 10 10"
              refX="8"
              refY="5"
              markerWidth="6"
              markerHeight="6"
              orient="auto-start-reverse"
            >
              <path d="M 0 1 L 10 5 L 0 9 z" fill="#475569" />
            </marker>
          </defs>
          {linhas.map((l) => {
            const cx = (l.x1 + l.x2) / 2;
            const d = `M ${l.x1} ${l.y1} C ${cx} ${l.y1}, ${cx} ${l.y2}, ${l.x2} ${l.y2}`;
            const ehDominio = l.regra.tipoConversao === 'DOMINIO';
            return (
              <g key={l.id} className="pointer-events-auto cursor-pointer group">
                <path
                  d={d}
                  fill="none"
                  stroke="transparent"
                  strokeWidth={14}
                  onClick={() => onSelecionarRegra(l.regra)}
                />
                <path
                  d={d}
                  fill="none"
                  stroke={ehDominio ? '#2563eb' : '#10b981'}
                  strokeWidth={2.5}
                  markerEnd="url(#seta-mapeamento)"
                  className="group-hover:stroke-indigo-700"
                  onClick={() => onSelecionarRegra(l.regra)}
                />
              </g>
            );
          })}
        </svg>
      </div>
    </div>
  );
};
