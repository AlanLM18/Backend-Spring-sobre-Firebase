# SafeMotion AI · Backend Spring Boot sobre Firebase

API REST de SafeMotion AI. Java 21 · Spring Boot 3.3 · Firebase Admin SDK 9.11.
Usa **los mismos datos** que la PWA, el dashboard y el reloj: Firebase Authentication (login con PIN de 4 dígitos) y
Cloud Firestore del proyecto `mesaayuda-a631d` (`usuarios`, `usuarios/{uid}/mediciones`, `conversaciones`, `emergencias`).

- **Sesión:** `POST /api/auth/login` con `{"pin":"0000"}` devuelve el ID token de Firebase (1 h). Las demás rutas lo
  reciben en `Authorization` (tal cual o `Bearer <token>`); se verifica con el Admin SDK, incluida la revocación.
- **Permisos por rol** (paciente, recepcionista, doctor, admin) en `service/Reglas.java`, iguales a `firestore.rules`.
- **Transacciones** de Firestore en mediciones (idempotentes por `idLocal`), SOS, canalización y atención.
- **Errores** siempre como `{"status": n, "error": "mensaje"}`, sin trazas.

## Desplegar en Render

1. Render > **New > Blueprint** > este repositorio (`render.yaml` crea `safemotion-backend` con Docker).
2. Capturar las variables secretas en el panel de Render:
   - `FIREBASE_API_KEY`: llave web de Firebase (la misma `VITE_FIREBASE_API_KEY` de la PWA).
   - `FIREBASE_SERVICE_ACCOUNT_JSON`: contenido del JSON de Firebase > Configuración > Cuentas de servicio.
     **Nunca subir ese archivo a git** (está en `.gitignore`).
3. Comprobar `https://<servicio>.onrender.com/api/sistema/estado` → `"firestore":"disponible"`.

La PWA (Vercel) publica esta API en `https://safemotion-pwa.vercel.app/api/**` con un rewrite en su `vercel.json`.

## Correr en local contra el emulador de Firebase

```bash
npx firebase-tools@14 emulators:start --only auth,firestore --project demo-safemotion
FIRESTORE_EMULATOR_HOST=127.0.0.1:8085 FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIREBASE_PROJECT_ID=demo-safemotion mvn spring-boot:run
```

## Endpoints

| Método | Ruta | Quién |
|--------|------|-------|
| POST | /api/auth/login · /api/auth/logout · GET /api/auth/me | todos / sesión |
| POST | /api/mediciones | paciente, admin |
| GET | /api/mediciones/historial?limite=20 (1..500) · /api/mediciones/actual · /api/alertas | sesión |
| GET | /api/pacientes/{uid}/mediciones/ultima | doctor, admin |
| GET | /api/chat/conversaciones | doctor, admin |
| GET/POST/DELETE | /api/chat/{uid}/mensajes | paciente propio, doctor, admin |
| POST/GET | /api/emergencias | paciente crea; personal ve todas |
| GET | /api/emergencias/doctores | recepción, doctor, admin |
| PATCH | /api/emergencias/{id}/canalizar · /api/emergencias/{id}/atender | recepción · doctor asignado |
| GET · PATCH | /api/usuarios · /api/usuarios/{uid}/estado | admin |
| GET | /api/sistema/estado | público (health check) |

Pruebas: `mvn test`.
