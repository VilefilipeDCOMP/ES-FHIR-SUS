package br.ufs.fhirsus.config.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Entity
@Table(name = "config_versao")
public class ConfigVersaoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer versao;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String conteudo;

    @Setter
    @Column(nullable = false)
    private boolean ativa;

    @Column(nullable = false)
    private boolean validada;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "criado_por", nullable = false, length = 100)
    private String criadoPor;

    public ConfigVersaoEntity(Integer versao, String configId, String conteudo, boolean ativa, boolean validada, LocalDateTime criadoEm, String criadoPor) {
        this.versao = versao;
        this.configId = configId;
        this.conteudo = conteudo;
        this.ativa = ativa;
        this.validada = validada;
        this.criadoEm = criadoEm;
        this.criadoPor = criadoPor;
    }
}
