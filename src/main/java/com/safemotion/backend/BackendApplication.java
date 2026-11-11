package com.safemotion.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SafeMotion AI · API REST sobre Firebase.
 * Los datos viven en Cloud Firestore (las mismas colecciones que usan la PWA, el dashboard y el reloj) y las
 * cuentas en Firebase Authentication. Las cuentas y sus roles las crea el administrador del proyecto
 * (tests/support/sembrarFirebase.mjs en safemotion-pwa); este servicio no tiene registro de usuarios.
 */
@SpringBootApplication
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
