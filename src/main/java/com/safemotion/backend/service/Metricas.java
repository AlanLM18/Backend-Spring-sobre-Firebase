package com.safemotion.backend.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Contadores de operaciones remotas desde que arrancó el servicio (Práctica 12: profiling del backend).
 * Permiten calcular cuántas lecturas de Firestore y verificaciones de token cuesta cada petición.
 * Solo cuentan operaciones; no guardan datos de usuarios.
 */
public final class Metricas {

    private Metricas() { }

    public static final AtomicLong LECTURAS_FIRESTORE = new AtomicLong();   // get de documento o consulta
    public static final AtomicLong LECTURAS_PERFIL = new AtomicLong();      // usuarios/{uid} para autenticar
    public static final AtomicLong VERIFICACIONES_TOKEN = new AtomicLong(); // verifyIdToken remoto (revocación)
    public static final AtomicLong ACIERTOS_CACHE = new AtomicLong();       // peticiones autenticadas sin llamadas remotas

    public static Map<String, Object> resumen() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lecturasFirestore", LECTURAS_FIRESTORE.get());
        m.put("lecturasPerfil", LECTURAS_PERFIL.get());
        m.put("verificacionesToken", VERIFICACIONES_TOKEN.get());
        m.put("aciertosCache", ACIERTOS_CACHE.get());
        return m;
    }
}
