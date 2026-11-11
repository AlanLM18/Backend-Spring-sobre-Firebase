package com.safemotion.backend.config;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Conexión con Firebase mediante el Admin SDK.
 * Orden de credenciales: emulador local (FIRESTORE_EMULATOR_HOST) -> FIREBASE_SERVICE_ACCOUNT_JSON (contenido de la
 * llave de la cuenta de servicio, como variable secreta en Render) -> GOOGLE_APPLICATION_CREDENTIALS.
 * El Admin SDK no pasa por firestore.rules: los permisos por rol se validan en los servicios de este backend.
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Bean
    public FirebaseApp firebaseApp(@Value("${app.firebase.project-id}") String projectId) throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) return FirebaseApp.getInstance();
        FirebaseOptions options = FirebaseOptions.builder()
                .setProjectId(projectId)
                .setCredentials(credenciales())
                .build();
        log.info("Firebase: proyecto {} ({})", projectId, usaEmulador() ? "emulador " + System.getenv("FIRESTORE_EMULATOR_HOST") : "Cloud Firestore");
        return FirebaseApp.initializeApp(options);
    }

    @Bean
    public Firestore firestore(FirebaseApp app) {
        return FirestoreClient.getFirestore(app);
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp app) {
        return FirebaseAuth.getInstance(app);
    }

    public static boolean usaEmulador() {
        String host = System.getenv("FIRESTORE_EMULATOR_HOST");
        return host != null && !host.isBlank();
    }

    private static GoogleCredentials credenciales() throws IOException {
        if (usaEmulador()) {
            // El emulador acepta cualquier token; este nunca caduca para que el SDK no intente renovarlo.
            return GoogleCredentials.create(new AccessToken("owner", new Date(Long.MAX_VALUE)));
        }
        String json = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
        if (json != null && !json.isBlank()) {
            return GoogleCredentials.fromStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        }
        return GoogleCredentials.getApplicationDefault();
    }
}
