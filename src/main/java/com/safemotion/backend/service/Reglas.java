package com.safemotion.backend.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Reglas de negocio puras (sin Firebase), para poder probarlas con pruebas unitarias.
 * Roles: "usuario" (paciente), "recepcionista", "doctor", "admin" — los mismos de firestore.rules.
 */
public final class Reglas {

    private Reglas() { }

    public static final String PACIENTE = "usuario";
    public static final String RECEPCION = "recepcionista";
    public static final String DOCTOR = "doctor";
    public static final String ADMIN = "admin";

    public static final String ABIERTA = "ABIERTA";
    public static final String CANALIZADA = "CANALIZADA";
    public static final String ATENDIDA = "ATENDIDA";

    // Mismos umbrales que src/models/alerts.js de la PWA
    public static final int FC_MAX = 110;
    public static final int FC_MIN = 45;
    public static final double TEMP_MAX = 37.8;
    public static final int SPO2_MIN = 92;

    /** Datos clínicos y chat: doctor y admin. */
    public static boolean esClinico(String rol) {
        return DOCTOR.equals(rol) || ADMIN.equals(rol);
    }

    /** Ve las emergencias: clínicos y recepción. */
    public static boolean esPersonal(String rol) {
        return esClinico(rol) || RECEPCION.equals(rol);
    }

    /** El paciente solo su conversación; doctor y admin cualquiera. */
    public static boolean puedeVerChat(Sesion s, String pacienteId) {
        return esClinico(s.rol()) || (PACIENTE.equals(s.rol()) && s.uid().equals(pacienteId));
    }

    public static boolean activa(String estado) {
        return ABIERTA.equals(estado) || CANALIZADA.equals(estado);
    }

    /** Alertas de una lectura. Un valor ausente (null) nunca genera alerta. */
    public static List<String> alertas(Integer fc, Double temperatura, Integer spo2) {
        List<String> a = new ArrayList<>();
        if (fc != null && fc > FC_MAX) a.add("Frecuencia cardiaca alta");
        else if (fc != null && fc < FC_MIN) a.add("Frecuencia cardiaca baja");
        if (temperatura != null && temperatura > TEMP_MAX) a.add("Temperatura elevada");
        if (spo2 != null && spo2 < SPO2_MIN) a.add("Saturación de oxígeno baja");
        return a;
    }

    /** Límite del historial: entre 1 y 500. */
    public static boolean limiteValido(int limite) {
        return limite >= 1 && limite <= 500;
    }

    public static String mensajeCanalizada(String doctor) {
        return "Recepción canalizó tu solicitud de ayuda con " + doctor + ". En un momento te atiende.";
    }

    public static String mensajeAtendida(String doctor) {
        return "Tu solicitud de ayuda fue atendida por " + doctor + ". Mantente en este chat.";
    }

    public static String mensajeSos(Integer fc, Integer spo2, Double temp) {
        StringBuilder t = new StringBuilder("SOS: solicito ayuda de emergencia.");
        if (fc != null) t.append(" FC ").append(fc).append(" bpm.");
        if (spo2 != null) t.append(" SpO2 ").append(spo2).append(" %.");
        if (temp != null) t.append(" Temp. ").append(temp).append(" °C.");
        return t.toString();
    }
}
