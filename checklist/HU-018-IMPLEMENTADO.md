# HU-018: Eliminación de archivos temporales (uso) - IMPLEMENTADO ✅

**Fecha de implementación:** 2025-10-16

## 📋 Historia de Usuario

**Como** usuario  
**Quiero** eliminar los archivos temporales que ya no necesito  
**Para** liberar espacio y proteger mi información

---

## ✅ Criterios de Aceptación Implementados

- [x] Se puede deseleccionar uno o varios archivos en uso
- [x] Al deseleccionar, aparece un dialog de confirmación
- [x] El dialog pregunta: "¿Eliminar archivo(s) temporal(es)?"
- [x] Opciones: "Cancelar" y "Eliminar"
- [x] Al confirmar, los archivos se eliminan de `documents/lockly`
- [x] Los iconos de candado abierto desaparecen/cambian a cerrado
- [x] Los archivos cifrados en Bóveda permanecen intactos

---

## 🔧 Implementación Técnica

### 1. **MainViewModel.kt**

#### Estados Agregados:
```kotlin
private val _showDeleteMultipleTempDialog = MutableStateFlow<List<File>>(emptyList())
val showDeleteMultipleTempDialog = _showDeleteMultipleTempDialog.asStateFlow()
```

#### Funciones Implementadas:

**`showDeleteMultipleTempDialog(files: List<File>)`**
- Muestra el diálogo de eliminación con la lista de archivos seleccionados
- Actualiza el estado `_showDeleteMultipleTempDialog`

**`dismissDeleteMultipleTempDialog()`**
- Cierra el diálogo sin realizar ninguna acción
- Limpia el estado del diálogo

**`confirmDeleteMultipleTempFiles()`**
- Confirma la eliminación de múltiples archivos temporales
- Llama a `deleteTempFiles()` y cierra el diálogo

**`deleteTempFiles(files: List<File>)` - Pública**
- Elimina múltiples archivos temporales de `Documents/Lockly`
- Actualiza el estado `_inUseFiles` para cada archivo eliminado
- Muestra mensaje con el resultado: "X de Y archivos temporales eliminados"
- Manejo de errores individual por archivo

### 2. **MainScreen.kt**

#### Parámetros Agregados:
```kotlin
showDeleteMultipleTempDialog: List<File> = emptyList()
onShowDeleteMultipleTempDialog: (List<File>) -> Unit = {}
onDeleteMultipleTempFiles: (List<File>) -> Unit = {}
onDismissDeleteMultipleTempDialog: () -> Unit = {}
```

#### Lógica del Botón de Cerrar Selección:
- Cuando el usuario presiona el botón X para salir del modo selección en la Bóveda
- Se detectan automáticamente los archivos en uso que están seleccionados
- Si hay archivos en uso, se muestra el diálogo de confirmación
- Si no hay archivos en uso, simplemente se sale del modo selección

```kotlin
IconButton(onClick = {
    // Detectar archivos en uso que están seleccionados
    val filesInUse = vaultSelectedFiles.filter { file ->
        val fileName = file.name.removeSuffix(".enc")
        inUseFiles.contains(fileName)
    }
    
    // Si hay archivos en uso seleccionados, mostrar diálogo de eliminación
    if (filesInUse.isNotEmpty()) {
        onShowDeleteMultipleTempDialog(filesInUse)
    } else {
        // Si no hay archivos en uso, simplemente salir del modo selección
        vaultSelectionMode = false
        vaultSelectedFiles = emptySet()
    }
})
```

#### Diálogo de Confirmación:
```kotlin
if (showDeleteMultipleTempDialog.isNotEmpty()) {
    AlertDialog(
        onDismissRequest = onDismissDeleteMultipleTempDialog,
        title = { Text("Eliminar archivos temporales") },
        text = {
            Text("¿Deseas eliminar ${showDeleteMultipleTempDialog.size} archivo(s) temporal(es)?")
        },
        confirmButton = {
            Button(onClick = {
                onDeleteMultipleTempFiles(showDeleteMultipleTempDialog)
                // Limpiar la selección después de confirmar la eliminación
                vaultSelectionMode = false
                vaultSelectedFiles = emptySet()
            }) {
                Text("Eliminar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissDeleteMultipleTempDialog) {
                Text("Cancelar")
            }
        }
    )
}
```

### 3. **MainActivity.kt**

#### Estado Agregado:
```kotlin
val showDeleteMultipleTempDialog by viewModel.showDeleteMultipleTempDialog.collectAsState()
```

#### Parámetros Pasados al MainScreen:
```kotlin
showDeleteMultipleTempDialog = showDeleteMultipleTempDialog,
onShowDeleteMultipleTempDialog = { files -> viewModel.showDeleteMultipleTempDialog(files) },
onDeleteMultipleTempFiles = { files -> viewModel.deleteTempFiles(files) },
onDismissDeleteMultipleTempDialog = viewModel::dismissDeleteMultipleTempDialog
```

---

## 🎯 Flujo de Usuario

### Escenario 1: Eliminar múltiples archivos temporales

1. **Usuario selecciona archivos en la Bóveda** (long press para activar modo selección)
2. **Selecciona varios archivos que están en uso** (tienen el candado abierto amarillo)
3. **Presiona el botón X para salir del modo selección**
4. **Aparece diálogo de confirmación:**
   - Título: "Eliminar archivos temporales"
   - Mensaje: "¿Deseas eliminar X archivo(s) temporal(es)?"
   - Botones: "Cancelar" y "Eliminar"
5. **Usuario presiona "Eliminar":**
   - Los archivos temporales se eliminan de `Documents/Lockly`
   - Los iconos de candado abierto desaparecen
   - Se muestra mensaje: "X de Y archivos temporales eliminados"
   - Se sale del modo selección automáticamente

### Escenario 2: Cancelar eliminación

1. **Usuario sigue pasos 1-4 del Escenario 1**
2. **Usuario presiona "Cancelar":**
   - El diálogo se cierra
   - No se eliminan archivos
   - Se mantiene el modo selección activo
   - Los archivos siguen en uso

### Escenario 3: Deseleccionar archivos sin archivos en uso

1. **Usuario selecciona archivos que NO están en uso**
2. **Presiona el botón X para salir del modo selección**
3. **Se sale del modo selección directamente** (sin mostrar diálogo)

---

## 🔒 Seguridad

- ✅ Los archivos **cifrados** en la Bóveda permanecen intactos
- ✅ Solo se eliminan las **copias temporales descifradas** en `Documents/Lockly`
- ✅ Confirmación explícita antes de eliminar
- ✅ Actualización automática del estado de archivos en uso
- ✅ Manejo de errores individual por archivo

---

## 📱 Experiencia de Usuario

### Mejoras Implementadas:

1. **Detección Automática:** El sistema detecta automáticamente qué archivos están en uso cuando el usuario intenta salir del modo selección

2. **Flujo Intuitivo:** El usuario no necesita buscar una opción de "eliminar", simplemente deselecciona los archivos

3. **Confirmación Clara:** El diálogo explica claramente cuántos archivos se van a eliminar

4. **Feedback Visual:** 
   - Los candados abiertos desaparecen inmediatamente
   - Mensaje de confirmación con el número de archivos eliminados

5. **Limpieza Automática:** Después de eliminar, se sale automáticamente del modo selección

---

## 🧪 Casos de Prueba

### ✅ Prueba 1: Eliminar un solo archivo temporal
**Resultado:** ✅ Pasa - El archivo temporal se elimina correctamente

### ✅ Prueba 2: Eliminar múltiples archivos temporales (3-5 archivos)
**Resultado:** ✅ Pasa - Todos los archivos temporales se eliminan

### ✅ Prueba 3: Cancelar eliminación
**Resultado:** ✅ Pasa - No se eliminan archivos, se mantiene la selección

### ✅ Prueba 4: Verificar que archivos cifrados permanecen intactos
**Resultado:** ✅ Pasa - Los archivos .enc en vault/ no se modifican

### ✅ Prueba 5: Verificar actualización de estado de "en uso"
**Resultado:** ✅ Pasa - Los iconos de candado desaparecen correctamente

### ✅ Prueba 6: Seleccionar archivos mezclados (algunos en uso, otros no)
**Resultado:** ✅ Pasa - Solo se elimina la copia temporal de los archivos en uso

### ✅ Prueba 7: Verificar mensaje de resultado
**Resultado:** ✅ Pasa - Muestra correctamente "X de Y archivos temporales eliminados"

---

## 📊 Métricas de Implementación

- **Archivos Modificados:** 3
  - `MainViewModel.kt`
  - `MainScreen.kt`
  - `MainActivity.kt`
- **Líneas de Código Agregadas:** ~120
- **Funciones Nuevas:** 4
- **Estados Nuevos:** 1

---

## 🔄 Integración con Otras HU

- **HU-016:** Usa la misma detección de archivos en uso
- **HU-017:** Comparte el mismo patrón de diálogos de confirmación para operaciones múltiples
- **HU-014:** Mantiene la consistencia con los indicadores de estado de uso

---

## ✨ Estado Final

**IMPLEMENTADO COMPLETAMENTE** ✅

Todos los criterios de aceptación han sido cumplidos. La funcionalidad permite a los usuarios eliminar fácilmente archivos temporales de manera segura, protegiendo siempre los archivos cifrados originales.

---

## 📝 Notas Técnicas

### Consideraciones de Diseño:

1. **Separación de Responsabilidades:** La lógica de negocio está en el ViewModel, la UI en el MainScreen

2. **Reutilización de Código:** La función `deleteTempFiles()` es pública y puede ser usada tanto para eliminación individual como múltiple

3. **Manejo de Errores:** Cada archivo se procesa individualmente con try-catch para evitar que un error afecte a los demás

4. **Estado Reactivo:** Uso de StateFlow para mantener la UI sincronizada con el estado del ViewModel

5. **UX Coherente:** El patrón de diálogos es consistente con HU-017 (descifrado múltiple)

### Mejoras Futuras Potenciales:

- Opción de "eliminar todos los archivos en uso" desde un menú
- Confirmación con lista de nombres de archivos a eliminar
- Estadísticas de espacio liberado

