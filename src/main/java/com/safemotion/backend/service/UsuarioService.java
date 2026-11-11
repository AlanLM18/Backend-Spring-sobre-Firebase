package com.safemotion.backend.service;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.safemotion.backend.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.safemotion.backend.service.FirestoreUtil.*;

/** Administración de cuentas (solo admin). Las cuentas se crean fuera de la app; aquí se habilitan o deshabilitan. */
@Service
public class UsuarioService {

    private final Firestore db;
    private final AuthService auth;

    public UsuarioService(Firestore db, AuthService auth) {
        this.db = db;
        this.auth = auth;
    }

    static Map<String, Object> dto(DocumentSnapshot d) {
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("id", d.getId());
        u.put("nombre", d.getString("nombre"));
        u.put("rol", d.getString("rol"));
        u.put("habilitado", !Boolean.FALSE.equals(d.getBoolean("habilitado")));
        u.put("ultimoInicioSesion", iso(d.get("ultimoInicioSesion")));
        return u;
    }

    public List<Map<String, Object>> listar(Sesion s) {
        if (!Reglas.ADMIN.equals(s.rol())) throw ApiException.sinPermiso();
        return get(db.collection("usuarios")).getDocuments().stream().map(UsuarioService::dto).toList();
    }

    public Map<String, Object> cambiarEstado(Sesion s, String uid, boolean habilitado) {
        if (!Reglas.ADMIN.equals(s.rol())) throw ApiException.sinPermiso();
        if (s.uid().equals(uid)) throw new ApiException(HttpStatus.BAD_REQUEST, "No puedes deshabilitar tu propia cuenta");
        DocumentReference ref = db.collection("usuarios").document(uid);
        if (!get(ref).exists()) throw ApiException.noExiste("El usuario");
        esperar(ref.update("habilitado", habilitado));
        if (!habilitado) auth.revocar(uid);   // sus tokens dejan de servir de inmediato
        return dto(get(ref));
    }
}
