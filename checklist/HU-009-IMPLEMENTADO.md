# HU-009: Descarga de archivos en lotes (background) - ANÁLISIS DE IMPLEMENTACIÓN

**Fecha de análisis:** 2025-10-15
**Estado general:** ⚠️ PARCIALMENTE IMPLEMENTADO

---

## Descripción de la Historia de Usuario

**Como** usuario que inicia la sincronización
**Quiero** que los archivos se descarguen en segundo plano
**Para** poder usar otras apps mientras se completa el proceso

---

## Análisis de Criterios de Aceptación

### ✅ Criterio 1: Al presionar el CTA del banner, inicia la descarga

**Estado:** IMPLEMENTADO

**Ubicación en el código:**
- `FileExplorerScreen.kt:118-124` - Banner de archivos faltantes (MissingFilesSection)
- `FileExplorerScreen.kt:121` - Llamada a `viewModel.downloadMissingFiles()`
- `MainViewModel.kt:241-336` - Función `downloadMissingFiles()`

**Descripción:**
La UI muestra un banner (`MissingFilesSection`) cuando hay archivos disponibles para sincronizar (`missingCloudFiles.isNotEmpty()`). El banner contiene un botón CTA que llama a `viewModel.downloadMissingFiles()` para iniciar la descarga.

**Flujo implementado:**
1. Se detectan archivos faltantes en la nube (`syncCloudFiles()`)
2. Se actualiza el estado `_missingCloudFiles`
3. Si hay archivos, se muestra el banner en la tab Bóveda
4. El usuario presiona el botón de descarga
5. Se llama a `downloadMissingFiles()`

---

### ⚠️ Criterio 2: Los archivos se descargan en lotes de 10 (ajustable según pruebas)

**Estado:** PARCIALMENTE IMPLEMENTADO

**Ubicación en el código:**
- `MainViewModel.kt:260-261` - División en grupos de archivos
- `MainViewModel.kt:282-312` - Descarga paralela

**Qué está implementado:**
Los archivos se dividen en **lotes de 100** (no 10) debido al límite del API batch endpoint:

```kotlin
// Dividir en grupos de 100 archivos (límite del API)
val fileNameGroups = filesToDownload.map { it.fileName }.chunked(100)
```

Dentro de cada lote de 100, las descargas se ejecutan en paralelo usando `async`:

```kotlin
val downloadJobs = presignedUrls.map { presignedUrl ->
    async(coroutineExceptionHandler) {
        // Descarga individual
    }
}
val results = downloadJobs.awaitAll()
```

**Qué NO está implementado:**
- No hay control de concurrencia (máximo 3 descargas simultáneas como especifica HU-041)
- Todos los archivos del lote de 100 se descargan en paralelo sin restricción
- No hay lotes de 10 archivos como especifica este criterio

**Discrepancia:**
El criterio especifica "lotes de 10", pero la implementación usa "lotes de 100" por limitación del API. Esto podría causar problemas de memoria/red si se intentan descargar 100 archivos grandes simultáneamente.

**Recomendación:**
Implementar control de concurrencia para limitar las descargas simultáneas a 3-5, independientemente del tamaño del lote del API.

---

### ❌ Criterio 3: El proceso continúa en background

**Estado:** NO IMPLEMENTADO

**Qué falta:**
La descarga actual se ejecuta en `viewModelScope`, NO en un Worker de background. Esto significa:

**Implementación actual:**
```kotlin
fun downloadMissingFiles() {
    viewModelScope.launch(coroutineExceptionHandler) {
        // Descargas...
    }
}
```

**Problema:**
- `viewModelScope` está vinculado al ciclo de vida del ViewModel
- Si el usuario cierra la app o el ViewModel se destruye, la descarga se cancela
- Si el sistema mata el proceso por falta de recursos, se pierde el progreso
- NO funciona como un servicio en background persistente

**Qué se necesita implementar:**
1. Crear un `DownloadWorker` similar a `EncryptWorker` y `FileUploadWorker`
2. Usar WorkManager para encolar las descargas
3. Configurar el Worker como ForegroundService para evitar que el sistema lo mate
4. Implementar persistencia del progreso de descarga

**Referencia de implementación:**
Ver `EncryptWorker.kt` como ejemplo de cómo implementar un Worker con notificación foreground.

---

### ❌ Criterio 4: Se muestra una notificación persistente con el progreso

**Estado:** NO IMPLEMENTADO

**Qué falta:**
No existe ninguna notificación durante el proceso de descarga. La implementación actual solo:
1. Actualiza un StateFlow `_isDownloading`
2. Muestra un mensaje final vía Snackbar

**Código actual:**
```kotlin
_isDownloading.value = true  // Inicia
// ... descarga ...
showSnackbarMessage("$downloadedCount de ${filesToDownload.size} archivos descargados.")  // Final
_isDownloading.value = false  // Termina
```

**Qué se necesita implementar:**
1. Crear un NotificationChannel para descargas
2. Mostrar notificación persistente con:
   - Título: "Descargando archivos"
   - Progreso: "X de Y archivos descargados"
   - Barra de progreso (determinada)
   - Botón de cancelar (opcional)
3. Actualizar la notificación en tiempo real conforme se descargan archivos
4. Notificación final al completar con resumen de éxito/fallos

**Referencia de implementación:**
```kotlin
// Similar a EncryptWorker.kt líneas 74-93
private fun createNotification(contentText: String, progress: Int, max: Int) =
    NotificationCompat.Builder(appContext, CHANNEL_ID)
        .setContentTitle("Sincronización de archivos")
        .setContentText(contentText)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(true)
        .setProgress(max, progress, false)
        .build()
```

---

### ❌ Criterio 5: El usuario puede salir de la app y el proceso continúa

**Estado:** NO IMPLEMENTADO

**Por qué NO funciona:**
La descarga usa `viewModelScope.launch`, que se cancela cuando:
- El usuario cierra la app
- El ViewModel es destruido
- El sistema mata el proceso

**Evidencia:**
```kotlin
// MainViewModel.kt:248
viewModelScope.launch(coroutineExceptionHandler) {
    // Esta coroutine se cancela con el ViewModel
}
```

**Qué se necesita:**
Implementar con WorkManager como Worker persistente que:
1. Sobrevive a la destrucción del ViewModel
2. Sobrevive al cierre de la app
3. Puede ejecutarse como ForegroundService
4. Es gestionado por el sistema operativo

**Implementación sugerida:**
```kotlin
// Similar a MainViewModel.kt:434-455 (encryptAndUploadFiles)
fun syncMissingFiles() {
    val workManager = WorkManager.getInstance(getApplication())

    for (file in _missingCloudFiles.value) {
        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(
                DownloadWorker.KEY_USER_ID to VaultManager.getUserId(getApplication()),
                DownloadWorker.KEY_FILE_NAME to file.fileName
            ))
            .build()

        workManager.enqueue(downloadRequest)
    }
}
```

---

### ⚠️ Criterio 6: Se muestra progreso: "Descargando X de Y archivos"

**Estado:** PARCIALMENTE IMPLEMENTADO

**Ubicación en el código:**
- `MainViewModel.kt:255-256` - Estado de descarga
- `MainViewModel.kt:325` - Mensaje final
- `FileExplorerScreen.kt:122` - Estado visual en UI

**Qué está implementado:**
1. Se actualiza `_isDownloading` a `true` durante la descarga
2. La UI puede mostrar un indicador genérico de carga usando `isDownloading`
3. Se muestra mensaje final: "$downloadedCount de ${filesToDownload.size} archivos descargados."

**Qué NO está implementado:**
- No se actualiza el progreso en tiempo real ("Descargando X de Y")
- No hay StateFlow para el progreso actual
- El usuario solo ve "está descargando" vs "no está descargando", sin detalles
- No se muestra cuántos archivos faltan o el porcentaje completado

**Implementación actual:**
```kotlin
// Solo muestra estado binario (descargando: sí/no)
_isDownloading.value = true
```

**Qué se necesita:**
```kotlin
// Estado de progreso detallado
private val _downloadProgress = MutableStateFlow(DownloadProgress(0, 0))
val downloadProgress = _downloadProgress.asStateFlow()

data class DownloadProgress(
    val downloaded: Int,
    val total: Int
) {
    val percentage: Int get() = if (total > 0) (downloaded * 100) / total else 0
    val message: String get() = "Descargando $downloaded de $total archivos"
}

// Actualizar después de cada descarga
downloadedCount++
_downloadProgress.value = DownloadProgress(downloadedCount, filesToDownload.size)
```

**Logging actual:**
El progreso solo se registra en Logcat, no se muestra al usuario:
```kotlin
Log.d(TAG, "Iniciando descarga batch de ${filesToDownload.size} archivos.")
Log.d(TAG, "Procesando grupo ${groupIndex + 1}/${fileNameGroups.size}")
```

---

## Resumen de Implementación

| Criterio | Estado | Observaciones |
|----------|--------|---------------|
| 1. CTA inicia descarga | ✅ IMPLEMENTADO | Banner funcional en tab Bóveda |
| 2. Lotes de 10 archivos | ⚠️ PARCIAL | Usa lotes de 100 (límite API), sin control de concurrencia |
| 3. Proceso en background | ❌ NO IMPLEMENTADO | Usa viewModelScope, no WorkManager |
| 4. Notificación persistente | ❌ NO IMPLEMENTADO | Solo Snackbar al finalizar |
| 5. Continúa al salir de app | ❌ NO IMPLEMENTADO | Se cancela con el ViewModel |
| 6. Progreso "X de Y" | ⚠️ PARCIAL | Solo mensaje final, no tiempo real |

---

## Estado General: ⚠️ PARCIALMENTE IMPLEMENTADO

**Porcentaje estimado de implementación:** 30%

**Funcionalidad crítica implementada:**
- ✅ Integración con API de descarga batch
- ✅ Descarga paralela de archivos
- ✅ Botón CTA para iniciar sincronización
- ✅ Almacenamiento de archivos descargados
- ✅ Actualización de vault tras descarga

**Funcionalidad faltante:**
- ❌ Worker de background (WorkManager)
- ❌ Notificación persistente con progreso
- ❌ Persistencia de descarga al cerrar app
- ⚠️ Control de concurrencia (máx 3-5 simultáneas)
- ⚠️ Progreso en tiempo real visible al usuario
- ❌ Lotes de 10 archivos (usa 100)

---

## Problemas Críticos

### 1. **Pérdida de Progreso al Cerrar la App**

**Severidad:** ALTA

**Problema:**
Si el usuario cierra la app durante la descarga, se pierde todo el progreso. Los archivos parcialmente descargados no se guardan ni se reanudan.

**Impacto:**
- Mala experiencia de usuario
- Desperdicio de datos móviles
- Frustración en redes lentas o inestables

### 2. **Sin Control de Concurrencia**

**Severidad:** MEDIA

**Problema:**
Intenta descargar todos los archivos del lote (hasta 100) simultáneamente sin límite de concurrencia.

**Impacto:**
- Consumo excesivo de memoria
- Posible saturación de red
- Potenciales errores de timeout
- Bloqueo de la app en dispositivos con recursos limitados

### 3. **Falta de Retroalimentación Visual**

**Severidad:** MEDIA

**Problema:**
El usuario no sabe cuántos archivos se han descargado, cuántos faltan, o si el proceso está funcionando.

**Impacto:**
- Usuario no sabe si la app está trabajando o congelada
- No puede estimar tiempo de espera
- Puede cerrar la app pensando que falló

---

## Recomendaciones Técnicas

### 1. Implementar DownloadWorker con WorkManager

**Prioridad:** ALTA

```kotlin
class DownloadWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return Result.failure()

        // Configurar como Foreground Service
        val notification = createNotification("Descargando $fileName")
        val foregroundInfo = ForegroundInfo(id.hashCode(), notification)
        setForeground(foregroundInfo)

        return try {
            // 1. Obtener URL de descarga
            val urlResponse = ApiClient.apiService.getDownloadUrl(
                FileUrlRequest(userId, fileName)
            )

            // 2. Descargar archivo
            val fileResponse = ApiClient.apiService.downloadFileFromUrl(urlResponse.url)

            // 3. Guardar archivo
            val destinationFile = File(appContext.filesDir, "vault/$fileName")
            fileResponse.body()?.byteStream()?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $fileName", e)
            Result.retry()
        }
    }

    companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_FILE_NAME = "file_name"
        private const val TAG = "DownloadWorker"
    }
}
```

### 2. Agregar Control de Concurrencia

**Prioridad:** ALTA

```kotlin
// En downloadMissingFiles()
import kotlinx.coroutines.Semaphore

val downloadSemaphore = Semaphore(3) // Máximo 3 descargas simultáneas

val downloadJobs = presignedUrls.map { presignedUrl ->
    async(coroutineExceptionHandler) {
        downloadSemaphore.withPermit {
            // Descarga individual
            try {
                val fileResponse = ApiClient.apiService.downloadFileFromUrl(presignedUrl.url)
                // ...
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
```

### 3. Implementar Progreso en Tiempo Real

**Prioridad:** MEDIA

```kotlin
// En MainViewModel
private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
val downloadProgress = _downloadProgress.asStateFlow()

data class DownloadProgress(
    val downloaded: Int,
    val total: Int,
    val currentFile: String
)

// Actualizar después de cada descarga
downloadedCount++
_downloadProgress.value = DownloadProgress(
    downloaded = downloadedCount,
    total = filesToDownload.size,
    currentFile = presignedUrl.fileName
)
```

### 4. Agregar Notificación Persistente

**Prioridad:** ALTA

```kotlin
// Si se mantiene en ViewModel (no recomendado)
private fun updateDownloadNotification(downloaded: Int, total: Int) {
    val notificationManager = getApplication<Application>()
        .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    val notification = NotificationCompat.Builder(getApplication(), "DownloadChannel")
        .setContentTitle("Sincronizando archivos")
        .setContentText("$downloaded de $total archivos descargados")
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setProgress(total, downloaded, false)
        .setOngoing(true)
        .build()

    notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, notification)
}

// Llamar después de cada descarga
updateDownloadNotification(downloadedCount, filesToDownload.size)
```

---

## Dependencias

**Esta HU depende de:**
- HU-007: Verificación de archivos en la nube ✅ Implementado
- HU-008: Notificación de archivos disponibles ⚠️ Parcial
- API endpoint `POST /api/download-urls/batch` ✅ Implementado
- API endpoint directo para descarga de archivos ✅ Implementado

**Esta HU es prerequisito de:**
- HU-010: Finalización de sincronización
- Experiencia completa de sincronización cross-device

---

## Archivos Principales Involucrados

1. **MainViewModel.kt**
   - Líneas 241-336: Función `downloadMissingFiles()`
   - Líneas 56-57: Estados `_isDownloading` y `_missingCloudFiles`

2. **FileExplorerScreen.kt**
   - Líneas 118-124: Banner `MissingFilesSection`
   - Línea 121: Llamada a `downloadMissingFiles()`

3. **LocklyApiService.kt**
   - Líneas 103-104: Endpoint `getBatchDownloadUrls()`
   - Líneas 112-114: Endpoint `downloadFileFromUrl()`

4. **EncryptWorker.kt** (Referencia para nuevo DownloadWorker)
   - Ejemplo completo de Worker con notificación foreground

---

## Notas Adicionales

**Fortalezas de la implementación actual:**
1. Uso eficiente del API batch (reduce llamadas)
2. Descarga paralela para mejorar velocidad
3. Manejo de errores con try-catch
4. División en grupos de 100 para evitar límites del API
5. Limpieza del estado al finalizar (limpia `_missingCloudFiles`)

**Debilidades:**
1. **No persistente:** Se pierde todo al cerrar la app
2. **Sin notificaciones:** Usuario no ve progreso fuera de la app
3. **Sin control de concurrencia:** Puede saturar recursos
4. **Progreso limitado:** Solo mensaje final
5. **No cumple con patrón de Workers:** Inconsistente con cifrado/subida

**Impacto en la experiencia del usuario:**
- ⚠️ Funciona bien para cantidades pequeñas de archivos (< 20)
- ❌ Problemático para sincronizaciones grandes (100+ archivos)
- ❌ No funciona si el usuario necesita usar otras apps durante la descarga
- ❌ Desperdicia datos móviles si se interrumpe

---

## Conclusión

La HU-009 está **parcialmente implementada** con funcionalidad básica de descarga, pero **NO cumple con los requisitos críticos de background y persistencia**. La implementación actual es adecuada solo para pruebas o cantidades muy pequeñas de archivos.

**Acción recomendada:** Refactorizar la descarga para usar WorkManager con DownloadWorker, similar a cómo están implementados EncryptWorker y FileUploadWorker. Esto garantizará consistencia arquitectónica y cumplimiento completo de los criterios de aceptación.

**Prioridad de implementación:** ALTA - Los usuarios esperan poder sincronizar archivos en background sin mantener la app abierta.
