# HU-016: Selección de un solo archivo para usar - ANÁLISIS

**Fecha de revisión:** 2025-10-15

---

## Descripción

**Como** usuario  
**Quiero** seleccionar un archivo cifrado  
**Para** usarlo temporalmente

---

## Análisis de Criterios de Aceptación

### 1. Al hacer clic/long press en un archivo, se selecciona

**Estado:** IMPLEMENTADO

- El clic en archivo está implementado en `FileListItem`
- En modo Bóveda, al hacer clic llama a `onDecryptAndOpenFile`
- No requiere long press, funciona con clic simple
- El archivo no entra en "modo selección", sino que inicia directamente el descifrado

---

### 2. El archivo seleccionado muestra un indicador visual

**Estado:** IMPLEMENTADO PARCIALMENTE

- Durante el descifrado se agrega al set `_creatingTempFile`
- Falta: No hay indicador visual de "cargando" o "procesando" en la UI durante el descifrado
- El indicador visual solo aparece DESPUÉS del descifrado (candado amarillo)

---

### 3. Se inicia automáticamente el proceso de descifrado

**Estado:** IMPLEMENTADO

- Al hacer clic, se llama a `onVaultFileClick()` que verifica si ya está en uso
- Si no está en uso, llama a `decryptAndOpenFile(file)`
- El descifrado se ejecuta automáticamente sin pasos adicionales
- Usa `VaultManager.decryptStream()` para descifrar el archivo

---

### 4. El archivo descifrado se copia a `documents/lockly`

**Estado:** IMPLEMENTADO

- Se define `publicTempDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Lockly")`
- El archivo descifrado se guarda: `val tempFile = File(publicTempDir, VaultManager.getOriginalFileName(file))`
- La ruta corresponde a `/storage/emulated/0/Documents/Lockly/`
- El archivo se crea usando `FileOutputStream(tempFile)`

---

### 5. El icono cambia a candado abierto amarillo

**Estado:** IMPLEMENTADO

- Después del descifrado exitoso, se actualiza: `_inUseFiles.update { it + tempFile.name }`
- En `FileListItem`, verifica: `val isFileInUse = inUseFiles.contains(displayName)`
- Si está en uso, muestra `Icon(Icons.Default.LockOpen)` con `tint = inUseColor`
- El color amarillo está definido: `val inUseColor = Color(0xFFFFA000)`

---

### 6. El archivo es accesible desde otras apps

**Estado:** IMPLEMENTADO

- El archivo se guarda en directorio público: `Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)`
- Se ejecuta `MediaScannerConnection.scanFile()` para indexar el archivo
- Esto permite que otras apps detecten el archivo
- El archivo está en formato descifrado y puede abrirse normalmente

---

## Resumen

**Estado General:** IMPLEMENTADO con 1 aspecto parcial

### Implementado (5/6):
- ✅ Clic en archivo funciona
- ✅ Descifrado automático
- ✅ Guardado en `documents/lockly`
- ✅ Candado amarillo para archivos en uso
- ✅ Accesible desde otras apps

### Implementado Parcialmente (1/6):
- ⚠️ Falta indicador visual durante el proceso de descifrado (solo muestra snackbar al finalizar)

### No Implementado (0/6):
- Ninguno

---

## Tabla de Estado

| Criterio | Estado | Detalles |
|----------|--------|----------|
| Clic/long press selecciona | ✅ Implementado | Clic simple inicia descifrado |
| Indicador visual | ⚠️ Parcial | Falta indicador durante proceso, solo después |
| Descifrado automático | ✅ Implementado | `decryptAndOpenFile()` se ejecuta automáticamente |
| Copia a documents/lockly | ✅ Implementado | Usa `publicTempDir` correctamente |
| Candado amarillo | ✅ Implementado | Color #FFA000 con icono LockOpen |
| Accesible desde otras apps | ✅ Implementado | MediaScanner + directorio público |

---

## Aspectos a Mejorar (Opcional)

1. **Indicador visual durante descifrado:** Agregar un indicador de progreso circular o similar mientras se descifra
2. **Feedback táctil:** Considerar vibración o animación al hacer clic
3. **Diálogo de confirmación:** Opcionalmente, confirmar antes de descifrar archivos grandes

---

**Estado Final:** ✅ IMPLEMENTADO (con mejora recomendada en indicador visual)

**Archivos relevantes:**
- `/app/src/main/java/com/developermx/lockly/MainViewModel.kt` (líneas 621-668, 735-741)
- `/app/src/main/java/com/developermx/lockly/screens/MainScreen.kt` (FileListItem)

