package br.ufs.fhirsus.controller;

import br.ufs.fhirsus.dto.HealthStatusDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

//Controller responsável pelo Health Check da API Gateway.

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<HealthStatusDto> healthCheck() {
        return ResponseEntity.ok(HealthStatusDto.up());
    }
}
