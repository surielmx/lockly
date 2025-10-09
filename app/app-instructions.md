# Plan de trabajo iterativo para app de cifrado de archivos en Kotlin

**Objetivo general**  
Desarrollar una app que detecte, cifre y proteja archivos (documentos, PDFs, fotos) con seguridad avanzada, integrando funcionalidad de borrado remoto y protección biométrica/contraseña, mediante iteraciones pequeñas para pruebas tempranas y desarrollo incremental.

---

## Iteración 0 — Preparación del proyecto y entorno

**Objetivo:** Configurar proyecto Kotlin para Android y validar compatibilidad de librerías.

**Actividades principales:**
- Crear proyecto Android Studio con Kotlin.
- Configurar estructura de carpetas y módulos (app, librerías, pruebas).
- Definir target SDK (Android 13+) y mínimo SDK (Android 11+ recomendable).
- Configuración inicial de permisos en `AndroidManifest.xml`:
    - `READ_EXTERNAL_STORAGE` (solo si es estrictamente necesario, para carpetas visibles)
    - `WRITE_EXTERNAL_STORAGE` (solo para versiones < Android 11)
    - `MANAGE_EXTERNAL_STORAGE` (opcional, si se requiere acceso total a carpetas externas; explicar al usuario)
    - `INTERNET` y `ACCESS_NETWORK_STATE` (para borrado remoto y notificaciones push)
    - `USE_BIOMETRIC` y `USE_FINGERPRINT`
    - `FOREGROUND_SERVICE` (para background service estable)

**Librerías recomendadas:**
- [Tink](https://developers.google.com/tink) — librería de criptografía moderna y segura.
- [libsodium-jni](https://github.com/jedisct1/libsodium) — cifrado avanzado XChaCha20-Poly1305.
- [SQLCipher for Android](https://www.zetetic.net/sqlcipher/sqlcipher-for-android/) — base de datos cifrada de metadatos.
- [Firebase Cloud Messaging (FCM)](https://firebase.google.com/docs/cloud-messaging) — notificaciones push seguras.
- AndroidX Biometric — integración biometría.
- WorkManager — tareas de background (cifrado nocturno).

---

## Iteración 1 — Registro de usuario y seguridad básica

**Objetivo:** Implementar autenticación local con contraseña y biometría, y crear “vault” seguro para archivos de prueba.

**Actividades implementadas:**
- Pantalla de registro/ingreso de contraseña maestra (AuthScreen).
- Integración biométrica para desbloqueo de app (AndroidX Biometric + BiometricPrompt).
- Creación de almacenamiento seguro (app-specific storage) para archivos de prueba (VaultManager con cifrado AES y clave derivada PBKDF2).
- Configuración de permisos adicionales:
    - Acceso a almacenamiento privado (`Context.getFilesDir()`)
    - Permisos biométricos (AndroidX Biometric en AndroidManifest.xml)
- Validar que el acceso al vault esté bloqueado si no se autentica el usuario.
- La contraseña maestra se guarda como hash con salt (SHA-256) y el vault se cifra con clave derivada (PBKDF2).
- El nombre de los archivos internos es discreto y no explícito.
- La contraseña se mantiene en memoria durante la sesión y se pasa automáticamente a MainActivity para todas las operaciones del vault.
- El campo de contraseña oculta los caracteres (visualTransformation).
- Flujo de autenticación conectado entre AuthActivity y MainActivity usando Intents.

**Librerías por iteración:**
- AndroidX Biometric — biometría y fallback a contraseña.
- Cifrado AES con clave derivada PBKDF2 (implementado en VaultManager).
- (No se usó Tink/libsodium ni SQLCipher en esta iteración, pero el cifrado básico está implementado).

**Adicional implementado:**
- Comentario en el código para futura implementación de bloqueo automático por inactividad.
- Eliminación de archivos obsoletos recomendada para mantener solo los actuales: _vlt.bin, _vlt_pwd.bin, _vlt_slt.bin.

**Pendiente para futuras iteraciones:**
- Implementar Tink/libsodium para cifrado avanzado si se requiere.
- Implementar SQLCipher si se necesita registro de metadatos cifrados.
- Bloqueo automático de la app tras X tiempo de inactividad.

---

## Iteración 2 — Cifrado y desencriptado de archivos seleccionados

**Objetivo:** Permitir al usuario cifrar/desencriptar archivos manualmente.

**Actividades principales:**
1. Permitir al usuario seleccionar archivos desde almacenamiento o galería usando el Storage Access Framework (SAF).
2. Cifrar el archivo seleccionado con cifrado simétrico seguro (AES-GCM o XChaCha20-Poly1305), usando Tink o libsodium-jni.
3. Generar una content key única para cada archivo y envolverla (encrypt) con la master key almacenada en el Keystore.
4. Guardar el archivo cifrado en el vault y almacenar la content key cifrada junto con metadatos mínimos.
5. Permitir desencriptar el archivo mediante autenticación por contraseña o biometría (AndroidX Biometric).
6. Solicitar y gestionar los permisos necesarios para acceso a archivos mediante SAF, evitando permisos globales de almacenamiento.

**Librerías recomendadas:**
- Tink / libsodium-jni — cifrado y desencriptado seguro.
- AndroidX Biometric — desbloqueo seguro.
- Storage Access Framework — selección y acceso a archivos.

**Notas técnicas:**
- Validar que la master key nunca salga del Keystore.
- El flujo de cifrado/desencriptado debe ser transparente y seguro para el usuario.
- Probar con archivos de diferentes tipos (PDF, JPG, DOCX) y tamaños.
- Documentar el proceso y posibles errores para pruebas funcionales.

---

## Iteración 3 — Detección de archivos nuevos y alertas al usuario

**Objetivo:** Detectar fotos, documentos o archivos descargados y preguntar al usuario si desea cifrarlos.

**Actividades:**
- Implementar observadores de archivos:
    - Android: `FileObserver` / `ContentObserver` / MediaStore queries
    - iOS (opcional si multiplataforma): `PhotoKit Change Observer`
- Mostrar diálogo al usuario: *“¿Deseas cifrar este archivo?”*
- Opción de abrir temporalmente con otras apps sin moverlo al vault.

**Permisos requeridos:**
- `READ_EXTERNAL_STORAGE` / SAF para acceso a carpetas seleccionadas.
- Notificaciones locales para alertas.

**Librerías por iteración:**
- Tink / libsodium-jni — cifrado de archivos detectados.
- WorkManager — en caso de que el check se haga en background.

---

## Iteración 4 — Cifrado automático nocturno

**Objetivo:** Revisar carpetas predefinidas y cifrar archivos no cifrados automáticamente.

**Actividades:**
- Implementar tarea de background recurrente (WorkManager).
- Registrar resultados de cifrado en base de datos segura (SQLCipher).
- Notificar al usuario al final del proceso con resumen de archivos cifrados.

**Permisos requeridos:**
- Acceso a carpetas seleccionadas mediante SAF o MediaStore.
- Background execution (`FOREGROUND_SERVICE` si se necesita en Android 12+).

**Librerías por iteración:**
- WorkManager — planificación de tareas.
- Tink / libsodium-jni — cifrado automático.
- SQLCipher — registro de metadatos.

---

## Iteración 5 — Borrado remoto

**Objetivo:** Permitir borrar o invalidar archivos de forma remota si el teléfono se pierde o es robado.

**Actividades:**
- Backend (opcional): servicio web autenticado que envía comando de borrado.
- Dispositivo: recibir comando seguro mediante FCM / APNs y validar firma.
- Acción: borrar master key o sobrescribirla, haciendo inútiles los archivos cifrados.

**Permisos requeridos:**
- `INTERNET` y notificaciones push.
- Background execution para recibir comando remoto incluso si app está cerrada.

**Librerías por iteración:**
- Firebase Cloud Messaging — notificaciones push.
- Tink / libsodium-jni — cifrado/desencriptado y wipe seguro de llaves.

---

## Iteración 6 — Mejora de UX y seguridad avanzada

**Objetivo:** Pulir experiencia, prevención de pérdida de datos y rendimiento.

**Actividades:**
- Implementar previews seguras de archivos desencriptados temporalmente.
- Auto-borrado de archivos temporales después de TTL.
- Optimizar cifrado para archivos grandes (PDFs, fotos de alta resolución).
- Auditoría de eventos: registro de acciones cifrado/desencriptado/borrado.
- Configuración de alertas y notificaciones locales.

**Librerías recomendadas:**
- Tink / libsodium-jni — cifrado seguro.
- SQLCipher — auditoría y metadatos.
- AndroidX Biometric — desbloqueo de archivos temporalmente.
- Notificaciones locales de Android (`NotificationCompat`)

---

## Notas finales
- Todas las librerías mencionadas son **actualizadas y compatibles con Kotlin** al 2025, sin problemas conocidos de integración.
- Cada iteración debe terminar con **pruebas funcionales y de seguridad** antes de avanzar.
- El flujo incremental permite **probar funcionalidades críticas** antes de completar toda la app, reduciendo riesgo y errores de integración.
