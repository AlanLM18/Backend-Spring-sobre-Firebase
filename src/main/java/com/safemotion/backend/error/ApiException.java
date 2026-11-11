package com.safemotion.backend.error;

import org.springframework.http.HttpStatus;

/** Error de negocio con su código HTTP. datos: cuerpo adicional (p. ej. un 409 trae el registro como quedó). */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final transient Object datos;

    public ApiException(HttpStatus status, String mensaje) {
        this(status, mensaje, null);
    }

    public ApiException(HttpStatus status, String mensaje, Object datos) {
        super(mensaje);
        this.status = status;
        this.datos = datos;
    }

    public HttpStatus getStatus() { return status; }
    public Object getDatos() { return datos; }

    public static ApiException noAutenticado() { return new ApiException(HttpStatus.UNAUTHORIZED, "Sesión inválida o expirada"); }
    public static ApiException sinPermiso() { return new ApiException(HttpStatus.FORBIDDEN, "No tienes permiso para esta operación"); }
    public static ApiException noExiste(String que) { return new ApiException(HttpStatus.NOT_FOUND, que + " no existe"); }
}
