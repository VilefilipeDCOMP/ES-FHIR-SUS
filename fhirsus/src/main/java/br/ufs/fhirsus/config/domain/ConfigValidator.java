package br.ufs.fhirsus.config.domain;

import br.ufs.fhirsus.map.DominioValores;
import br.ufs.fhirsus.map.Mapeamento;
import br.ufs.fhirsus.map.MapeamentoConfig;
import br.ufs.fhirsus.map.RegraCampo;
import br.ufs.fhirsus.map.TipoConversao;
import br.ufs.fhirsus.map.ValidacaoCaminhos;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class ConfigValidator {

    public static final Set<String> RECURSOS = Set.of(
            "ServiceRequest", "Patient", "Practitioner", "PractitionerRole",
            "Organization", "DiagnosticReport", "Observation", "Specimen");

    public List<ErroValidacao> validar(MapeamentoConfig c) {
        List<ErroValidacao> erros = new ArrayList<>();
        if (c == null) {
            erros.add(new ErroValidacao("$", "configuração ausente"));
            return erros;
        }
        Set<String> dominios = validarDominios(c, erros);
        if (c.mapeamentos().isEmpty()) {
            erros.add(new ErroValidacao("mapeamentos", "deve ter ao menos 1 mapeamento"));
            return erros;
        }
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < c.mapeamentos().size(); i++) {
            validarMapeamento(c.mapeamentos().get(i), "mapeamentos[" + i + "]", vistos, dominios, erros);
        }
        return erros;
    }

    private Set<String> validarDominios(MapeamentoConfig c, List<ErroValidacao> erros) {
        Set<String> nomes = new HashSet<>();
        for (int i = 0; i < c.dominios().size(); i++) {
            DominioValores d = c.dominios().get(i);
            String p = "dominios[" + i + "]";
            if (vazio(d.nome())) {
                erros.add(new ErroValidacao(p + ".nome", "obrigatório"));
                continue;
            }
            if (!nomes.add(d.nome())) {
                erros.add(new ErroValidacao(p + ".nome", "duplicado: " + d.nome()));
            }
            if (d.equivalencia().isEmpty()) {
                erros.add(new ErroValidacao(p + ".equivalencia", "deve ter ao menos 1 entrada"));
            }
        }
        return nomes;
    }

    private void validarMapeamento(Mapeamento m, String p, Set<String> vistos, Set<String> dominios,
                                   List<ErroValidacao> erros) {
        if (vazio(m.recurso()) || !RECURSOS.contains(m.recurso())) {
            erros.add(new ErroValidacao(p + ".recurso", "deve ser um de " + new TreeSet<>(RECURSOS)));
            return;
        }
        if (!vistos.add(m.recurso())) {
            erros.add(new ErroValidacao(p + ".recurso", "recurso duplicado: " + m.recurso()));
        }
        if (m.toLegado().isEmpty() && m.toFHIR().isEmpty()) {
            erros.add(new ErroValidacao(p, "deve ter ao menos 1 regra em toLegado ou toFHIR"));
            return;
        }
        validarRegras(m.toLegado(), p + ".toLegado", dominios, erros);
        validarRegras(m.toFHIR(), p + ".toFHIR", dominios, erros);
    }

    private void validarRegras(List<RegraCampo> regras, String p, Set<String> dominios,
                               List<ErroValidacao> erros) {
        for (int i = 0; i < regras.size(); i++) {
            validarRegra(regras.get(i), p + "[" + i + "]", dominios, erros);
        }
        ValidacaoCaminhos.errosDestino(regras.stream().map(RegraCampo::getCaminhoDestino).toList())
                .forEach((i, msg) -> erros.add(new ErroValidacao(
                        i < 0 ? p : p + "[" + i + "].caminhoDestino", msg)));
    }

    private void validarRegra(RegraCampo r, String rp, Set<String> dominios, List<ErroValidacao> erros) {
        TipoConversao tipo = r.getTipoConversao();
        if (tipo == null) {
            erros.add(new ErroValidacao(rp + ".tipoConversao",
                    "obrigatório (" + Arrays.toString(TipoConversao.values()) + ")"));
        }
        if (r.getObrigatorio() == null) {
            erros.add(new ErroValidacao(rp + ".obrigatorio", "obrigatório (true ou false)"));
        }
        if (vazio(r.getCaminhoDestino())) {
            erros.add(new ErroValidacao(rp + ".caminhoDestino", "obrigatório"));
        }
        if (tipo != TipoConversao.CONSTANTE) {
            if (vazio(r.getCaminhoOrigem())) {
                erros.add(new ErroValidacao(rp + ".caminhoOrigem", "obrigatório (exceto em CONSTANTE)"));
            } else {
                String erro = ValidacaoCaminhos.erroOrigem(r.getCaminhoOrigem());
                if (erro != null) {
                    erros.add(new ErroValidacao(rp + ".caminhoOrigem", erro));
                }
            }
        }
        if (tipo != null) {
            validarParametro(tipo, r.getParametroConversao(), rp, dominios, erros);
        }
    }

    private void validarParametro(TipoConversao tipo, String parametro, String rp, Set<String> dominios,
                                  List<ErroValidacao> erros) {
        String campo = rp + ".parametroConversao";
        switch (tipo) {
            case DIRETA -> {
                if (parametro != null) {
                    erros.add(new ErroValidacao(campo, "deve ser null em DIRETA"));
                }
            }
            case DOMINIO -> {
                if (vazio(parametro)) {
                    erros.add(new ErroValidacao(campo, "obrigatório em DOMINIO (nome do domínio)"));
                } else if (!dominios.contains(parametro)) {
                    erros.add(new ErroValidacao(campo, "domínio inexistente: " + parametro));
                }
            }
            case CONSTANTE -> {
                if (vazio(parametro)) {
                    erros.add(new ErroValidacao(campo, "obrigatório em CONSTANTE"));
                }
            }
            case TRANSFORMACAO, EXPRESSAO -> erros.add(new ErroValidacao(rp + ".tipoConversao",
                    "fora do recorte do P3: use DIRETA, DOMINIO ou CONSTANTE"));
        }
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}