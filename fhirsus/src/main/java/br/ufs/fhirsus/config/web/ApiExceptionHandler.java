package br.ufs.fhirsus.config.web;

import br.ufs.fhirsus.config.domain.ConfigInvalidaException;
import br.ufs.fhirsus.config.domain.ConfigSintaxeException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ConfigSintaxeException.class)
    public ResponseEntity<Map<String, Object>> sintaxe(ConfigSintaxeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("erro", "CONFIG_SINTAXE", "mensagem", e.getMessage()));
    }

    @ExceptionHandler(ConfigInvalidaException.class)
    public ResponseEntity<Map<String, Object>> invalida(ConfigInvalidaException e) {
        List<Map<String, String>> detalhes = e.getErros().stream()
                .map(x -> Map.of("caminho", x.caminho(), "mensagem", x.mensagem())).toList();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("erro", "CONFIG_INVALIDA", "mensagem", e.getMessage(), "detalhes", detalhes));
    }

    /** Qualquer outra falha (inclui falha de auditoria): a transação já fez rollback. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> interno(Exception e) {
        log.error("Falha inesperada", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("erro", "ERRO_INTERNO", "mensagem", "Operação não concluída; nada foi salvo"));
    }
}
