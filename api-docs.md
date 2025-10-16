# Lockly API - Referencia Completa para Android

Esta documentación lista todos los endpoints de la API de Lockly con sus requests, respuestas y validaciones para implementación en la app de Android.

---

## Tabla de Contenidos

1. [Health & Status](#health--status)
    - [GET /health](#get-health)
    - [GET /test-b2](#get-test-b2)
2. [Gestión de Archivos](#gestión-de-archivos)
    - [POST /api/upload-url](#post-apiupload-url)
    - [POST /api/download-url](#post-apidownload-url)
    - [GET /api/files/:userId](#get-apifilesuserid)
3. [Cuotas y Almacenamiento](#cuotas-y-almacenamiento)
    - [GET /api/quota/:userId](#get-apiquotauserid)
4. [Operaciones Batch](#operaciones-batch)
    - [POST /api/upload-urls/batch](#post-apiupload-urlsbatch)
    - [POST /api/download-urls/batch](#post-apidownload-urlsbatch)
    - [DELETE /api/files/batch](#delete-apifilesbatch)

---

## Health & Status

### GET /health

Verifica el estado del servidor.

**Request:**
```http
GET /health
```

**No requiere body ni autenticación.**

**Response 200 (OK):**
```json
{
  "status": "ok",
  "timestamp": "2025-10-14T10:30:00.000Z",
  "environment": "production",
  "version": "1.0.0"
}
```

**Uso en Android:**
```kotlin
data class HealthResponse(
    val status: String,
    val timestamp: String,
    val environment: String,
    val version: String
)

suspend fun checkHealth(): Boolean {
    return try {
        val response = apiService.getHealth()
        response.status == "ok"
    } catch (e: Exception) {
        false
    }
}
```

---

### GET /test-b2

Prueba la conexión con Backblaze B2 (almacenamiento).

**Request:**
```http
GET /test-b2
```

**No requiere body ni autenticación.**

**Response 200 (OK):**
```json
{
  "success": true,
  "connected": true,
  "message": "B2 connection successful"
}
```

**Response 500 (Error):**
```json
{
  "success": false,
  "connected": false,
  "error": "Connection failed: Invalid credentials"
}
```

**Uso en Android:**
```kotlin
data class B2TestResponse(
    val success: Boolean,
    val connected: Boolean,
    val message: String? = null,
    val error: String? = null
)

suspend fun testStorageConnection(): Boolean {
    return try {
        val response = apiService.testB2Connection()
        response.connected
    } catch (e: Exception) {
        false
    }
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

**Response 400 (Validación fallida):**
```json
{
  "success": false,
  "error": "fileName must end with .enc extension"
}
```

**Response 403 (Cuota excedida):**
```json
{
  "success": false,
  "error": "Storage quota exceeded. Used: 100.5MB of 100MB"
}
```

**Response 429 (Rate limit):**
```json
{
  "success": false,
  "error": "Too many requests, please try again later."
}
```

**Response 500 (Error del servidor):**
```json
{
  "success": false,
  "error": "Internal server error"
}
```

**Uso en Android:**
```kotlin
data class UploadUrlRequest(
    val userId: String,
    val fileName: String,
    val contentType: String = "application/octet-stream"
)

data class UploadUrlResponse(
    val success: Boolean,
    val data: UploadUrlData? = null,
    val error: String? = null
)

data class UploadUrlData(
    val url: String,
    val key: String,
    val expiresIn: Int,
    val quota: QuotaInfo
)

data class QuotaInfo(
    val used: Long,
    val limit: Long,
    val remaining: Long,
    val usedPercentage: Double
)

suspend fun getUploadUrl(userId: String, file: File): Result<UploadUrlData> {
    return try {
        val fileName = "${UUID.randomUUID()}.enc"
        val request = UploadUrlRequest(
            userId = userId,
            fileName = fileName,
            contentType = file.getMimeType()
        )

        val response = apiService.getUploadUrl(request)

        if (response.success && response.data != null) {
            Result.success(response.data)
        } else {
            Result.failure(Exception(response.error ?: "Unknown error"))
        }
    } catch (e: HttpException) {
        when (e.code()) {
            400 -> Result.failure(ValidationException(e.message()))
            403 -> Result.failure(QuotaExceededException())
            429 -> Result.failure(RateLimitException())
            else -> Result.failure(e)
        }
    }
}
```

---

### POST /api/download-url

Genera una URL pre-firmada para descargar un archivo encriptado desde B2. Verifica que el archivo exista antes de generar la URL.

**Request:**
```http
POST /api/download-url
Content-Type: application/json

{
  "userId": "pQZXhVkeutHcoR5ca9K0tw",
  "fileName": "13785622-523b-4c06-b6bd-d4ea0cf98a5d.enc"
}
```

**Validaciones:**
- `userId`: 3-64 caracteres (alfanuméricos, guiones, guiones bajos)
- `fileName`: Debe terminar en `.enc`, máximo 255 caracteres

**Response 200 (OK):**
```json
{
  "success": true,
  "data": {
    "url": "https://s3.eu-central-003.backblazeb2.com/lockly-secure-storage/...",
    "expiresIn": 300
  }
}
```

**Response 400 (Validación fallida):**
```json
{
  "success": false,
  "error": "userId must be between 3 and 64 characters"
}
```

**Response 404 (Archivo no encontrado):**
```json
{
  "success": false,
  "error": "File not found: 13785622-523b-4c06-b6bd-d4ea0cf98a5d.enc"
}
```

**Response 429 (Rate limit):**
```json
{
  "success": false,
  "error": "Too many requests, please try again later."
}
```

**Response 500 (Error del servidor):**
```json
{
  "success": false,
  "error": "Internal server error"
}
```

**Uso en Android:**
```kotlin
data class DownloadUrlRequest(
    val userId: String,
    val fileName: String
)

data class DownloadUrlResponse(
    val success: Boolean,
    val data: DownloadUrlData? = null,
    val error: String? = null
)

data class DownloadUrlData(
    val url: String,
    val expiresIn: Int
)

suspend fun getDownloadUrl(userId: String, fileName: String): Result<DownloadUrlData> {
    return try {
        val request = DownloadUrlRequest(userId, fileName)
        val response = apiService.getDownloadUrl(request)

        if (response.success && response.data != null) {
            Result.success(response.data)
        } else {
            Result.failure(Exception(response.error ?: "Unknown error"))
        }
    } catch (e: HttpException) {
        when (e.code()) {
            400 -> Result.failure(ValidationException(e.message()))
            404 -> Result.failure(FileNotFoundException())
            429 -> Result.failure(RateLimitException())
            else -> Result.failure(e)
        }
    }
}
```

---

### GET /api/files/:userId

Obtiene la lista de todos los archivos de un usuario específico.

**Request:**
```http
GET /api/files/pQZXhVkeutHcoR5ca9K0tw
```

**No requiere body.**

**Validaciones:**
- `userId`: 3-64 caracteres (alfanuméricos, guiones, guiones bajos)

**Response 200 (OK):**
```json
{
  "success": true,
  "count": 3,
  "data": [
    {
      "fileName": "13785622-523b-4c06-b6bd-d4ea0cf98a5d.enc",
      "size": 1048576,
      "lastModified": "2025-10-14T10:30:00.000Z",
      "contentType": "application/octet-stream"
    },
    {
      "fileName": "4ce6b64d-7245-4b6c-823c-08966846bd15.enc",
      "size": 2097152,
      "lastModified": "2025-10-14T09:15:00.000Z",
      "contentType": "image/jpeg"
    },
    {
      "fileName": "8bfeca18-6dbf-47d6-ae32-4b0cea2a5c02.enc",
      "size": 524288,
      "lastModified": "2025-10-14T08:00:00.000Z",
      "contentType": "application/pdf"
    }
  ]
}
```

**Response 200 (Sin archivos):**
```json
{
  "success": true,
  "count": 0,
  "data": []
}
```

**Response 400 (userId inválido):**
```json
{
  "success": false,
  "error": "userId must be between 3 and 64 characters"
}
```

**Response 429 (Rate limit):**
```json
{
  "success": false,
  "error": "Too many requests, please try again later."
}
```

**Response 500 (Error del servidor):**
```json
{
  "success": false,
  "error": "Internal server error"
}
```

**Uso en Android:**
```kotlin
data class FilesListResponse(
    val success: Boolean,
    val count: Int,
    val data: List<FileMetadata>? = null,
    val error: String? = null
)

data class FileMetadata(
    val fileName: String,
    val size: Long,
    val lastModified: String,
    val contentType: String
)

suspend fun getUserFiles(userId: String): Result<List<FileMetadata>> {
    return try {
        val response = apiService.getUserFiles(userId)

        if (response.success && response.data != null) {
            Result.success(response.data)
        } else {
            Result.failure(Exception(response.error ?: "Unknown error"))
        }
    } catch (e: HttpException) {
        when (e.code()) {
            400 -> Result.failure(ValidationException(e.message()))
            429 -> Result.failure(RateLimitException())
            else -> Result.failure(e)
        }
    }
}
```

---

## Cuotas y Almacenamiento

### GET /api/quota/:userId

Obtiene información sobre el uso de almacenamiento del usuario (usado, límite, restante).

**Request:**
```http
GET /api/quota/pQZXhVkeutHcoR5ca9K0tw
```

**No requiere body.**

**Validaciones:**
- `userId`: 3-64 caracteres (alfanuméricos, guiones, guiones bajos)

**Response 200 (OK):**
```json
{
  "success": true,
  "data": {
    "userId": "pQZXhVkeutHcoR5ca9K0tw",
    "used": 52428800,
    "limit": 104857600,
    "remaining": 52428800,
    "usedPercentage": 50.0,
    "usedFormatted": "50.00 MB",
    "limitFormatted": "100.00 MB",
    "remainingFormatted": "50.00 MB"
  }
}
```

**Response 400 (userId inválido):**
```json
{
  "success": false,
  "error": "userId must be between 3 and 64 characters"
}
```

**Response 429 (Rate limit):**
```json
{
  "success": false,
  "error": "Too many requests, please try again later."
}
```

**Response 500 (Error del servidor):**
```json
{
  "success": false,
  "error": "Internal server error"
}
```

**Uso en Android:**
```kotlin
data class QuotaResponse(
    val success: Boolean,
    val data: QuotaData? = null,
    val error: String? = null
)

data class QuotaData(
    val userId: String,
    val used: Long,
    val limit: Long,
    val remaining: Long,
    val usedPercentage: Double,
    val usedFormatted: String,
    val limitFormatted: String,
    val remainingFormatted: String
)

suspend fun getUserQuota(userId: String): Result<QuotaData> {
    return try {
        val response = apiService.getUserQuota(userId)

        if (response.success && response.data != null) {
            Result.success(response.data)
        } else {
            Result.failure(Exception(response.error ?: "Unknown error"))
        }
    } catch (e: HttpException) {
        when (e.code()) {
            400 -> Result.failure(ValidationException(e.message()))
            429 -> Result.failure(RateLimitException())
            else -> Result.failure(e)
        }
    }
}

// Uso para mostrar en UI
fun displayQuotaInfo(quotaData: QuotaData) {
    val percentage = quotaData.usedPercentage.toInt()
    binding.quotaProgressBar.progress = percentage
    binding.quotaText.text = "${quotaData.usedFormatted} de ${quotaData.limitFormatted}"

    // Cambiar color si está cerca del límite
    when {
        percentage >= 90 -> binding.quotaProgressBar.progressTintList =
            ColorStateList.valueOf(Color.RED)
        percentage >= 75 -> binding.quotaProgressBar.progressTintList =
            ColorStateList.valueOf(Color.YELLOW)
        else -> binding.quotaProgressBar.progressTintList =
            ColorStateList.valueOf(Color.GREEN)
    }
}
```

---

## Operaciones Batch

### POST /api/upload-urls/batch

Genera múltiples URLs pre-firmadas para subir archivos en una sola petición. Más eficiente que llamar al endpoint individual múltiples veces.

**Request:**
```http
POST /api/upload-urls/batch
Content-Type: application/json

{
  "userId": "pQZXhVkeutHcoR5ca9K0tw",
  "files": [
    {
      "fileName": "13785622-523b-4c06-b6bd-d4ea0cf98a5d.enc",
      "contentType": "application/octet-stream"
    },
    {
      "fileName": "4ce6b64d-7245-4b6c-823c-08966846bd15.enc",
      "contentType": "image/jpeg"
    },
    {
      "fileName": "8bfeca18-6dbf-47d6-ae32-4b0cea2a5c02.enc",
      "contentType": "application/pdf"
    }
  ]
}
```

**Validaciones:**
- `userId`: 3-64 caracteres (alfanuméricos, guiones, guiones bajos)
- `files`: Array de 1 a 100 archivos
- Cada archivo:
    - `fileName`: Requerido, debe terminar en `.enc`, máximo 255 caracteres
    - `contentType`: Opcional, formato MIME válido (default: `application/octet-stream`)

**Límites:**
- **Máximo 100 archivos** por petición
- URLs expiran en **5 minutos**

**Response 200 (OK):**
```json
{
  "success": true,
  "count": 3,
  "data": [
    {
      "fileName": "13785622-523b-4c06-b6bd-d4ea0cf98a5d.enc",
      "url": "https://s3.eu-central-003.backblazeb2.com/lockly-secure-storage/...",
      "key": "pQZXhVkeutHcoR5ca9K0tw/13785622-523b-4c06-b6bd-d4ea0cf98a5d.enc",
      "expiresIn": 300
    },
    {
      "fileName": "4ce6b64d-7245-4b6c-823c-08966846bd15.enc",
      "url": "https://s3.eu-central-003.backblazeb2.com/lockly-secure-storage/...",
      "key": "pQZXhVkeutHcoR5ca9K0tw/4ce6b64d-7245-4b6c-823c-08966846bd15.enc",
      "expiresIn": 300
    },
    {
      "fileName": "8bfeca18-6dbf-47d6-ae32-4b0cea2a5c02.enc",
      "url": "https://s3.eu-central-003.backblazeb2.com/lockly-secure-storage/...",
      "key": "pQZXhVkeutHcoR5ca9K0tw/8bfeca18-6dbf-47d6-ae32-4b0cea2a5c02.enc",
      "expiresIn": 300
    }
  ]
}
```

**Response 400 (Validación fallida):**
```json
// Array vacío
{
  "success": false,
  "error": "files array cannot be empty"
}

// Demasiados archivos
{
  "success": false,
  "error": "Maximum 100 files allowed per batch request"
}

// Archivo inválido en el índice 2
{
  "success": false,
  "error": "File at index 2: fileName must end with .enc extension"
}
```

**Response 403 (Cuota excedida):**
```json
{
  "success": false,
  "error": "Storage quota exceeded. Used: 100.5MB of 100MB"
}
```

**Response 429 (Rate limit):**
```json
{
  "success": false,
  "error": "Too many requests, please try again later."
}
```

**Response 500 (Error del servidor):**
```json
{
  "success": false,
  "error": "Internal server error"
}
```

**Uso en Android:**
```kotlin
data class BatchUploadRequest(
    val userId: String,
    val files: List<FileInfo>
)

data class FileInfo(
    val fileName: String,
    val contentType: String = "application/octet-stream"
)

data class BatchUploadResponse(
    val success: Boolean,
    val count: Int? = null,
    val data: List<PresignedUrl>? = null,
    val error: String? = null
)

data class PresignedUrl(
    val fileName: String,
    val url: String,
    val key: String,
    val expiresIn: Int
)

suspend fun uploadMultipleFiles(userId: String, files: List<File>): Result<List<String>> {
    return try {
        // 1. Preparar la lista de archivos
        val fileInfoList = files.map { file ->
            FileInfo(
                fileName = "${UUID.randomUUID()}.enc",
                contentType = file.getMimeType()
            )
        }

        // Validar límite de 100 archivos
        if (fileInfoList.size > 100) {
            return Result.failure(Exception("Maximum 100 files per batch"))
        }

        // 2. Obtener URLs en batch (1 sola petición)
        val response = apiService.getBatchUploadUrls(
            BatchUploadRequest(userId, fileInfoList)
        )

        if (!response.success || response.data == null) {
            return Result.failure(Exception(response.error ?: "Unknown error"))
        }

        // 3. Subir archivos en paralelo
        val uploadJobs = response.data.mapIndexed { index, presignedUrl ->
            async(Dispatchers.IO) {
                uploadToBucket(presignedUrl.url, files[index])
                presignedUrl.fileName
            }
        }

        val uploadedFiles = uploadJobs.awaitAll()
        Result.success(uploadedFiles)

    } catch (e: HttpException) {
        when (e.code()) {
            400 -> Result.failure(ValidationException(e.message()))
            403 -> Result.failure(QuotaExceededException())
            429 -> Result.failure(RateLimitException())
            else -> Result.failure(e)
        }
    }
}

private suspend fun uploadToBucket(url: String, file: File) {
    val requestBody = file.asRequestBody("application/octet-stream".toMediaType())
    val request = Request.Builder()
        .url(url)
        .put(requestBody)
        .build()

    okHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw IOException("Upload failed: ${response.code}")
        }
    }
}
```

---

### POST /api/download-urls/batch

Genera múltiples URLs pre-firmadas para descargar archivos en una sola petición. Más eficiente que llamar al endpoint individual múltiples veces.

**Request:**
```http
POST /api/download-urls/batch
Content-Type: application/json

{
  "userId": "pQZXhVkeutHcoR5ca9K0tw",
  "fileNames": [
    "13785622-523b-4c06-b6bd-d4ea0cf98a5d.enc",
    "4ce6b64d-7245-4b6c-823c-08966846bd15.enc",
    "8bfeca18-6dbf-47d6-ae32-4b0cea2a5c02.enc"
  ]
}
```

**Validaciones:**
- `userId`: 3-64 caracteres (alfanuméricos, guiones, guiones bajos)
- `fileNames`: Array de 1 a 100 archivos
- Cada fileName debe terminar en `.enc`, máximo 255 caracteres

**Límites:**
- **Máximo 100 archivos** por petición
- URLs expiran en **5 minutos**

**Response 200 (OK):**
```json
{
  "success": true,
  "count": 3,
  "data": [
    {
      "fileName": "13785622-523b-4c06-b6bd-d4ea0cf98a5d.enc",
      "url": "https://s3.eu-central-003.backblazeb2.com/lockly-secure-storage/...",
      "key": "pQZXhVkeutHcoR5ca9K0tw/13785622-523b-4c06-b6bd-d4ea0cf98a5d.enc",
      "expiresIn": 300
    },
    {
      "fileName": "4ce6b64d-7245-4b6c-823c-08966846bd15.enc",
      "url": "https://s3.eu-central-003.backblazeb2.com/lockly-secure-storage/...",
      "key": "pQZXhVkeutHcoR5ca9K0tw/4ce6b64d-7245-4b6c-823c-08966846bd15.enc",
      "expiresIn": 300
    },
    {
      "fileName": "8bfeca18-6dbf-47d6-ae32-4b0cea2a5c02.enc",
      "url": "https://s3.eu-central-003.backblazeb2.com/lockly-secure-storage/...",
      "key": "pQZXhVkeutHcoR5ca9K0tw/8bfeca18-6dbf-47d6-ae32-4b0cea2a5c02.enc",
      "expiresIn": 300
    }
  ]
}
```

**Response 400 (Validación fallida):**
```json
// Array vacío
{
  "success": false,
  "error": "fileNames array cannot be empty"
}

// Demasiados archivos
{
  "success": false,
  "error": "Maximum 100 files allowed per batch download request"
}

// Archivo inválido en el índice 2
{
  "success": false,
  "error": "FileName at index 2: fileName must end with .enc extension"
}
```

**Response 429 (Rate limit):**
```json
{
  "success": false,
  "error": "Too many requests, please try again later."
}
```

**Response 500 (Error del servidor):**
```json
{
  "success": false,
  "error": "Internal server error"
}
```

**Uso en Android:**
```kotlin
data class BatchDownloadRequest(
    val userId: String,
    val fileNames: List<String>
)

data class BatchDownloadResponse(
    val success: Boolean,
    val count: Int? = null,
    val data: List<DownloadPresignedUrl>? = null,
    val error: String? = null
)

data class DownloadPresignedUrl(
    val fileName: String,
    val url: String,
    val key: String,
    val expiresIn: Int
)

suspend fun downloadMultipleFiles(
    userId: String,
    fileNames: List<String>
): Result<List<File>> {
    return try {
        // Validar límite de 100 archivos
        if (fileNames.size > 100) {
            return Result.failure(Exception("Maximum 100 files per batch"))
        }

        // 1. Obtener URLs en batch (1 sola petición)
        val response = apiService.getBatchDownloadUrls(
            BatchDownloadRequest(userId, fileNames)
        )

        if (!response.success || response.data == null) {
            return Result.failure(Exception(response.error ?: "Unknown error"))
        }

        // 2. Descargar archivos en paralelo
        val downloadJobs = response.data.map { presignedUrl ->
            async(Dispatchers.IO) {
                downloadFromBucket(presignedUrl.url, presignedUrl.fileName)
            }
        }

        val downloadedFiles = downloadJobs.awaitAll()
        Result.success(downloadedFiles)

    } catch (e: HttpException) {
        when (e.code()) {
            400 -> Result.failure(ValidationException(e.message()))
            429 -> Result.failure(RateLimitException())
            else -> Result.failure(e)
        }
    }
}

private suspend fun downloadFromBucket(url: String, fileName: String): File {
    val request = Request.Builder()
        .url(url)
        .get()
        .build()

    return okHttpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw IOException("Download failed: ${response.code}")
        }

        // Guardar archivo en directorio temporal
        val file = File(context.cacheDir, fileName)
        file.outputStream().use { output ->
            response.body?.byteStream()?.copyTo(output)
        }
        file
    }
}

// Uso práctico: Descargar archivos seleccionados
suspend fun downloadSelectedFiles(userId: String, selectedFiles: List<FileMetadata>) {
    val fileNames = selectedFiles.map { it.fileName }

    // Mostrar progreso
    showProgressDialog("Descargando ${fileNames.size} archivos...")

    downloadMultipleFiles(userId, fileNames)
        .onSuccess { files ->
            hideProgressDialog()
            Toast.makeText(
                context,
                "Se descargaron ${files.size} archivos correctamente",
                Toast.LENGTH_SHORT
            ).show()
            // Procesar archivos descargados
            files.forEach { file ->
                // Desencriptar y guardar en ubicación final
                processDownloadedFile(file)
            }
        }
        .onFailure { error ->
            hideProgressDialog()
            when (error) {
                is ValidationException -> showError("Datos inválidos: ${error.message}")
                is RateLimitException -> showError("Demasiadas peticiones, intenta más tarde")
                else -> showError("Error al descargar: ${error.message}")
            }
        }
}
```

---

### DELETE /api/files/batch

Elimina múltiples archivos de un usuario en una sola operación.

**Request:**
```http
DELETE /api/files/batch
Content-Type: application/json

{
  "userId": "pQZXhVkeutHcoR5ca9K0tw",
  "fileNames": [
    "13785622-523b-4c06-b6bd-d4ea0cf98a5d.enc",
    "4ce6b64d-7245-4b6c-823c-08966846bd15.enc",
    "8bfeca18-6dbf-47d6-ae32-4b0cea2a5c02.enc"
  ]
}
```

**Validaciones:**
- `userId`: 3-64 caracteres (alfanuméricos, guiones, guiones bajos)
- `fileNames`: Array de 1 a 1000 archivos
- Cada fileName debe terminar en `.enc`

**Límites:**
- **Máximo 1000 archivos** por petición
- Si un archivo no existe, se reporta en `errors` pero no falla la operación completa

**Response 200 (OK - Todos exitosos):**
```json
{
  "success": true,
  "deleted": [
    "13785622-523b-4c06-b6bd-d4ea0cf98a5d.enc",
    "4ce6b64d-7245-4b6c-823c-08966846bd15.enc",
    "8bfeca18-6dbf-47d6-ae32-4b0cea2a5c02.enc"
  ],
  "errors": []
}
```

**Response 200 (OK - Con errores parciales):**
```json
{
  "success": true,
  "deleted": [
    "13785622-523b-4c06-b6bd-d4ea0cf98a5d.enc",
    "4ce6b64d-7245-4b6c-823c-08966846bd15.enc"
  ],
  "errors": [
    {
      "fileName": "archivo-no-existe.enc",
      "code": "NoSuchKey",
      "message": "The specified key does not exist"
    }
  ]
}
```

**Response 400 (Validación fallida):**
```json
// Array vacío
{
  "success": false,
  "error": "fileNames array cannot be empty"
}

// Demasiados archivos
{
  "success": false,
  "error": "Maximum 1000 files allowed per batch delete request"
}

// Nombre de archivo inválido en el índice 5
{
  "success": false,
  "error": "FileName at index 5: fileName must end with .enc extension"
}
```

**Response 429 (Rate limit):**
```json
{
  "success": false,
  "error": "Too many requests, please try again later."
}
```

**Response 500 (Error del servidor):**
```json
{
  "success": false,
  "error": "Internal server error"
}
```

**Uso en Android:**
```kotlin
data class BatchDeleteRequest(
    val userId: String,
    val fileNames: List<String>
)

data class BatchDeleteResponse(
    val success: Boolean,
    val deleted: List<String>? = null,
    val errors: List<DeleteError>? = null,
    val error: String? = null
)

data class DeleteError(
    val fileName: String,
    val code: String,
    val message: String
)

suspend fun deleteMultipleFiles(
    userId: String,
    fileNames: List<String>
): Result<BatchDeleteResult> {
    return try {
        // Validar límite de 1000 archivos
        if (fileNames.size > 1000) {
            return Result.failure(Exception("Maximum 1000 files per batch"))
        }

        val request = BatchDeleteRequest(userId, fileNames)
        val response = apiService.deleteBatchFiles(request)

        if (!response.success) {
            return Result.failure(Exception(response.error ?: "Unknown error"))
        }

        val result = BatchDeleteResult(
            deleted = response.deleted ?: emptyList(),
            failed = response.errors ?: emptyList()
        )

        Result.success(result)

    } catch (e: HttpException) {
        when (e.code()) {
            400 -> Result.failure(ValidationException(e.message()))
            429 -> Result.failure(RateLimitException())
            else -> Result.failure(e)
        }
    }
}

data class BatchDeleteResult(
    val deleted: List<String>,
    val failed: List<DeleteError>
)

// Uso para mostrar resultados en UI
fun showDeleteResults(result: BatchDeleteResult) {
    if (result.failed.isEmpty()) {
        Toast.makeText(
            context,
            "Se eliminaron ${result.deleted.size} archivos correctamente",
            Toast.LENGTH_SHORT
        ).show()
    } else {
        // Mostrar diálogo con detalles de errores
        val message = buildString {
            append("Eliminados: ${result.deleted.size}\n")
            append("Errores: ${result.failed.size}\n\n")
            result.failed.forEach { error ->
                append("• ${error.fileName}: ${error.message}\n")
            }
        }

        AlertDialog.Builder(context)
            .setTitle("Resultado de eliminación")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
```

---

## Rate Limiting

Todos los endpoints tienen límites de peticiones para prevenir abuso:

| Endpoint | Límite | Ventana de tiempo |
|----------|--------|-------------------|
| POST /api/upload-url | 50 peticiones | 15 minutos |
| POST /api/upload-urls/batch | 50 peticiones | 15 minutos |
| POST /api/download-url | 100 peticiones | 15 minutos |
| POST /api/download-urls/batch | 100 peticiones | 15 minutos |
| DELETE /api/files/batch | 50 peticiones | 15 minutos |
| GET /api/files/:userId | 30 peticiones | 5 minutos |
| GET /api/quota/:userId | 30 peticiones | 5 minutos |
| General (todos) | 100 peticiones | 15 minutos |

**Nota:** Las peticiones batch cuentan como **1 petición**, sin importar cuántos archivos incluyan.

**Manejo de Rate Limit en Android:**
```kotlin
class RateLimitException : Exception("Rate limit exceeded. Please try again later.")

suspend fun <T> withRateLimitRetry(
    maxRetries: Int = 3,
    initialDelayMs: Long = 1000,
    block: suspend () -> T
): Result<T> {
    var currentDelay = initialDelayMs
    repeat(maxRetries) { attempt ->
        try {
            return Result.success(block())
        } catch (e: HttpException) {
            if (e.code() == 429) {
                if (attempt < maxRetries - 1) {
                    delay(currentDelay)
                    currentDelay *= 2 // Exponential backoff
                } else {
                    return Result.failure(RateLimitException())
                }
            } else {
                return Result.failure(e)
            }
        }
    }
    return Result.failure(RateLimitException())
}

// Uso
suspend fun uploadFileWithRetry(userId: String, file: File) {
    withRateLimitRetry {
        getUploadUrl(userId, file)
    }.onSuccess { uploadData ->
        // Continuar con el upload
    }.onFailure { error ->
        // Mostrar error al usuario
    }
}
```

---

## Manejo de Errores Centralizado

**Recomendación:** Crear una clase centralizada para manejar todas las respuestas de la API.

```kotlin
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val exception: ApiException) : ApiResult<Nothing>()
}

sealed class ApiException(message: String) : Exception(message) {
    class ValidationError(message: String) : ApiException(message)
    class NotFound(message: String) : ApiException(message)
    class QuotaExceeded(message: String) : ApiException(message)
    class RateLimit(message: String) : ApiException(message)
    class ServerError(message: String) : ApiException(message)
    class NetworkError(message: String) : ApiException(message)
    class Unknown(message: String) : ApiException(message)
}

suspend fun <T> safeApiCall(
    apiCall: suspend () -> Response<T>
): ApiResult<T> {
    return try {
        val response = apiCall()
        if (response.isSuccessful && response.body() != null) {
            ApiResult.Success(response.body()!!)
        } else {
            val errorBody = response.errorBody()?.string()
            val errorMessage = parseErrorMessage(errorBody) ?: "Unknown error"

            val exception = when (response.code()) {
                400 -> ApiException.ValidationError(errorMessage)
                404 -> ApiException.NotFound(errorMessage)
                403 -> ApiException.QuotaExceeded(errorMessage)
                429 -> ApiException.RateLimit(errorMessage)
                in 500..599 -> ApiException.ServerError(errorMessage)
                else -> ApiException.Unknown(errorMessage)
            }

            ApiResult.Error(exception)
        }
    } catch (e: IOException) {
        ApiResult.Error(ApiException.NetworkError("Network error: ${e.message}"))
    } catch (e: Exception) {
        ApiResult.Error(ApiException.Unknown("Unexpected error: ${e.message}"))
    }
}

private fun parseErrorMessage(errorBody: String?): String? {
    return try {
        errorBody?.let {
            val json = JSONObject(it)
            json.optString("error")
        }
    } catch (e: Exception) {
        null
    }
}

// Uso
suspend fun uploadFile(userId: String, file: File) {
    when (val result = safeApiCall { apiService.getUploadUrl(request) }) {
        is ApiResult.Success -> {
            // Manejar éxito
            val uploadData = result.data
        }
        is ApiResult.Error -> {
            // Manejar error específico
            when (val exception = result.exception) {
                is ApiException.ValidationError ->
                    showError("Datos inválidos: ${exception.message}")
                is ApiException.QuotaExceeded ->
                    showQuotaExceededDialog()
                is ApiException.RateLimit ->
                    showError("Demasiadas peticiones, intenta más tarde")
                is ApiException.NetworkError ->
                    showError("Error de conexión: ${exception.message}")
                is ApiException.ServerError ->
                    showError("Error del servidor: ${exception.message}")
                else ->
                    showError("Error: ${exception.message}")
            }
        }
    }
}
```

---

## Configuración de Retrofit

**Ejemplo de configuración completa:**

```kotlin
object ApiClient {
    private const val BASE_URL = "https://your-api-domain.com/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: LocklyApiService = retrofit.create(LocklyApiService::class.java)
}

interface LocklyApiService {
    // Health
    @GET("health")
    suspend fun getHealth(): Response<HealthResponse>

    @GET("test-b2")
    suspend fun testB2Connection(): Response<B2TestResponse>

    // Upload
    @POST("api/upload-url")
    suspend fun getUploadUrl(@Body request: UploadUrlRequest): Response<UploadUrlResponse>

    @POST("api/upload-urls/batch")
    suspend fun getBatchUploadUrls(@Body request: BatchUploadRequest): Response<BatchUploadResponse>

    // Download
    @POST("api/download-url")
    suspend fun getDownloadUrl(@Body request: DownloadUrlRequest): Response<DownloadUrlResponse>

    @POST("api/download-urls/batch")
    suspend fun getBatchDownloadUrls(@Body request: BatchDownloadRequest): Response<BatchDownloadResponse>

    // Files
    @GET("api/files/{userId}")
    suspend fun getUserFiles(@Path("userId") userId: String): Response<FilesListResponse>

    @DELETE("api/files/batch")
    suspend fun deleteBatchFiles(@Body request: BatchDeleteRequest): Response<BatchDeleteResponse>

    // Quota
    @GET("api/quota/{userId}")
    suspend fun getUserQuota(@Path("userId") userId: String): Response<QuotaResponse>
}
```

---

## Validaciones de Datos

**Todas las validaciones que se realizan en el servidor:**

### userId
- Mínimo: 3 caracteres
- Máximo: 64 caracteres
- Permitidos: letras, números, guiones (-), guiones bajos (_)
- Ejemplo válido: `pQZXhVkeutHcoR5ca9K0tw`

### fileName
- Debe terminar en `.enc`
- Máximo: 255 caracteres
- Ejemplo válido: `13785622-523b-4c06-b6bd-d4ea0cf98a5d.enc`

### contentType
- Debe ser un MIME type válido
- Ejemplos: `application/octet-stream`, `image/jpeg`, `application/pdf`
- Default: `application/octet-stream`

**Función de validación en Android:**
```kotlin
object ValidationUtils {
    private val USER_ID_REGEX = "^[a-zA-Z0-9_-]{3,64}$".toRegex()
    private val FILE_NAME_REGEX = "^.{1,251}\\.enc$".toRegex()

    fun validateUserId(userId: String): Boolean {
        return USER_ID_REGEX.matches(userId)
    }

    fun validateFileName(fileName: String): Boolean {
        return fileName.length <= 255 && fileName.endsWith(".enc")
    }

    fun validateContentType(contentType: String): Boolean {
        val mimeTypeRegex = "^[a-zA-Z0-9]+/[a-zA-Z0-9\\-+.]+$".toRegex()
        return mimeTypeRegex.matches(contentType)
    }

    fun sanitizeFileName(originalName: String): String {
        // Generar nombre seguro con UUID
        val extension = originalName.substringAfterLast('.', "")
        return "${UUID.randomUUID()}.enc"
    }
}

// Uso antes de hacer peticiones
fun uploadFile(userId: String, file: File) {
    if (!ValidationUtils.validateUserId(userId)) {
        showError("ID de usuario inválido")
        return
    }

    val fileName = ValidationUtils.sanitizeFileName(file.name)
    // Continuar con el upload...
}
```

---

## Seguridad

### Encriptación Zero-Knowledge

La API está diseñada para no tener acceso al contenido de los archivos:

1. **Cliente Android encripta** los archivos antes de subirlos
2. **Servidor solo almacena** archivos encriptados (`.enc`)
3. **URLs pre-firmadas** permiten uploads/downloads directos a B2
4. **Servidor nunca ve** el contenido desencriptado

**Flujo de upload:**
```kotlin
suspend fun secureUpload(userId: String, file: File, password: String) {
    // 1. Encriptar archivo localmente
    val encryptedFile = EncryptionManager.encrypt(file, password)

    // 2. Obtener URL de upload
    val uploadUrlResult = getUploadUrl(userId, encryptedFile)

    uploadUrlResult.onSuccess { uploadData ->
        // 3. Subir directamente a B2 (sin pasar por el servidor)
        uploadToBucket(uploadData.url, encryptedFile)

        // 4. Eliminar archivo temporal encriptado
        encryptedFile.delete()
    }
}
```

**Flujo de download:**
```kotlin
suspend fun secureDownload(userId: String, fileName: String, password: String) {
    // 1. Obtener URL de download
    val downloadUrlResult = getDownloadUrl(userId, fileName)

    downloadUrlResult.onSuccess { downloadData ->
        // 2. Descargar archivo encriptado directamente desde B2
        val encryptedFile = downloadFromBucket(downloadData.url)

        // 3. Desencriptar localmente
        val decryptedFile = EncryptionManager.decrypt(encryptedFile, password)

        // 4. Eliminar archivo temporal encriptado
        encryptedFile.delete()

        // 5. Retornar archivo desencriptado
        return decryptedFile
    }
}
```

---

## Testing

### Ejemplo de pruebas unitarias:

```kotlin
class LocklyApiTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: LocklyApiService

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(LocklyApiService::class.java)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `test upload url success`() = runBlocking {
        // Mock response
        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setBody("""
                {
                  "success": true,
                  "data": {
                    "url": "https://example.com/upload",
                    "key": "user/file.enc",
                    "expiresIn": 300,
                    "quota": {
                      "used": 1000,
                      "limit": 10000,
                      "remaining": 9000,
                      "usedPercentage": 10.0
                    }
                  }
                }
            """.trimIndent())

        mockWebServer.enqueue(mockResponse)

        // Make request
        val request = UploadUrlRequest("test-user", "file.enc")
        val response = apiService.getUploadUrl(request)

        // Verify
        assertTrue(response.isSuccessful)
        assertEquals(true, response.body()?.success)
        assertEquals("https://example.com/upload", response.body()?.data?.url)
    }

    @Test
    fun `test quota exceeded`() = runBlocking {
        // Mock error response
        val mockResponse = MockResponse()
            .setResponseCode(403)
            .setBody("""
                {
                  "success": false,
                  "error": "Storage quota exceeded"
                }
            """.trimIndent())

        mockWebServer.enqueue(mockResponse)

        // Make request
        val request = UploadUrlRequest("test-user", "file.enc")
        val response = apiService.getUploadUrl(request)

        // Verify
        assertFalse(response.isSuccessful)
        assertEquals(403, response.code())
    }
}
```

---

## Mejores Prácticas

1. **Usar operaciones batch** cuando sea posible para mejor rendimiento
2. **Implementar reintentos con exponential backoff** para rate limiting
3. **Cachear información de cuota** para reducir peticiones
4. **Validar datos localmente** antes de enviar al servidor
5. **Implementar timeout adecuados** (30 segundos recomendado)
6. **Manejar errores de manera específica** según el código de estado
7. **Encriptar archivos antes de subir** (zero-knowledge)
8. **Eliminar archivos temporales** después de operaciones
9. **Mostrar progreso al usuario** durante uploads/downloads largos
10. **Implementar retry logic** para errores de red

---

## Changelog

### v1.0.0 (2025-10-14)

- Documentación completa de todos los endpoints
- Ejemplos de uso en Android/Kotlin
- Manejo de errores centralizado
- Validaciones y seguridad
- Rate limiting y mejores prácticas

---

## Soporte

Para más información, consulta:
- [Documentación de endpoints batch](./batch-endpoints.md)
- Documentación de Swagger: `http://your-api-domain/api-docs`
