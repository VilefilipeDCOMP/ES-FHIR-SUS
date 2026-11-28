import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from '../App';

afterEach(() => {
  vi.restoreAllMocks();
  localStorage.clear();
});

describe('Integração de Workspace (App.tsx)', () => {
  it('renderiza o Shell inicial com sistema-demo e aviso de dados sintéticos', async () => {
    render(<App />);

    expect(screen.getByText('FHIR SUS+')).toBeInTheDocument();
    expect(screen.getByText('sistema-demo')).toBeInTheDocument();
    expect(
      screen.getByText(/demo local, sem autenticação, dados sintéticos/i)
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { level: 1, name: 'Mapeamento De-Para' })
    ).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText(/campos obrigatórios mapeados/i)).toBeInTheDocument();
    });
  });

  it('Cenário 1: carrega configuração com dois recursos e alterna entre sentidos exibindo apenas as regras da aba', async () => {
    render(<App />);

    // Inicia em ServiceRequest -> toLegado (pois toFHIR é [] e toLegado possui 3 regras)
    await waitFor(() => {
      expect(screen.getByText(/3 conexões ativas/i)).toBeInTheDocument();
    });
    expect(screen.getByText(/numeroPedido/i)).toBeInTheDocument();

    // Alterna para Legado -> FHIR em ServiceRequest (toFHIR tem 0 regras)
    fireEvent.click(screen.getByRole('button', { name: /Legado → FHIR/i }));
    expect(screen.getByText(/0 conexões ativas/i)).toBeInTheDocument();

    // Troca para Observation em Legado -> FHIR (toFHIR tem 5 regras)
    const selectRecurso = screen.getByRole('combobox');
    fireEvent.change(selectRecurso, { target: { value: 'Observation' } });
    expect(screen.getByText(/5 conexões ativas/i)).toBeInTheDocument();
    expect(screen.getAllByText(/valueQuantity\.value/i).length).toBeGreaterThanOrEqual(1);
  });

  it('permite alternar abas entre Mapeamento e Prévia JSON', async () => {
    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/3 conexões ativas/i)).toBeInTheDocument();
    });

    const abaPrevia = screen.getByRole('button', { name: /Prévia JSON/i });
    fireEvent.click(abaPrevia);

    expect(screen.getByText(/Prévia do MapeamentoConfig/i)).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /Importar e Ativar \(UC-04\)/i })
    ).toBeInTheDocument();

    const abaCampos = screen.getByRole('button', { name: /Mapeamento de campos/i });
    fireEvent.click(abaCampos);

    expect(screen.getByText('JSON Legado')).toBeInTheDocument();
    expect(screen.getByText('Recurso FHIR R4')).toBeInTheDocument();
  });

  it('abre o modal de carregar JSON e permite selecionar exemplo', async () => {
    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/3 conexões ativas/i)).toBeInTheDocument();
    });

    const botaoCarregar = screen.getByRole('button', { name: /Carregar JSON/i });
    fireEvent.click(botaoCarregar);

    expect(screen.getByText(/Carregar JSON de origem \(UC-02\)/i)).toBeInTheDocument();

    const botaoUsarExemplo = screen.getByRole('button', { name: /Usar exemplo/i });
    fireEvent.click(botaoUsarExemplo);

    expect(screen.getByText(/JSON válido: \d+ campos simples detectados/i)).toBeInTheDocument();
  });

  it('reinicia o estado do RuleDrawer ao selecionar outra conexão e fecha ao trocar contexto', async () => {
    render(<App />);

    await waitFor(() => {
      expect(screen.getByText(/3 conexões ativas/i)).toBeInTheDocument();
    });

    // Clica na regra DIRETA ($.identifier[0].value -> numeroPedido)
    fireEvent.click(screen.getByRole('button', { name: /numeroPedido/i }));
    expect(screen.getByLabelText(/Direta \(DIRETA \/ COPIAR\)/i)).toBeChecked();

    // Com o drawer ainda aberto, clica na regra DOMINIO ($.code.coding[0].code -> codigoExame)
    fireEvent.click(screen.getByRole('button', { name: /codigoExame/i }));
    expect(screen.getByLabelText(/Tabela de códigos \(DOMINIO \/ TABELA\)/i)).toBeChecked();

    // Com o drawer ainda aberto, clica na regra CONSTANTE ("CLINICA_DEMO" -> origem)
    fireEvent.click(screen.getByRole('button', { name: /CLINICA_DEMO/i }));
    expect(screen.getByLabelText(/Constante literal \(CONSTANTE\)/i)).toBeChecked();
    expect(screen.queryByLabelText(/Caminho de origem/i)).not.toBeInTheDocument();

    // Alterna o sentido com o drawer aberto: o drawer deve fechar para não vazar contexto
    fireEvent.click(screen.getByRole('button', { name: /Legado → FHIR/i }));
    expect(screen.queryByLabelText(/Regras de transformação/i)).not.toBeInTheDocument();
  });
});
