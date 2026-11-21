package br.ufs.fhirsus.config.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Validação semântica da configuração. Retorna a lista de erros (vazia = válida). */
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
        if (c.mapeamentos() == null || c.mapeamentos().isEmpty()) {
            erros.add(new ErroValidacao("mapeamentos", "deve ter ao menos 1 mapeamento"));
            return erros;
        }
        Set<String> recursosVistos = new HashSet<>();
        for (int i = 0; i < c.mapeamentos().size(); i++) {
            validarMapeamento(c.mapeamentos().get(i), "mapeamentos[" + i + "]", recursosVistos, dominios, erros);
        }
        return erros;
    }

    private Set<String> validarDominios(MapeamentoConfig c, List<ErroValidacao> erros) {
        Set<String> nomes = new HashSet<>();
        if (c.dominiosValores() == null) {
            return nomes;
        }
        for (int i = 0; i < c.dominiosValores().size(); i++) {
            DominioValores d = c.dominiosValores().get(i);
            String p = "dominiosValores[" + i + "]";
            if (d == null || vazio(d.nome())) {
                erros.add(new ErroValidacao(p + ".nome", "obrigatório"));
                continue;
            }
            if (!nomes.add(d.nome())) {
                erros.add(new ErroValidacao(p + ".nome", "duplicado: " + d.nome()));
            }
            if (d.equivalencia() == null || d.equivalencia().isEmpty()) {
                erros.add(new ErroValidacao(p + ".equivalencia", "deve ter ao menos 1 entrada"));
            }
        }
        return nomes;
    }

    private void validarMapeamento(Mapeamento m, String p, Set<String> vistos, Set<String> dominios, List<ErroValidacao> erros) {
        if (m == null || vazio(m.recurso()) || !RECURSOS.contains(m.recurso())) {
            erros.add(new ErroValidacao(p + ".recurso", "deve ser um de " + new TreeSet<>(RECURSOS)));
            return;
        }
        if (!vistos.add(m.recurso())) {
            erros.add(new ErroValidacao(p + ".recurso", "recurso duplicado: " + m.recurso()));
        }
        List<RegraCampo> toLegado = m.regrasToLegado() == null ? List.of() : m.regrasToLegado();
        List<RegraCampo> toFhir = m.regrasToFHIR() == null ? List.of() : m.regrasToFHIR();
        if (toLegado.isEmpty() && toFhir.isEmpty()) {
            erros.add(new ErroValidacao(p, "deve ter ao menos 1 regra em regrasToLegado ou regrasToFHIR"));
            return;
        }
        validarRegras(m.recurso(), toFhir, p + ".regrasToFHIR", true, dominios, erros);
        validarRegras(m.recurso(), toLegado, p + ".regrasToLegado", false, dominios, erros);
    }

    private void validarRegras(String recurso, List<RegraCampo> regras, String p, boolean paraFhir,
                               Set<String> dominios, List<ErroValidacao> erros) {
        Set<String> destinos = new HashSet<>();
        for (int i = 0; i < regras.size(); i++) {
            RegraCampo r = regras.get(i);
            String rp = p + "[" + i + "]";
            if (r == null) {
                erros.add(new ErroValidacao(rp, "regra nula"));
                continue;
            }
            TipoConversao tipo = r.tipoConversao();
            if (tipo == null) {
                erros.add(new ErroValidacao(rp + ".tipoConversao", "obrigatório (" + java.util.Arrays.toString(TipoConversao.values()) + ")"));
            }
            if (tipo != TipoConversao.CONSTANTE && vazio(r.caminhoOrigem())) {
                erros.add(new ErroValidacao(rp + ".caminhoOrigem", "obrigatório (exceto em CONSTANTE)"));
            }
            if (vazio(r.caminhoDestino())) {
                erros.add(new ErroValidacao(rp + ".caminhoDestino", "obrigatório"));
            } else if (!destinos.add(r.caminhoDestino())) {
                erros.add(new ErroValidacao(rp + ".caminhoDestino", "destino duplicado no mesmo sentido: " + r.caminhoDestino()));
            }
            // Caminho do lado FHIR deve começar pelo nome do recurso (validação só por prefixo: ver TD-09)
            String caminhoFhir = paraFhir ? r.caminhoDestino() : r.caminhoOrigem();
            String campoFhir = paraFhir ? ".caminhoDestino" : ".caminhoOrigem";
            if (!vazio(caminhoFhir) && !caminhoFhir.startsWith(recurso + ".")) {
                erros.add(new ErroValidacao(rp + campoFhir, "caminho FHIR deve começar com \"" + recurso + ".\""));
            }
            if (tipo != null) {
                validarParametro(tipo, r.parametroConversao(), rp, dominios, erros);
            }
        }
    }

    private void validarParametro(TipoConversao tipo, String parametro, String rp, Set<String> dominios, List<ErroValidacao> erros) {
        String campo = rp + ".parametroConversao";
        switch (tipo) {
            case DIRETA -> {
                if (!vazio(parametro)) {
                    erros.add(new ErroValidacao(campo, "deve ficar vazio em DIRETA"));
                }
            }
            case DOMINIO -> {
                if (vazio(parametro)) {
                    erros.add(new ErroValidacao(campo, "obrigatório em DOMINIO (nome do domínio)"));
                } else if (!dominios.contains(parametro)) {
                    erros.add(new ErroValidacao(campo, "domínio inexistente: " + parametro));
                }
            }
            case CONSTANTE, TRANSFORMACAO, EXPRESSAO -> {
                if (vazio(parametro)) {
                    erros.add(new ErroValidacao(campo, "obrigatório em " + tipo));
                }
            }
        }
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
