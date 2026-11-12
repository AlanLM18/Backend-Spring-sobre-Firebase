package com.safemotion.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.safemotion.backend.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Sesión con Firebase Authentication.
 * Login: el PIN de 4 dígitos se traduce a la cuenta de Firebase (mismo esquema que src/models/user.js de la PWA, el
 * dashboard y el reloj) y se inicia sesión con la API REST de Identity Toolkit. El token que se devuelve es el ID token
 * de Firebase (caduca en 1 h); cada petición lo verifica con el Admin SDK, incluido si fue revocado (logout o cuenta
 * deshabilitada), y vuelve a leer el perfil para aplicar el rol y el estado actuales.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    public static final String DOMINIO_PIN = "pin.safemotion.app";

    private final FirebaseAuth auth;
    private final Firestore db;
    private final String apiKey;
    private final String referer;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ObjectMapper json = new ObjectMapper();

    public AuthService(FirebaseAuth auth, Firestore db,
                       @Value("${app.firebase.api-key}") String apiKey,
                       @Value("${app.firebase.referer}") String referer) {
        this.auth = auth;
        this.db = db;
        this.apiKey = apiKey;
        this.referer = referer;
    }

    public static String correoDePin(String pin) { return pin + "@" + DOMINIO_PIN; }
    public static String passwordDePin(String pin) { return "SafeMotion-" + pin; }

    public record Login(String token, int expiraEnSegundos, Sesion usuario) { }

    public Login login(String pin) {
        JsonNode r = signInWithPassword(correoDePin(pin), passwordDePin(pin));
        String uid = r.path("localId").asText();
        Sesion s = perfil(uid);   // 403 si no tiene perfil o está deshabilitada
        db.collection("usuarios").document(uid).update("ultimoInicioSesion", FieldValue.serverTimestamp());
        return new Login(r.path("idToken").asText(), r.path("expiresIn").asInt(3600), s);
    }

    /** Valida el header Authorization ("<token>" o "Bearer <token>") y devuelve la sesión con el rol actual. */
    public Sesion autenticar(String header) {
        if (header == null || header.isBlank()) throw ApiException.noAutenticado();
        String token = header.startsWith("Bearer ") ? header.substring(7).trim() : header.trim();
        FirebaseToken t;
        try {
            Metricas.VERIFICACIONES_TOKEN.incrementAndGet();
            t = auth.verifyIdToken(token, true);   // true: rechaza tokens revocados (logout / cuenta deshabilitada)
        } catch (FirebaseAuthException | IllegalArgumentException e) {
            throw ApiException.noAutenticado();
        }
        return perfil(t.getUid());
    }

    public void logout(String header) {
        Sesion s = autenticar(header);
        revocar(s.uid());
    }

    public void revocar(String uid) {
        try {
            auth.revokeRefreshTokens(uid);
        } catch (FirebaseAuthException e) {
            log.warn("No se pudieron revocar las sesiones de {}: {}", uid, e.getMessage());
        }
    }

    /** Perfil de usuarios/{uid}. Una cuenta sin perfil o deshabilitada no puede operar (403). */
    public Sesion perfil(String uid) {
        Metricas.LECTURAS_PERFIL.incrementAndGet();
        DocumentSnapshot d = FirestoreUtil.get(db.collection("usuarios").document(uid));
        if (!d.exists()) throw new ApiException(HttpStatus.FORBIDDEN, "Tu cuenta no tiene perfil en SafeMotion");
        if (Boolean.FALSE.equals(d.getBoolean("habilitado"))) throw new ApiException(HttpStatus.FORBIDDEN, "Esta cuenta está deshabilitada");
        return new Sesion(uid, d.getString("nombre"), d.getString("rol"));
    }

    // ---- Identity Toolkit (Firebase Authentication REST) ----
    private JsonNode signInWithPassword(String correo, String password) {
        String emulador = System.getenv("FIREBASE_AUTH_EMULATOR_HOST");
        String base = emulador != null && !emulador.isBlank()
                ? "http://" + emulador + "/identitytoolkit.googleapis.com"
                : "https://identitytoolkit.googleapis.com";
        String key = apiKey == null || apiKey.isBlank() ? (emulador != null ? "emulador" : null) : apiKey;
        if (key == null) throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Falta FIREBASE_API_KEY en el servidor");
        try {
            String cuerpo = json.writeValueAsString(Map.of("email", correo, "password", password, "returnSecureToken", true));
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/v1/accounts:signInWithPassword?key=" + key))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Referer", referer)
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode body = json.readTree(res.body());
            if (res.statusCode() == 200) return body;
            String codigo = body.path("error").path("message").asText("");
            if (codigo.startsWith("USER_DISABLED")) throw new ApiException(HttpStatus.FORBIDDEN, "Esta cuenta está deshabilitada");
            if (codigo.startsWith("TOO_MANY_ATTEMPTS")) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos. Espera un momento.");
            if (codigo.startsWith("INVALID") || codigo.startsWith("EMAIL_NOT_FOUND")) throw new ApiException(HttpStatus.UNAUTHORIZED, "PIN incorrecto");
            log.error("Identity Toolkit respondió {}: {}", res.statusCode(), codigo);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "No fue posible validar el acceso con Firebase");
        } catch (IOException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Firebase Authentication no responde");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Firebase Authentication no responde");
        }
    }
}
