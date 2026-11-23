package br.ufs.fhirsus.dto;

import java.util.Map;


//Resposta de confirmação de recebimento da requisição clínica.

public record ClinicalReceptionResponseDto(
        String status,
        String mensagem,
        Map<String, String> dadosExtraidos
) {}
