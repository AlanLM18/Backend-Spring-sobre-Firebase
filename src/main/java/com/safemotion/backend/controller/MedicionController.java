package com.safemotion.backend.controller;

import com.safemotion.backend.dto.Solicitudes;
import com.safemotion.backend.service.AuthService;
import com.safemotion.backend.service.MedicionService;
import com.safemotion.backend.service.Sesion;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MedicionController {

    private final MedicionService mediciones;
    private final AuthService auth;

    public MedicionController(MedicionService mediciones, AuthService auth) {
        this.mediciones = mediciones;
        this.auth = auth;
    }

    /** 201 si se guardó; 200 si esa lectura (mismo idLocal) ya estaba guardada. */
    @PostMapping("/mediciones")
    public ResponseEntity<Map<String, Object>> registrar(@RequestHeader(value = "Authorization", required = false) String token,
                                                         @Valid @RequestBody Solicitudes.Medicion req) {
        Sesion s = auth.autenticar(token);
        MedicionService.Resultado r = mediciones.registrar(s, req);
        return r.creada()
                ? ResponseEntity.created(URI.create("/api/mediciones/" + r.medicion().get("id"))).body(r.medicion())
                : ResponseEntity.ok(r.medicion());
    }

    @GetMapping("/mediciones/historial")
    public List<Map<String, Object>> historial(@RequestHeader(value = "Authorization", required = false) String token,
                                               @RequestParam(defaultValue = "20") int limite) {
        Sesion s = auth.autenticar(token);
        return mediciones.historial(s.uid(), limite);
    }

    /** 204 si el usuario aún no tiene lecturas. */
    @GetMapping("/mediciones/actual")
    public ResponseEntity<Map<String, Object>> actual(@RequestHeader(value = "Authorization", required = false) String token) {
        Map<String, Object> m = mediciones.ultima(auth.autenticar(token).uid());
        return m == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(m);
    }

    /** Doctor/admin: última lectura de un paciente. */
    @GetMapping("/pacientes/{pacienteId}/mediciones/ultima")
    public ResponseEntity<Map<String, Object>> ultimaDePaciente(@RequestHeader(value = "Authorization", required = false) String token,
                                                                @PathVariable String pacienteId) {
        Map<String, Object> m = mediciones.ultimaDePaciente(auth.autenticar(token), pacienteId);
        return m == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(m);
    }

    /** Alertas generadas a partir de las últimas 100 lecturas del usuario. */
    @GetMapping("/alertas")
    public List<Map<String, Object>> alertas(@RequestHeader(value = "Authorization", required = false) String token) {
        return mediciones.alertas(auth.autenticar(token).uid(), 100);
    }
}
