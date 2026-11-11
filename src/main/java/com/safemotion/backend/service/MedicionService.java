package com.safemotion.backend.service;

import com.google.cloud.firestore.*;
import com.safemotion.backend.dto.Solicitudes;
import com.safemotion.backend.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

import static com.safemotion.backend.service.FirestoreUtil.*;

/**
 * Lecturas del wearable en usuarios/{uid}/mediciones/{idLocal} (misma ruta y campos que la PWA y el reloj).
 * El id del documento es el idLocal que genera la app: reenviar la misma lectura no crea otra (idempotencia).
 */
@Service
public class MedicionService {

    private final Firestore db;

    public MedicionService(Firestore db) {
        this.db = db;
    }

    public record Resultado(Map<String, Object> medicion, boolean creada) { }

    private CollectionReference mediciones(String uid) {
        return db.collection("usuarios").document(uid).collection("mediciones");
    }

    public Resultado registrar(Sesion s, Solicitudes.Medicion r) {
        if (!Reglas.PACIENTE.equals(s.rol()) && !Reglas.ADMIN.equals(s.rol())) throw ApiException.sinPermiso();
        DocumentReference ref = r.idLocal() != null ? mediciones(s.uid()).document(r.idLocal()) : mediciones(s.uid()).document();

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("frecuenciaCardiaca", r.frecuenciaCardiaca());
        doc.put("temperatura", r.temperatura());
        doc.put("spo2", r.spo2());   // null si el wearable no lo mide (nunca 0)
        doc.put("pasos", r.pasos());
        doc.put("calorias", r.calorias());
        doc.put("actividad", r.actividad().trim());
        doc.put("bateria", r.bateria());
        if (r.giroscopioX() != null) doc.put("giroscopioX", r.giroscopioX());
        if (r.giroscopioY() != null) doc.put("giroscopioY", r.giroscopioY());
        if (r.giroscopioZ() != null) doc.put("giroscopioZ", r.giroscopioZ());
        if (r.fuente() != null) doc.put("fuente", r.fuente());
        if (r.fechaCaptura() != null) doc.put("fechaCaptura", r.fechaCaptura());
        doc.put("idLocal", ref.getId());
        doc.put("pacienteId", s.uid());
        doc.put("alertas", Reglas.alertas(r.frecuenciaCardiaca(), r.temperatura(), r.spo2()));
        doc.put("fecha", FieldValue.serverTimestamp());

        return esperar(db.runTransaction(tx -> {
            DocumentSnapshot previa = tx.get(ref).get();
            if (previa.exists()) return new Resultado(json(previa), false);   // reintento: ya estaba guardada
            tx.set(ref, doc);
            Map<String, Object> salida = new HashMap<>(doc);
            salida.put("id", ref.getId());
            salida.put("fecha", Instant.now().toString());
            return new Resultado(salida, true);
        }));
    }

    public List<Map<String, Object>> historial(String uid, int limite) {
        if (!Reglas.limiteValido(limite)) throw new ApiException(HttpStatus.BAD_REQUEST, "limite debe estar entre 1 y 500");
        return get(mediciones(uid).orderBy("fecha", Query.Direction.DESCENDING).limit(limite))
                .getDocuments().stream().map(FirestoreUtil::json).toList();
    }

    public Map<String, Object> ultima(String uid) {
        List<QueryDocumentSnapshot> d = get(mediciones(uid).orderBy("fecha", Query.Direction.DESCENDING).limit(1)).getDocuments();
        return d.isEmpty() ? null : json(d.get(0));
    }

    /** Última lectura de un paciente: solo doctor y admin (recepción no ve datos clínicos). */
    public Map<String, Object> ultimaDePaciente(Sesion s, String pacienteId) {
        if (!Reglas.esClinico(s.rol())) throw ApiException.sinPermiso();
        return ultima(pacienteId);
    }

    /** Alertas del usuario: una por tipo y lectura, a partir de sus últimas lecturas. */
    public List<Map<String, Object>> alertas(String uid, int ultimas) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> m : historial(uid, ultimas)) {
            Object a = m.get("alertas");
            if (a instanceof List<?> lista) {
                for (Object tipo : lista) {
                    Map<String, Object> x = new LinkedHashMap<>();
                    x.put("tipo", tipo);
                    x.put("medicionId", m.get("id"));
                    x.put("fecha", m.get("fecha"));
                    x.put("frecuenciaCardiaca", m.get("frecuenciaCardiaca"));
                    x.put("temperatura", m.get("temperatura"));
                    x.put("spo2", m.get("spo2"));
                    out.add(x);
                }
            }
        }
        return out;
    }
}
