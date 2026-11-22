CREATE TABLE config_versao (
    id          BIGSERIAL PRIMARY KEY,
    versao      INTEGER      NOT NULL UNIQUE,
    config_id   VARCHAR(100),
    conteudo    TEXT         NOT NULL,
    ativa       BOOLEAN      NOT NULL,
    validada    BOOLEAN      NOT NULL,
    criado_em   TIMESTAMP    NOT NULL,
    criado_por  VARCHAR(100) NOT NULL
);

CREATE UNIQUE INDEX ux_config_versao_ativa ON config_versao (ativa) WHERE ativa;

CREATE TABLE auditoria_mapeamento (
    id              BIGSERIAL PRIMARY KEY,
    datahora        TIMESTAMP    NOT NULL,
    versao_atual    INTEGER      NOT NULL REFERENCES config_versao (versao),
    versao_anterior INTEGER      REFERENCES config_versao (versao),
    responsavel     VARCHAR(100) NOT NULL
);
