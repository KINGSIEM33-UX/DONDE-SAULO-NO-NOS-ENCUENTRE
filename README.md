# Adora — Quedadas & Salidas + Mundo Bíblico con IA

App Android en **Kotlin + Jetpack Compose** con dos mundos integrados:

- **Mundo 1 (Social & Salidas):** Perfil personalizable, grupos multiusuario en Firestore, calendario interactivo con estados (`Confirmada`, `Pensada`, `Cancelada`) y Date/Time pickers, mapa real con **Google Maps Compose**, chat de grupo con **notas de voz reales** (`MediaRecorder`/`MediaPlayer`) y dictado, cámara/álbum compartido con **Instantáneas animadas**, Agente IA social con Gemini y **8 minijuegos completos**.
- **Mundo 2 (Estudio Bíblico con IA):** Lector bíblico versículo a versículo (66 libros, búsqueda, favoritos, notas y navegación entre capítulos), estudios guiados con progreso real en Room, agenda de estudio persistente, trivia bíblica y asistente **Adora IA** con Gemini.

---

## Compilación rápida

El proyecto compila y funciona en modo local/gratis sin necesidad de claves privadas:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

---

## Cómo activar los servicios en la nube (Claves y Credenciales)

1. **Google Maps real incrustado + API.Bible opcional (`.env`):**
   Crea un archivo `.env` en la raíz del proyecto (copiando `.env.example`):
   ```properties
   MAPS_API_KEY=TU_CLAVE_DE_GOOGLE_MAPS_ANDROID
   BIBLE_API_KEY=TU_CLAVE_DE_API_BIBLE_OPCIONAL
   ```
   - `MAPS_API_KEY`: Obtén tu clave en Google Cloud Console con **Maps SDK for Android** habilitado. Sin esta clave, la app entra automáticamente en modo compañero con búsqueda Geocoder, GPS y botón directo para abrir Google Maps externo.
   - `BIBLE_API_KEY`: Opcional. Sin clave, el lector bíblico usa automáticamente el proveedor gratuito público en español (`RVR1960`, `NVI`, `NTV`, `LBLA`, `DHH`).

2. **Firebase (Google Sign-In, Firestore multiusuario, Firebase Storage y Gemini AI):**
   - Descarga tu `google-services.json` desde Firebase Console para el paquete `com.aistudio.dondesaulo.vuxmjr` y colócalo en `app/google-services.json`.
   - Despliega las reglas de Firestore actualizadas (`firestore.rules`) con:
     ```bash
     firebase deploy --only firestore:rules
     ```
