package com.safemotion.backend.controller;

import com.safemotion.backend.dto.Solicitudes;
import com.safemotion.backend.service.AuthService;
import com.safemotion.backend.service.Sesion;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Inicio de sesión con el PIN de 4 dígitos sobre Firebase Authentication. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /** 200 {token, expiraEnSegundos, usuario{id,nombre,rol}} · 400 PIN mal formado · 401 PIN incorrecto · 403 deshabilitada. */
    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody Solicitudes.Login req) {
        AuthService.Login r = auth.login(req.pin());
        return Map.of("token", r.token(), "expiraEnSegundos", r.expiraEnSegundos(), "usuario", usuario(r.usuario()));
    }

    /** Revoca las sesiones del usuario en Firebase: el token deja de servir. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        auth.logout(token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestHeader(value = "Authorization", required = false) String token) {
        return usuario(auth.autenticar(token));
    }

    static Map<String, Object> usuario(Sesion s) {
        return Map.of("id", s.uid(), "nombre", s.nombre() == null ? "" : s.nombre(), "rol", s.rol() == null ? "" : s.rol());
    }
}
