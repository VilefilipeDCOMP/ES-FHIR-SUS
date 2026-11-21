package br.ufs.fhirsus.config.domain;

/** Como o valor da regra é obtido (diagrama de classes: RegraCampo.tipoConversao). */
public enum TipoConversao {
    DIRETA,         // copia o valor; sem parâmetro
    CONSTANTE,      // valor fixo; parâmetro = o valor; origem opcional
    DOMINIO,        // tabela de equivalência; parâmetro = nome do DominioValores
    TRANSFORMACAO,  // função do motor; parâmetro = nome da função
    EXPRESSAO       // expressão; parâmetro = a expressão
}
