# HU-007: Verificación de archivos en la nube al primer inicio - ANÁLISIS DE IMPLEMENTACIÓN

**Fecha de análisis:** 2025-10-15
**Estado general:** ⚠️ PARCIALMENTE IMPLEMENTADO

---

## Descripción de la Historia de Usuario

**Como** usuario que ingresa a la app
**Quiero** que la app verifique si tengo archivos en la nube
**Para** sincronizarlos con el dispositivo actual

---

## Análisis de Criterios de Aceptación

### ✅ Criterio 1: La app verifica archivos en la nube usando el userId

**Estado:** IMPLEMENTADO

**Ubicación en el código:**
- `MainViewModel.kt:101-163` - Función `syncCloudFiles()`
- `MainViewModel.kt:119` - Llamada al API

**Descripción:**
La app obtiene el userId del usuario autenticado y realiza una petición al API para obtener la lista de archivos en la nube. La implementación utiliza el endpoint `GET /api/files/{userId}` correctamente.

**Flujo implementado:**
1. Se obtiene el userId usando `VaultManager.getUserId(getApplication())` (línea 104)
2. Se valida que el userId no sea null (líneas 105-108)
3. Se realiza la petición al API: `ApiClient.apiService.getCloudFiles(userId)` (línea 119)
4. Se procesa la respuesta y se filtran archivos válidos (línea 125)

---

### ❌ Criterio 2: Esta verificación ocurre solo en el primer inicio después de la autenticación

**Estado:** NO IMPLEMENTADO

**Qué falta:**
La función `syncCloudFiles()` se ejecuta cada vez que se inicia el `MainViewModel` (línea 94 en el bloque `init`). No existe ninguna lógica de persistencia para determinar si es el "primer inicio después de la autenticación" o un inicio subsecuente.

**Comportamiento actual:**
- La sincronización se ejecuta CADA VEZ que se crea el ViewModel
- No hay diferenciación entre primer inicio y subsecuentes inicios
- Esto genera llamadas al API innecesarias en cada apertura de la app

**Qué se necesita implementar:**
1. Agregar una preferencia persistente (SharedPreferences) con una bandera como `first_sync_completed`
2. Verificar esta bandera antes de llamar a `syncCloudFiles()`
3. Actualizar la bandera a `true` después de la primera sincronización exitosa
4. Considerar resetear la bandera solo cuando:
   - El usuario cierra sesión
   - El usuario desinstala/reinstala la app
   - El usuario cambia de dispositivo

**Observación:**
Aunque la verificación se ejecuta en cada inicio, el banner de sincronización solo se muestra cuando no hay archivos locales (líneas 129-135), lo que mitiga parcialmente el problema de UX pero no elimina las llamadas innecesarias al API.

---

### ✅ Criterio 3: Se muestra un indicador de carga durante la verificación

**Estado:** IMPLEMENTADO

**Ubicación en el código:**
- `MainViewModel.kt:52-53` - Definición del StateFlow `_isSyncing`
- `MainViewModel.kt:117` - Activación del indicador: `_isSyncing.value = true`
- `MainViewModel.kt:159` - Desactivación del indicador: `_isSyncing.value = false` (en bloque finally)

**Descripción:**
Se implementó correctamente un StateFlow para controlar el estado de carga durante la sincronización. El estado se actualiza al inicio de la operación y se desactiva en el bloque `finally`, garantizando que siempre se limpie incluso si ocurre un error.

**Flujo implementado:**
1. Al iniciar la sincronización: `_isSyncing.value = true`
2. Durante la sincronización: el estado permanece en `true`
3. Al finalizar (exitoso o con error): `_isSyncing.value = false`

**Nota:** El indicador está disponible para ser usado en la UI a través del StateFlow expuesto públicamente `isSyncing`.

---

### ⚠️ Criterio 4: Si hay archivos, se activa el flujo de sincronización (HU-008)

**Estado:** IMPLEMENTADO PARCIALMENTE

**Ubicación en el código:**
- `MainViewModel.kt:129-145` - Lógica para determinar archivos faltantes
- `MainViewModel.kt:137` - Asignación de archivos faltantes: `_missingCloudFiles.value = missingFiles`
- `MainViewModel.kt:165-260` - Función `downloadMissingFiles()` para descargar archivos

**Qué está implementado:**
1. La app detecta si hay archivos en la nube
2. Compara archivos en la nube con archivos locales
3. Identifica archivos faltantes
4. SOLO muestra archivos para sincronizar si NO hay archivos locales (primera vez en el dispositivo)
5. Expone los archivos faltantes a través del StateFlow `_missingCloudFiles`

**Qué está parcialmente implementado:**
- La lógica para descargar archivos existe (`downloadMissingFiles()`), pero debe ser invocada manualmente por el usuario
- No se "activa automáticamente" el flujo, sino que se prepara para que el usuario decida

**Comportamiento actual:**
```kotlin
val missingFiles = if (hasLocalFiles) {
    // Ya usó la app antes - NO mostrar banner
    emptyList()
} else {
    // Primera vez en este dispositivo - mostrar todos los archivos de la nube
    cloudFiles
}
```

**Observación:**
Este criterio depende directamente de HU-008 (Notificación de archivos disponibles para sincronizar), que es donde se implementa el banner y el CTA para que el usuario active la sincronización. La implementación actual prepara correctamente el estado para HU-008.

---

### ❌ Criterio 5: La verificación NO se repite en inicios subsecuentes

**Estado:** NO IMPLEMENTADO

**Qué falta:**
No existe persistencia para evitar que la verificación se ejecute en cada inicio de la app. La función `syncCloudFiles()` se llama en el `init` del ViewModel, ejecutándose cada vez que se crea la instancia.

**Comportamiento actual:**
- La verificación se ejecuta cada vez que se inicia el ViewModel
- Se realizan llamadas al API en cada apertura de la app
- No hay mecanismo para recordar que ya se verificó previamente

**Impacto:**
1. **Consumo de cuota de API:** Llamadas innecesarias en cada inicio
2. **Uso de datos:** Consumo de datos móviles del usuario
3. **Rendimiento:** Retraso al iniciar la app mientras se completa la verificación

**Qué se necesita implementar:**
1. Persistencia local para almacenar:
   - Bandera de "primera sincronización completada"
   - Timestamp de la última verificación
   - Lista de archivos conocidos en la nube (para comparar cambios)
2. Lógica condicional en `init` para verificar si ya se sincronizó
3. Opción para "forzar sincronización" manualmente desde la UI cuando el usuario lo requiera

**Observación importante:**
Aunque la verificación se ejecuta repetidamente, el banner de sincronización solo se muestra cuando `hasLocalFiles == false` (líneas 129-135), lo que evita molestar al usuario con notificaciones repetidas. Sin embargo, esto no resuelve el problema de las llamadas innecesarias al API.

**Alternativa actual:**
El código actual usa la presencia de archivos locales como proxy para "primera vez en el dispositivo":
- Si hay archivos locales → No mostrar banner (usuario ya usó la app)
- Si no hay archivos locales → Mostrar todos los archivos de la nube

Esta solución es funcional para la UX pero no eficiente para el uso de recursos (API, datos, batería).

---

## Resumen de Implementación

| Criterio | Estado | Observaciones |
|----------|--------|---------------|
| 1. Verifica archivos usando userId | ✅ IMPLEMENTADO | Funcionando correctamente con el API |
| 2. Solo en primer inicio | ❌ NO IMPLEMENTADO | Se ejecuta en cada inicio del ViewModel |
| 3. Indicador de carga | ✅ IMPLEMENTADO | StateFlow `isSyncing` funcional |
| 4. Activa flujo de sincronización | ⚠️ PARCIAL | Prepara datos, pero no activa automáticamente |
| 5. No se repite en subsecuentes | ❌ NO IMPLEMENTADO | Se ejecuta cada vez, sin persistencia |

---

## Estado General: ⚠️ PARCIALMENTE IMPLEMENTADO

**Porcentaje estimado de implementación:** 50%

**Funcionalidad crítica implementada:**
- ✅ Verificación de archivos en la nube
- ✅ Uso correcto del userId
- ✅ Indicador de carga durante la verificación
- ✅ Detección de archivos faltantes

**Funcionalidad faltante:**
- ❌ Persistencia para "primer inicio"
- ❌ Prevención de verificaciones repetidas
- ⚠️ Activación automática del flujo de sincronización (depende de HU-008)

---

## Recomendaciones Técnicas

### 1. Implementar SharedPreferences para persistencia

```kotlin
// Ejemplo de implementación sugerida
private fun shouldRunInitialSync(): Boolean {
    val prefs = getApplication<Application>().getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
    return !prefs.getBoolean("initial_sync_completed", false)
}

private fun markInitialSyncCompleted() {
    val prefs = getApplication<Application>().getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
    prefs.edit {
        putBoolean("initial_sync_completed", true)
        putLong("last_sync_timestamp", System.currentTimeMillis())
    }
}
```

### 2. Modificar el bloque init

```kotlin
init {
    if (hasPermissions) {
        loadUnencryptedFiles()
    }
    loadVaultFiles()
    loadInUseFiles()

    // Solo sincronizar si es el primer inicio o si pasó suficiente tiempo
    if (shouldRunInitialSync()) {
        syncCloudFiles()
    }
}
```

### 3. Agregar opción de sincronización manual

Permitir al usuario forzar una sincronización desde la UI cuando lo necesite:

```kotlin
fun forceCloudSync() {
    syncCloudFiles()
}
```

### 4. Considerar caché con tiempo de expiración

En lugar de solo verificar "primera vez", implementar un caché con tiempo de expiración (por ejemplo, 1 hora):

```kotlin
private fun shouldRefreshCloudFiles(): Boolean {
    val prefs = getApplication<Application>().getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
    val lastSync = prefs.getLong("last_sync_timestamp", 0)
    val oneHourInMillis = 60 * 60 * 1000
    return System.currentTimeMillis() - lastSync > oneHourInMillis
}
```

---

## Dependencias

**Esta HU depende de:**
- VaultManager (para obtener userId) ✅ Implementado
- ApiClient y LocklyApiService ✅ Implementado
- Autenticación del usuario ✅ Implementado (HU-001 a HU-006)

**Esta HU es prerequisito de:**
- HU-008: Notificación de archivos disponibles para sincronizar
- HU-009: Descarga de archivos en lotes (background)
- HU-010: Finalización de sincronización

---

## Archivos Principales Involucrados

1. **MainViewModel.kt**
   - Líneas 94: Llamada en `init`
   - Líneas 101-163: Función `syncCloudFiles()`
   - Líneas 52-53: StateFlow `_isSyncing`
   - Líneas 56-57: StateFlow `_missingCloudFiles`
   - Líneas 165-260: Función `downloadMissingFiles()`

2. **LocklyApiService.kt**
   - Líneas 78-79: Endpoint `getCloudFiles()`

3. **ApiClient.kt**
   - Configuración del cliente HTTP (no leído en este análisis)

---

## Notas Adicionales

**Fortalezas de la implementación actual:**
1. Manejo robusto de errores con try-catch y finally
2. Logging detallado para debugging
3. Lógica inteligente para evitar mostrar el banner repetidamente usando archivos locales como proxy
4. Uso correcto de coroutines y StateFlow

**Debilidades:**
1. Falta de persistencia para controlar la primera sincronización
2. Llamadas innecesarias al API en cada inicio
3. No hay mecanismo de caché o tiempo de expiración
4. Dependencia implícita de HU-008 sin documentación clara

**Impacto en la experiencia del usuario:**
- ⚠️ Posible consumo excesivo de datos móviles
- ⚠️ Retraso al iniciar la app debido a llamadas al API
- ✅ El usuario no ve banners repetitivos gracias a la lógica de archivos locales
- ✅ Indicador de carga visible durante la verificación

---

## Conclusión

La HU-007 está **parcialmente implementada** con 3 de 5 criterios completamente funcionales. Los criterios faltantes están relacionados principalmente con la **falta de persistencia** para controlar cuándo y con qué frecuencia se ejecuta la verificación de archivos en la nube.

La implementación actual es **funcional desde el punto de vista del usuario** (no ve banners repetitivos), pero **ineficiente desde el punto de vista de recursos** (llamadas innecesarias al API, consumo de datos, batería).

**Acción recomendada:** Implementar persistencia usando SharedPreferences para marcar la primera sincronización y agregar lógica de caché con tiempo de expiración para optimizar el uso de recursos.
