package com.joao.rinha.controller;

import ch.qos.logback.core.net.SyslogOutputStream;
import com.joao.rinha.dto.PaymentDTO;
import com.joao.rinha.dto.PaymentResponseDTO;
import com.joao.rinha.services.FraudService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/fraud-score")
public class FraudController {

    private FraudService service;

    public FraudController(FraudService service) {
        this.service = service;
    }

    @GetMapping("/ready")
    public ResponseEntity getStatusApplication(){
        //TODO: Iplemenat logica para saber se aplicação esta pronta para processar novamente
        return ResponseEntity.ok().build();
    }
    @PostMapping
    public PaymentResponseDTO verifyFraud(@RequestBody PaymentDTO dto){
        return service.evaluateTransaction(dto);
    }
}
