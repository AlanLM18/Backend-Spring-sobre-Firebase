package com.safemotion.backend.controller;

import com.safemotion.backend.dto.Solicitudes;
import com.safemotion.backend.service.AtencionService;
import com.safemotion.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** SOS: el paciente lo crea, recepción lo canaliza a un doctor y el doctor lo atiende. */
@RestController
@RequestMapping("/api/emergencias")
public class EmergenciaController {

    private final AtencionService atencion;
    private final AuthService auth;

    public EmergenciaController(AtencionService atencion, AuthService auth) {
        this.atencion = atencion;
        this.auth = auth;
    }

    /** 201 si se creó; 200 con duplicada=true si el paciente ya tenía uno activo. */
    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@RequestHeader(value = "Authorization", required = false) String token,
                                                     @Valid @RequestBody Solicitudes.Emergencia req) {
        AtencionService.ResultadoSos r = atencion.crearEmergencia(auth.autenticar(token), req);
        Map<String, Object> body = new LinkedHashMap<>(r.emergencia());
        body.put("duplicada", r.duplicada());
        return ResponseEntity.status(r.duplicada() ? 200 : 201).body(body);
    }

    @GetMapping
    public List<Map<String, Object>> listar(@RequestHeader(value = "Authorization", required = false) String token,
                                           @RequestParam(defaultValue = "50") int limite) {
        return atencion.emergencias(auth.autenticar(token), limite);
    }

    @GetMapping("/doctores")
    public List<Map<String, Object>> doctores(@RequestHeader(value = "Authorization", required = false) String token) {
        return atencion.doctores(auth.autenticar(token));
    }

    /** 409 con {"actual": ...} si otra recepcionista ya la canalizó. */
    @PatchMapping("/{id}/canalizar")
    public Map<String, Object> canalizar(@RequestHeader(value = "Authorization", required = false) String token,
                                         @PathVariable String id, @Valid @RequestBody Solicitudes.Canalizar req) {
        return atencion.canalizar(auth.autenticar(token), id, req.doctorId());
    }

    @PatchMapping("/{id}/atender")
    public Map<String, Object> atender(@RequestHeader(value = "Authorization", required = false) String token,
                                       @PathVariable String id) {
        return atencion.atender(auth.autenticar(token), id);
    }
}
