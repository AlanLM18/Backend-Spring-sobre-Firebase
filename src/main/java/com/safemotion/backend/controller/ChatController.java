package com.safemotion.backend.controller;

import com.safemotion.backend.dto.Solicitudes;
import com.safemotion.backend.service.AtencionService;
import com.safemotion.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Chat paciente-médico. La conversación se identifica por el UID del paciente. */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AtencionService atencion;
    private final AuthService auth;

    public ChatController(AtencionService atencion, AuthService auth) {
        this.atencion = atencion;
        this.auth = auth;
    }

    @GetMapping("/conversaciones")
    public List<Map<String, Object>> conversaciones(@RequestHeader(value = "Authorization", required = false) String token) {
        return atencion.conversaciones(auth.autenticar(token));
    }

    @GetMapping("/{pacienteId}/mensajes")
    public List<Map<String, Object>> mensajes(@RequestHeader(value = "Authorization", required = false) String token,
                                              @PathVariable String pacienteId) {
        return atencion.mensajes(auth.autenticar(token), pacienteId);
    }

    @PostMapping("/{pacienteId}/mensajes")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> enviar(@RequestHeader(value = "Authorization", required = false) String token,
                                      @PathVariable String pacienteId,
                                      @Valid @RequestBody Solicitudes.Mensaje req) {
        return atencion.enviar(auth.autenticar(token), pacienteId, req.texto());
    }

    @DeleteMapping("/{pacienteId}/mensajes")
    public ResponseEntity<Void> limpiar(@RequestHeader(value = "Authorization", required = false) String token,
                                        @PathVariable String pacienteId) {
        atencion.limpiar(auth.autenticar(token), pacienteId);
        return ResponseEntity.noContent().build();
    }
}
