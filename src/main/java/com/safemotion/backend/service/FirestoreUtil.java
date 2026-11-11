package com.safemotion.backend.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.safemotion.backend.error.ApiException;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Utilidades para esperar las operaciones de Firestore y convertir sus datos a JSON (fechas ISO-8601). */
public final class FirestoreUtil {

    private FirestoreUtil() { }

    static final long ESPERA_S = 10;

    /** Espera el resultado; los errores de Firestore se convierten en ApiException (las de negocio se conservan). */
    public static <T> T esperar(ApiFuture<T> f) {
        try {
            return f.get(ESPERA_S, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            // Una ApiException lanzada dentro de una transacción llega envuelta: se busca en la cadena de causas.
            for (Throwable c = e.getCause(); c != null; c = c.getCause() == c ? null : c.getCause()) {
                if (c instanceof ApiException a) throw a;
            }
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Cloud Firestore no respondió correctamente");
        } catch (TimeoutException e) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "Cloud Firestore tardó demasiado en responder");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Operación interrumpida");
        }
    }

    public static DocumentSnapshot get(DocumentReference ref) { return esperar(ref.get()); }
    public static QuerySnapshot get(Query q) { return esperar(q.get()); }

    public static String iso(Object v) {
        return v instanceof Timestamp t ? t.toDate().toInstant().toString() : (v == null ? null : v.toString());
    }

    /** Documento -> mapa JSON: "id" = id del documento y todas las fechas (Timestamp) como texto ISO. */
    public static Map<String, Object> json(DocumentSnapshot d) {
        Map<String, Object> m = new HashMap<>();
        Map<String, Object> data = d.getData();
        if (data != null) data.forEach((k, v) -> m.put(k, v instanceof Timestamp ? iso(v) : v));
        m.put("id", d.getId());
        return m;
    }
}
