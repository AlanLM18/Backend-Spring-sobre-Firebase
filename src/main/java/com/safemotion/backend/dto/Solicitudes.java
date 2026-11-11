package com.safemotion.backend.dto;

import jakarta.validation.constraints.*;

/**
 * Cuerpos que recibe la API. Los límites son los mismos de src/models/measurement.js (PWA) y de firestore.rules,
 * para que una lectura válida en la app también lo sea aquí y en las reglas.
 */
public final class Solicitudes {

    private Solicitudes() { }

    public record Login(
            @NotBlank @Pattern(regexp = "\\d{4}", message = "El PIN debe tener exactamente 4 dígitos") String pin) { }

    /** Lectura del wearable + sensores del teléfono (contrato de POST /api/mediciones). */
    public record Medicion(
            @NotNull @Min(30) @Max(220) Integer frecuenciaCardiaca,
            @NotNull @DecimalMin("30.0") @DecimalMax("43.0") Double temperatura,
            @Min(0) @Max(100) Integer spo2,                 // opcional: un wearable BLE estándar no mide SpO2
            @NotNull @Min(0) Integer pasos,
            @NotNull @Min(0) Integer calorias,
            @NotBlank @Size(max = 20) String actividad,
            @NotNull @Min(0) @Max(100) Integer bateria,
            Double giroscopioX, Double giroscopioY, Double giroscopioZ,
            @Size(max = 20) String fuente,
            @Size(max = 40) String fechaCaptura,
            // Identificador estable generado por la app: reenviar la misma lectura no la duplica (idempotencia)
            @Pattern(regexp = "[A-Za-z0-9_-]{8,64}", message = "idLocal inválido") String idLocal) { }

    public record Mensaje(@NotBlank @Size(max = 500) String texto) { }

    /** SOS: todo es opcional (sin GPS o sin wearable también se pide ayuda). */
    public record Emergencia(
            @DecimalMin("-90.0") @DecimalMax("90.0") Double latitud,
            @DecimalMin("-180.0") @DecimalMax("180.0") Double longitud,
            @Min(0) @Max(250) Integer frecuenciaCardiaca,
            @Min(0) @Max(100) Integer spo2,
            @DecimalMin("25.0") @DecimalMax("45.0") Double temperatura) { }

    public record Canalizar(@NotBlank String doctorId) { }

    public record EstadoUsuario(@NotNull Boolean habilitado) { }
}
