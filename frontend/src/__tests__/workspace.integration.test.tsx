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

    // aguarda carregar configuração inicial
    await waitFor(() => {
      expect(screen.getByText(/campos obrigatórios mapeados/i)).toBeInTheDocument();
    });
  });

  it('permite alternar abas entre Mapeamento e Prévia JSON', async () => {
    render(<App />);

    const abaPrevia = screen.getByRole('button', { name: /Prévia JSON/i });
    fireEvent.click(abaPrevia);

    expect(
      screen.getByText(/Prévia do MapeamentoConfig/i)
    ).toBeInTheDocument();
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

    const botaoCarregar = screen.getByRole('button', { name: /Carregar JSON/i });
    fireEvent.click(botaoCarregar);

    expect(screen.getByText(/Carregar JSON de origem \(UC-02\)/i)).toBeInTheDocument();

    const botaoUsarExemplo = screen.getByRole('button', { name: /Usar exemplo/i });
    fireEvent.click(botaoUsarExemplo);

    expect(screen.getByText(/JSON válido: \d+ campos simples detectados/i)).toBeInTheDocument();
  });
});
