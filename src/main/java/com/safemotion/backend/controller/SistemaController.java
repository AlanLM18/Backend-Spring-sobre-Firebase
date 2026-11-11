package com.safemotion.backend.controller;

import com.google.cloud.firestore.Firestore;
import com.safemotion.backend.config.FirebaseConfig;
import com.safemotion.backend.service.FirestoreUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Estado del servicio (público, sin datos de nadie). Render lo usa como health check y sirve para comprobar
 * que el backend llega a Firestore. No revela cuentas ni datos clínicos.
 */
@RestController
@RequestMapping("/api/sistema")
public class SistemaController {

    private final Firestore db;
    private final String proyecto;

    public SistemaController(Firestore db, @Value("${app.firebase.project-id}") String proyecto) {
        this.db = db;
        this.proyecto = proyecto;
    }

    @GetMapping("/estado")
    public Map<String, Object> estado() {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("servicio", "safemotion-backend");
        e.put("baseDatos", "Cloud Firestore · proyecto " + proyecto);
        e.put("emulador", FirebaseConfig.usaEmulador());
        long t0 = System.nanoTime();
        boolean ok;
        try {
            FirestoreUtil.get(db.collection("usuarios").limit(1).select(new String[0]));   // solo verifica que Firestore responde
            ok = true;
        } catch (RuntimeException ex) {
            ok = false;
        }
        e.put("firestore", ok ? "disponible" : "sin respuesta");
        e.put("latenciaFirestoreMs", Math.round((System.nanoTime() - t0) / 1e5) / 10.0);
        return e;
    }
}
