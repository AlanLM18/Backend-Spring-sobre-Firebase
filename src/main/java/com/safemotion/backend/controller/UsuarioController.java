package com.safemotion.backend.controller;

import com.safemotion.backend.dto.Solicitudes;
import com.safemotion.backend.service.AuthService;
import com.safemotion.backend.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Administración de cuentas: solo el rol admin (401 sin sesión, 403 con otro rol). */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarios;
    private final AuthService auth;

    public UsuarioController(UsuarioService usuarios, AuthService auth) {
        this.usuarios = usuarios;
        this.auth = auth;
    }

    @GetMapping
    public List<Map<String, Object>> listar(@RequestHeader(value = "Authorization", required = false) String token) {
        return usuarios.listar(auth.autenticar(token));
    }

    @PatchMapping("/{id}/estado")
    public Map<String, Object> cambiarEstado(@RequestHeader(value = "Authorization", required = false) String token,
                                             @PathVariable String id, @Valid @RequestBody Solicitudes.EstadoUsuario req) {
        return usuarios.cambiarEstado(auth.autenticar(token), id, req.habilitado());
    }
}
