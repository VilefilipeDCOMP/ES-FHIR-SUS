package br.ufs.fhirsus.config.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Diagrama: RegistroAuditoriaMapeamento (id, datahora, versaoAtual, versaoAnterior, responsavel).
 * As versões são números de config_versao.versao. "responsavel" é texto fixo até existir Usuário (P4).
 */
@Entity
@Table(name = "auditoria_mapeamento")
public class AuditoriaMapeamentoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime datahora;

    @Column(name = "versao_atual", nullable = false)
    private Integer versaoAtual;

    @Column(name = "versao_anterior")
    private Integer versaoAnterior;

    @Column(nullable = false, length = 100)
    private String responsavel;

    protected AuditoriaMapeamentoEntity() {
    }

    public AuditoriaMapeamentoEntity(LocalDateTime datahora, Integer versaoAtual, Integer versaoAnterior, String responsavel) {
        this.datahora = datahora;
        this.versaoAtual = versaoAtual;
        this.versaoAnterior = versaoAnterior;
        this.responsavel = responsavel;
    }

    public Long getId() { return id; }
    public LocalDateTime getDatahora() { return datahora; }
    public Integer getVersaoAtual() { return versaoAtual; }
    public Integer getVersaoAnterior() { return versaoAnterior; }
    public String getResponsavel() { return responsavel; }
}
