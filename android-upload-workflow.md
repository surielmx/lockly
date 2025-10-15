# Lockly API - Referencia Completa para Android (Flujo Extendido)

Esta documentación lista todos los endpoints de la API de Lockly con sus requests, respuestas y validaciones para implementación en la app de Android. Este documento incluye secciones extendidas sobre el flujo de subida de archivos, recomendaciones de UX y ejecución en background.

---

## Tabla de Contenidos

1.  [Health & Status](#health--status)
2.  [Gestión de Archivos](#gestión-de-archivos)
3.  [Cuotas y Almacenamiento](#cuotas-y-almacenamiento)
4.  [Operaciones Batch](#operaciones-batch)
5.  [Ejecución de Tareas en Background con WorkManager](#ejecución-de-tareas-en-background-con-workmanager)
6.  [Recomendaciones de Experiencia de Usuario (UX)](#recomendaciones-de-experiencia-de-usuario-ux)
7.  [Rate Limiting](#rate-limiting)
8.  [Manejo de Errores Centralizado](#manejo-de-errores-centralizado)
9.  [Configuración de Retrofit](#configuración-de-retrofit)
10. [Validaciones de Datos](#validaciones-de-datos)
11. [Seguridad](#seguridad)
12. [Testing](#testing)
13. [Mejores Prácticas](#mejores-prácticas)
14. [Changelog](#changelog)
15. [Soporte](#soporte)

---

## Health & Status

### GET /health

Verifica el estado del servidor.

**Request:**
```http
GET /health
```

**Response 200 (OK):**
```json
{
  "status": "ok",
  "timestamp": "2025-10-14T10:30:00.000Z",
  "environment": "production",
  "version": "1.0.0"
}
```

---

### GET /test-b2

Prueba la conexión con Backblaze B2 (almacenamiento).

**Request:**
```http
GET /test-b2
```

**Response 200 (OK):**
```json
{
  "success": true,
  "connected": true,
  "message": "B2 connection successful"
}
```

---

## Gestión de Archivos

### POST /api/upload-url

Genera una URL pre-firmada para subir un archivo encriptado a B2. Verifica la cuota de almacenamiento antes de generar la URL.

**Request:**
```http
POST /api/upload-url
Content-Type: application/json

{
  "userId": "pQZXhVkeutHcoR5ca9K0tw",
  "fileName": "13785622-523b-4c06-b6bd-d4ea0cf98a5d.enc",
  "contentType": "application/octet-stream"
}
```

**Validaciones:**
- `userId`: 3-64 caracteres (alfanuméricos, guiones, guiones bajos)
- `fileName`: Debe terminar en `.enc`, máximo 255 caracteres
- `contentType`: Formato MIME válido (opcional, default: `application/octet-stream`)

**Response 200 (OK):**
```json
{
  "success": true,
  "data": {
    "url": "https://s3.eu-central-003.backblazeb2.com/lockly-secure-storage/...",
    "key": "pQZXhVkeutHcoR5ca9K0tw/13785622-523b-4c06-b6bd-d4ea0cf98a5d.enc",
    "expiresIn": 300,
    "quota": {
      "used": 52428800,
      "limit": 104857600,
      "remaining": 52428800,
      "usedPercentage": 50.0
    }
  }
}
```
**Uso en Android:**
```kotlin
// Data classes y llamadas a API omitidas por brevedad (ver documentación original)

suspend fun getUploadUrl(userId: String, file: File): Result<UploadUrlData> {
    // ... implementación ...
}
```

#### Flujo de Operación Detallado

Para garantizar la integridad de los datos y un manejo de errores robusto, el proceso de subida de un archivo debe seguir estos pasos en orden:

1.  **Cifrado Local:** Antes de cualquier petición de red, la aplicación **debe** cifrar el archivo original en el dispositivo del usuario. Esto crea un archivo temporal `.enc`.

2.  **Verificación de Cuota y Obtención de URL:** Con el archivo ya cifrado y listo para subir, se realiza la llamada a `POST /api/upload-url`. El servidor valida la cuota del usuario y, si es correcta, devuelve la URL pre-firmada para B2.

3.  **Subida a B2:** La aplicación utiliza la URL pre-firmada para subir el archivo cifrado (`.enc`) directamente al bucket de B2. Esta operación **no pasa** por los servidores de Lockly.

4.  **Confirmación y Limpieza:** El proceso de subida **solo se considera terminado** cuando la app recibe una respuesta exitosa (por ejemplo, `HTTP 200 OK`) del servidor de B2. **Solo después de esta confirmación**, la aplicación debe eliminar de forma segura el archivo cifrado temporal que se creó en el paso 1. Esto previene la pérdida de datos si la conexión falla durante la subida.

---

### POST /api/download-url

Genera una URL pre-firmada para descargar un archivo encriptado desde B2.

---
(El resto de las secciones de API se omiten por brevedad, pero estarían incluidas aquí)
---

## Ejecución de Tareas en Background con WorkManager

El proceso de cifrado y subida de archivos, especialmente los de gran tamaño o en lotes, puede tomar un tiempo considerable. Para evitar que el usuario deba mantener la aplicación abierta, es **altamente recomendable** ejecutar estas operaciones en segundo plano.

**WorkManager**, parte de Android Jetpack, es la solución ideal para esta tarea.

### ¿Por qué usar WorkManager?

1.  **Ejecución Garantizada:** WorkManager se asegura de que la tarea se complete, incluso si el usuario cierra la aplicación o reinicia el dispositivo.
2.  **Gestión de Restricciones:** Permite definir bajo qué condiciones se debe ejecutar la tarea. Por ejemplo, se puede configurar para que la subida de archivos solo se realice cuando el dispositivo esté conectado a una red Wi-Fi y cargando, para no consumir datos móviles ni batería innecesariamente.
3.  **Gestión de Reintentos:** Incorpora políticas de reintentos automáticos (como `exponential backoff`) si la tarea falla por problemas de red, simplificando el manejo de errores.
4.  **Compatibilidad con Versiones Anteriores:** Funciona en una amplia gama de versiones de Android, eligiendo la mejor manera de ejecutar la tarea (usando `JobScheduler` o `BroadcastReceiver`).

### Implementación a Alto Nivel

1.  **Crear un `Worker`:** Define una clase que herede de `CoroutineWorker`. Su método `doWork()` contendrá toda la lógica:
    *   Recibir el URI del archivo original como dato de entrada.
    *   Realizar el cifrado del archivo.
    *   Llamar a la API de Lockly para obtener la URL pre-firmada.
    *   Subir el archivo cifrado a B2.
    *   Gestionar el resultado (éxito o fallo) y la limpieza de archivos temporales.

2.  **Construir y Encolar la Petición de Trabajo (`WorkRequest`):**
    *   Desde tu ViewModel o repositorio, crea una `OneTimeWorkRequest` o `PeriodicWorkRequest`.
    *   Añade las restricciones (ej. `Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()`).
    *   Pasa los datos necesarios al `Worker` (ej. el URI del archivo).
    *   Encola la petición usando `WorkManager.getInstance(context).enqueue(workRequest)`.

3.  **Observar el Estado (Opcional pero Recomendado):**
    *   Puedes observar el estado del `WorkRequest` usando su ID para actualizar la UI en tiempo real, mostrando si la tarea está en cola, en ejecución, completada o fallida.

---

## Recomendaciones de Experiencia de Usuario (UX)

Mostrar al usuario que una tarea larga está en progreso es crucial para una buena experiencia.

### Subida de un Solo Archivo

*   **Notificación Persistente:** Utiliza una notificación en primer plano (`Foreground Service` desde el `Worker`) que indique que el proceso de cifrado/subida está activo.
    *   **Fase de Cifrado:** Puede mostrar una barra de progreso indeterminada (`"Cifrando archivo..."`).
    *   **Fase de Subida:** Si es posible obtener el progreso de la subida, cambia a una barra de progreso determinada (`"Subiendo... 45%"`).
*   **Estado Final:** La notificación debe actualizarse para reflejar el resultado: un mensaje de éxito (`"Archivo subido correctamente"`) o un error (`"No se pudo subir el archivo"`), con una posible acción de reintento.

### Subida de Múltiples Archivos (Batch)

*   **Notificación de Resumen:** Muestra una única notificación persistente que resuma el estado general (ej. `"Subiendo 7 archivos..."`).
*   **Pantalla de Detalles (Opcional):** Al tocar la notificación, el usuario podría ser llevado a una pantalla dentro de la app que muestre una lista de los archivos y el estado individual de cada uno (en cola, cifrando, subiendo, completado, error).
*   **Actualización de Progreso:** La notificación de resumen debe actualizarse a medida que los archivos se completan (ej. `"Archivos subidos: 3 de 7"`).
*   **Resultado Final:** Al finalizar, la notificación debe indicar el resultado final. Si hubo errores, un mensaje como `"6 de 7 archivos subidos. Toca para ver detalles."` puede llevar al usuario a la pantalla de detalles para que vea qué falló.

---
(El resto de las secciones de la documentación original como Rate Limiting, Seguridad, etc., seguirían aquí)
---
