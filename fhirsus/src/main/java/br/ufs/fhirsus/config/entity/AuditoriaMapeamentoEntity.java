package br.ufs.fhirsus.config.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    public AuditoriaMapeamentoEntity(LocalDateTime datahora, Integer versaoAtual,
                                     Integer versaoAnterior, String responsavel) {
        this.datahora = datahora;
        this.versaoAtual = versaoAtual;
        this.versaoAnterior = versaoAnterior;
        this.responsavel = responsavel;
    }
}
