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
 * Chat paciente-médico (conversaciones/{pacienteUid}/mensajes) y SOS (emergencias/{id}), con las mismas colecciones,
 * campos y estados que usa la PWA: ABIERTA (recepción) -> CANALIZADA (doctor) -> ATENDIDA.
 * Toda escritura que depende de lo que ya existe va en una transacción de Firestore, así dos peticiones simultáneas
 * no pueden crear dos SOS abiertos ni atender dos veces la misma emergencia.
 */
@Service
public class AtencionService {

    private final Firestore db;

    public AtencionService(Firestore db) {
        this.db = db;
    }

    // ------------------------------------------------------------------ chat
    private CollectionReference mensajes(String pacienteId) {
        return db.collection("conversaciones").document(pacienteId).collection("mensajes");
    }

    private Map<String, Object> mensaje(Sesion autor, String texto) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("autorId", autor.uid());
        m.put("autor", autor.nombre());
        m.put("rol", autor.rol());
        m.put("texto", texto);
        m.put("fecha", FieldValue.serverTimestamp());
        return m;
    }

    static Map<String, Object> dtoMensaje(DocumentSnapshot d, String pacienteId) {
        Map<String, Object> m = json(d);
        m.put("pacienteId", pacienteId);
        return m;
    }

    private DocumentSnapshot pacienteExistente(String pacienteId) {
        DocumentSnapshot p = get(db.collection("usuarios").document(pacienteId));
        if (!p.exists() || !Reglas.PACIENTE.equals(p.getString("rol"))) throw ApiException.noExiste("El paciente");
        return p;
    }

    public List<Map<String, Object>> mensajes(Sesion s, String pacienteId) {
        if (!Reglas.puedeVerChat(s, pacienteId)) throw ApiException.sinPermiso();
        pacienteExistente(pacienteId);
        return get(mensajes(pacienteId).orderBy("fecha")).getDocuments().stream().map(d -> dtoMensaje(d, pacienteId)).toList();
    }

    public Map<String, Object> enviar(Sesion s, String pacienteId, String texto) {
        if (!Reglas.puedeVerChat(s, pacienteId)) throw ApiException.sinPermiso();
        pacienteExistente(pacienteId);
        DocumentReference ref = mensajes(pacienteId).document();
        Map<String, Object> m = mensaje(s, texto.trim());
        esperar(ref.set(m));
        Map<String, Object> out = new LinkedHashMap<>(m);
        out.put("id", ref.getId());
        out.put("pacienteId", pacienteId);
        out.put("fecha", Instant.now().toString());
        return out;
    }

    /** Borra la conversación en lotes (WriteBatch admite hasta 500 operaciones). */
    public void limpiar(Sesion s, String pacienteId) {
        if (!Reglas.puedeVerChat(s, pacienteId)) throw ApiException.sinPermiso();
        pacienteExistente(pacienteId);
        List<QueryDocumentSnapshot> docs = get(mensajes(pacienteId)).getDocuments();
        for (int i = 0; i < docs.size(); i += 400) {
            WriteBatch b = db.batch();
            docs.subList(i, Math.min(i + 400, docs.size())).forEach(d -> b.delete(d.getReference()));
            esperar(b.commit());
        }
    }

    /** Bandeja del doctor/admin: un renglón por paciente; primero quien tiene SOS activo, luego por actividad reciente. */
    public List<Map<String, Object>> conversaciones(Sesion s) {
        if (!Reglas.esClinico(s.rol())) throw ApiException.sinPermiso();
        List<QueryDocumentSnapshot> pacientes = get(db.collection("usuarios").whereEqualTo("rol", Reglas.PACIENTE)).getDocuments();
        List<Map<String, Object>> activas = get(db.collection("emergencias").whereIn("estado", List.of(Reglas.ABIERTA, Reglas.CANALIZADA)))
                .getDocuments().stream().map(FirestoreUtil::json).toList();
        List<Map<String, Object>> lista = new ArrayList<>();
        for (QueryDocumentSnapshot p : pacientes) {
            if (Boolean.FALSE.equals(p.getBoolean("habilitado"))) continue;
            List<QueryDocumentSnapshot> ultimo = get(mensajes(p.getId()).orderBy("fecha", Query.Direction.DESCENDING).limit(1)).getDocuments();
            long total = esperar(mensajes(p.getId()).count().get()).getCount();   // agregación: no descarga los mensajes
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("pacienteId", p.getId());
            c.put("paciente", p.getString("nombre"));
            c.put("mensajes", total);
            c.put("ultimoMensaje", ultimo.isEmpty() ? null : ultimo.get(0).getString("texto"));
            c.put("fecha", ultimo.isEmpty() ? null : iso(ultimo.get(0).get("fecha")));
            c.put("emergenciaAbierta", activas.stream().filter(e -> p.getId().equals(e.get("pacienteId"))).findFirst().orElse(null));
            lista.add(c);
        }
        lista.sort(Comparator.comparing((Map<String, Object> c) -> c.get("emergenciaAbierta") == null)
                .thenComparing(c -> (String) c.get("fecha"), Comparator.nullsLast(Comparator.reverseOrder())));
        return lista;
    }

    // ------------------------------------------------------------------ SOS
    public record ResultadoSos(Map<String, Object> emergencia, boolean duplicada) { }

    /** El paciente pide ayuda. Si ya tiene un SOS activo se devuelve ese (no se duplica, ni con peticiones simultáneas). */
    public ResultadoSos crearEmergencia(Sesion s, Solicitudes.Emergencia r) {
        if (!Reglas.PACIENTE.equals(s.rol())) throw new ApiException(HttpStatus.FORBIDDEN, "Solo los pacientes piden SOS");
        Query propias = db.collection("emergencias").whereEqualTo("pacienteId", s.uid());
        DocumentReference ref = db.collection("emergencias").document();
        return esperar(db.runTransaction(tx -> {
            // La consulta dentro de la transacción bloquea el resultado: otra transacción que intente lo mismo se reintenta
            for (QueryDocumentSnapshot d : tx.get(propias).get().getDocuments()) {
                if (Reglas.activa(d.getString("estado"))) return new ResultadoSos(json(d), true);
            }
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("pacienteId", s.uid());
            e.put("paciente", s.nombre());
            e.put("fecha", FieldValue.serverTimestamp());
            e.put("latitud", r.latitud());
            e.put("longitud", r.longitud());
            e.put("frecuenciaCardiaca", r.frecuenciaCardiaca());
            e.put("spo2", r.spo2());
            e.put("temperatura", r.temperatura());
            e.put("estado", Reglas.ABIERTA);
            e.put("version", 0);
            tx.set(ref, e);
            tx.set(mensajes(s.uid()).document(), mensaje(s, Reglas.mensajeSos(r.frecuenciaCardiaca(), r.spo2(), r.temperatura())));
            Map<String, Object> out = new LinkedHashMap<>(e);
            out.put("id", ref.getId());
            out.put("fecha", Instant.now().toString());
            return new ResultadoSos(out, false);
        }));
    }

    /** Paciente: las suyas. Recepción, doctor y admin: todas. Lo más reciente primero. */
    /**
     * Paciente: sus SOS. Recepción, doctor y admin: TODOS los SOS activos (ABIERTA/CANALIZADA, que nunca se pueden
     * ocultar) más los `limite` más recientes del historial. Optimización (Práctica 12): antes se descargaba la
     * colección completa en cada consulta; ahora son dos consultas acotadas con índices de un solo campo (estado y
     * fecha), sin índice compuesto.
     */
    public List<Map<String, Object>> emergencias(Sesion s, int limite) {
        if (limite < 1 || limite > 200) throw new ApiException(HttpStatus.BAD_REQUEST, "limite debe estar entre 1 y 200");
        Map<String, Map<String, Object>> porId = new LinkedHashMap<>();
        if (Reglas.esPersonal(s.rol())) {
            get(db.collection("emergencias").whereIn("estado", List.of(Reglas.ABIERTA, Reglas.CANALIZADA)))
                    .getDocuments().forEach(d -> porId.put(d.getId(), json(d)));
            get(db.collection("emergencias").orderBy("fecha", Query.Direction.DESCENDING).limit(limite))
                    .getDocuments().forEach(d -> porId.putIfAbsent(d.getId(), json(d)));
        } else {
            get(db.collection("emergencias").whereEqualTo("pacienteId", s.uid()))
                    .getDocuments().forEach(d -> porId.put(d.getId(), json(d)));
        }
        List<Map<String, Object>> l = new ArrayList<>(porId.values());
        l.sort(Comparator.comparing((Map<String, Object> e) -> String.valueOf(e.get("fecha"))).reversed());
        return l;
    }

    public List<Map<String, Object>> doctores(Sesion s) {
        if (!Reglas.esPersonal(s.rol())) throw ApiException.sinPermiso();
        return get(db.collection("usuarios").whereEqualTo("rol", Reglas.DOCTOR)).getDocuments().stream()
                .filter(d -> !Boolean.FALSE.equals(d.getBoolean("habilitado")))
                .map(d -> Map.<String, Object>of("id", d.getId(), "nombre", Objects.toString(d.getString("nombre"), "")))
                .toList();
    }

    private static long version(DocumentSnapshot d) {
        Long v = d.getLong("version");
        return v == null ? 0 : v;
    }

    /** Recepción canaliza un SOS ABIERTO a un doctor. Si otra recepcionista ganó, 409 con cómo quedó. */
    public Map<String, Object> canalizar(Sesion s, String id, String doctorId) {
        if (!Reglas.RECEPCION.equals(s.rol()) && !Reglas.ADMIN.equals(s.rol())) throw ApiException.sinPermiso();
        DocumentReference ref = db.collection("emergencias").document(id);
        DocumentReference refDoctor = db.collection("usuarios").document(doctorId);
        return esperar(db.runTransaction(tx -> {
            DocumentSnapshot e = tx.get(ref).get();
            if (!e.exists()) throw ApiException.noExiste("La emergencia");
            if (!Reglas.ABIERTA.equals(e.getString("estado"))) throw new ApiException(HttpStatus.CONFLICT, "La emergencia ya no está abierta", json(e));
            DocumentSnapshot d = tx.get(refDoctor).get();
            if (!d.exists() || !Reglas.DOCTOR.equals(d.getString("rol")) || Boolean.FALSE.equals(d.getBoolean("habilitado")))
                throw new ApiException(HttpStatus.BAD_REQUEST, "Elige un doctor válido");
            Map<String, Object> cambio = new LinkedHashMap<>();
            cambio.put("estado", Reglas.CANALIZADA);
            cambio.put("canalizadaA", d.getId());
            cambio.put("canalizadaANombre", d.getString("nombre"));
            cambio.put("canalizadaPor", s.uid());
            cambio.put("canalizadaPorNombre", s.nombre());
            cambio.put("fechaCanalizacion", FieldValue.serverTimestamp());
            cambio.put("version", version(e) + 1);
            tx.update(ref, cambio);
            tx.set(mensajes(e.getString("pacienteId")).document(), mensaje(s, Reglas.mensajeCanalizada(d.getString("nombre"))));
            Map<String, Object> out = json(e);
            out.putAll(cambio);
            out.put("fechaCanalizacion", Instant.now().toString());
            return out;
        }));
    }

    /** El doctor al que se canalizó (o el admin) marca el SOS como atendido. Repetirlo no cambia nada ni avisa dos veces. */
    public Map<String, Object> atender(Sesion s, String id) {
        if (!Reglas.esClinico(s.rol())) throw ApiException.sinPermiso();
        DocumentReference ref = db.collection("emergencias").document(id);
        return esperar(db.runTransaction(tx -> {
            DocumentSnapshot e = tx.get(ref).get();
            if (!e.exists()) throw ApiException.noExiste("La emergencia");
            String estado = e.getString("estado");
            if (Reglas.ATENDIDA.equals(estado)) return json(e);
            if (!Reglas.CANALIZADA.equals(estado)) throw new ApiException(HttpStatus.CONFLICT, "Recepción aún no canaliza esta emergencia", json(e));
            if (!Reglas.ADMIN.equals(s.rol()) && !s.uid().equals(e.getString("canalizadaA")))
                throw new ApiException(HttpStatus.FORBIDDEN, "Esta emergencia está asignada a otro doctor");
            Map<String, Object> cambio = new LinkedHashMap<>();
            cambio.put("estado", Reglas.ATENDIDA);
            cambio.put("atendidaPor", s.nombre());
            cambio.put("atendidaPorUid", s.uid());
            cambio.put("fechaAtencion", FieldValue.serverTimestamp());
            cambio.put("version", version(e) + 1);
            tx.update(ref, cambio);
            tx.set(mensajes(e.getString("pacienteId")).document(), mensaje(s, Reglas.mensajeAtendida(s.nombre())));
            Map<String, Object> out = json(e);
            out.putAll(cambio);
            out.put("fechaAtencion", Instant.now().toString());
            return out;
        }));
    }
}
