package br.ufs.fhirsus.config.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Versão salva de uma MapeamentoConfig. Só entram aqui configs validadas. */
@Entity
@Table(name = "config_versao")
public class ConfigVersaoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer versao;

    /** MapeamentoConfig.id vindo do arquivo (opcional). */
    @Column(name = "config_id", length = 100)
    private String configId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String conteudo;

    @Column(nullable = false)
    private boolean ativa;

    /** MapeamentoConfig.validado: sempre true, pois config inválida nunca é salva. */
    @Column(nullable = false)
    private boolean validada;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "criado_por", nullable = false, length = 100)
    private String criadoPor;

    protected ConfigVersaoEntity() {
    }

    public ConfigVersaoEntity(Integer versao, String configId, String conteudo,
                              boolean ativa, boolean validada, LocalDateTime criadoEm, String criadoPor) {
        this.versao = versao;
        this.configId = configId;
        this.conteudo = conteudo;
        this.ativa = ativa;
        this.validada = validada;
        this.criadoEm = criadoEm;
        this.criadoPor = criadoPor;
    }

    public Long getId() { return id; }
    public Integer getVersao() { return versao; }
    public String getConfigId() { return configId; }
    public String getConteudo() { return conteudo; }
    public boolean isAtiva() { return ativa; }
    public void setAtiva(boolean ativa) { this.ativa = ativa; }
    public boolean isValidada() { return validada; }
    public LocalDateTime getCriadoEm() { return criadoEm; }
    public String getCriadoPor() { return criadoPor; }
}
