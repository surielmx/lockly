# Checklist de Implementación - Lockly App

## 📱 Autenticación e Inicio

### Historia 1: Contraseña Maestra y Biometría
- [ ] App muestra pantalla de bienvenida/splash
- [ ] Primera vez: solicitar creación de contraseña maestra
- [ ] Validar contraseña maestra (mínimo 8 caracteres)
- [ ] Guardar hash de contraseña en EncryptedSharedPreferences
- [ ] Después de contraseña: solicitar configuración biométrica
- [ ] Implementar BiometricPrompt para autenticación
- [ ] Guardar preferencia de biometría habilitada
- [ ] Siguientes aperturas: mostrar BiometricPrompt primero
- [ ] Si biometría falla: permitir ingresar contraseña maestra como fallback
- [ ] Validar contraseña maestra contra hash guardado

---

## 🆔 Generación de User ID

### Historia 2: User ID Determinístico
- [ ] Crear clase `UserIdGenerator.kt`
- [ ] Implementar función `generateUserId(masterPassword: String): String`
- [ ] Usar PBKDF2 con 100,000+ iteraciones
- [ ] Usar salt fijo derivado del email o identificador único del dispositivo
- [ ] Generar userId de 64 caracteres (hex string de 32 bytes)
- [ ] Guardar userId en EncryptedSharedPreferences
- [ ] Verificar que mismo password genera mismo userId
- [ ] Test: Diferentes passwords generan diferentes userIds
- [ ] Test: Mismo password en diferentes sesiones = mismo userId
- [ ] Mostrar userId en pantalla de ajustes (para debug/verificación)

---

## 🗂️ Navegación y Tabs

### Historia 3: Estructura de Tabs
- [ ] Implementar TabLayout con 2 tabs
- [ ] Tab 1: "Bóveda" (archivos cifrados)
- [ ] Tab 2: "Explorador" (carpetas del dispositivo)
- [ ] ViewPager2 para navegación entre tabs
- [ ] Iconos apropiados para cada tab
- [ ] Estado de tab persiste al rotar pantalla

---

## 📂 Tab Explorador

### Historia 4: Listado de Carpetas
- [ ] Solicitar permisos READ_EXTERNAL_STORAGE / READ_MEDIA_IMAGES
- [ ] Escanear carpetas del dispositivo usando MediaStore
- [ ] Debe estar el breadcrumb cuando se navegue entre carpetas
- [ ] El color del breadcrumb sera blanco, excepto el que se encuentra actualmente viendo (color azul)
- [ ] Obtener lista de carpetas con contenido multimedia
- [ ] Mostrar nombre de carpeta
- [ ] Mostrar número de items en cada carpeta
- [ ] Ordenar carpetas alfabéticamente
- [ ] RecyclerView con adapter para carpetas

### Historia 5: Estado Visual de Carpetas
- [ ] Carpetas vacías (0 items): mostrar con opacidad reducida (alpha 0.5)
- [ ] Carpetas vacías: deshabilitar click (isEnabled = false)
- [ ] Carpetas con items: mostrar con opacidad normal
- [ ] Carpetas con items: habilitar click
- [ ] Visual feedback al tocar carpeta habilitada

### Historia 6: Vista de Grid para Imágenes
- [ ] Al hacer click en carpeta: abrir vista de contenido
- [ ] Si carpeta tiene imágenes: usar GridLayoutManager (3 columnas)
- [ ] Cargar miniaturas con Glide o Coil
- [ ] Mostrar checkbox en cada imagen para selección múltiple
- [ ] Si carpeta tiene otros archivos: usar LinearLayoutManager o LazyColumn (lista) para optimizar listas largas
- [ ] Mostrar icono según tipo de archivo (video, audio, documento)
- [ ] Permitir selección múltiple con checkboxes

### Historia 7: Selección y Cifrado de Archivos
- [ ] Toolbar con contador de archivos seleccionados
- [ ] Botón FAB o ActionMode para iniciar cifrado
- [ ] Validar que hay archivos seleccionados
- [ ] Mostrar diálogo de confirmación antes de cifrar
- [ ] Iniciar proceso de cifrado al confirmar

### Historia 8: Proceso de Cifrado
- [ ] Crear WorkManager worker para cifrado (`EncryptWorker.kt`)
- [ ] Por cada archivo seleccionado:
    - [ ] Leer archivo original
    - [ ] Generar UUID para nombre cifrado
    - [ ] Cifrar con VaultManager usando master password
    - [ ] Guardar archivo cifrado en /data/data/com.developermx.lockly/files/ o /data/data/com.developermx.lockly/cache/
    - [ ] Cifrar metadata (nombre original, tipo MIME, tamaño, fecha)
    - [ ] Actualizar progreso
- [ ] Mostrar notificación con progreso de cifrado
- [ ] Marcar archivo como "cifrado" en base de datos local

### Historia 9: Subida a la Nube
- [ ] Crear WorkManager worker para upload (`UploadWorker.kt`)
- [ ] Después de cifrar exitosamente:
    - [ ] Solicitar presigned URL a API backend
    - [ ] Subir archivo cifrado a B2 usando presigned URL
    - [ ] Incluir metadata cifrada en B2 tags
    - [ ] Verificar upload exitoso (status 200)
    - [ ] Marcar archivo como "sincronizado" en DB
- [ ] Mostrar notificación con progreso de upload
- [ ] Manejar retry automático si falla upload (max 3 intentos)

### Historia 10: Notificaciones de Estado
- [ ] Notificación de éxito: "X archivos cifrados y subidos correctamente"
- [ ] Notificación de éxito parcial: "X archivos cifrados, Y pendientes de subir"
- [ ] Notificación de error cifrado: "Error al cifrar archivo [nombre]"
- [ ] Notificación de error upload: "Error al subir archivo [nombre]"
- [ ] Acción en notificación para reintentar upload
- [ ] Vibración/sonido al completar proceso

### Historia 11: Eliminación de Archivo Original
- [ ] Después de cifrado y upload exitoso:
- [ ] Mostrar diálogo: "¿Eliminar archivo original?"
- [ ] Botones: "Conservar" y "Eliminar"
- [ ] Al confirmar: eliminar archivo original usando MediaStore
- [ ] Notificar al usuario: "Archivo original eliminado"
- [ ] Si el usuario conserva: mantener archivo original peor avisar al usuario que el archivo estará expuesto
- [ ] Log de archivos eliminados (para auditoría)

---

## 🔐 Tab Bóveda

### Historia 12: Lista de Archivos Cifrados
- [ ] Listar archivos archivos encriptados
- [ ] RecyclerView con lista de archivos
- [ ] Mostrar nombre original
- [ ] Mostrar icono según tipo de archivo
- [ ] Mostrar fecha de cifrado
- [ ] Debe estar el breadcrumb cuando se navegue entre carpetas
- [ ] El color del breadcrumb sera blanco, excepto el que se encuentra actualmente viendo (color azul)

### Historia 13: Badges de Estado
- [ ] Badge 1 - Cifrado correcto: ✅ icono verde o "Seguro"
- [ ] Badge 2 - Pendiente de subir: ⏳ icono amarillo  de advertendia o "Subiendo..."
- [ ] Badge 3 - Error de upload: ⚠️ icono rojo o "Error"
- [ ] Badge 4 - Sincronizado: ☁️ icono azul o "En la nube"
- [ ] Badge 4 - Archivo cifrado recientemente: ☁️ punto azul
- [ ] Actualizar badges en tiempo real según estado
- [ ] Tooltip al mantener presionado badge

### Historia 14: Dialog de Acciones de Archivo
- [ ] Al hacer click en archivo: mostrar Dialog
- [ ] Mostrar nombre del archivo
- [ ] Botón "Cancelar": cerrar dialog
- [ ] Botón "Usar": descifrar y copiar a Documents/lockly
- [ ] Botón "Subir a la nube": reintentar upload (solo si pendiente)
- [ ] Si archivo ya está en uso: cambiar texto de "Usar" a "Detener uso"
- [ ] Deshabilitar botones durante operación

### Historia 15: Uso Temporal de Archivo
- [ ] Crear directorio Documents/lockly si no existe
- [ ] Al hacer click en "Usar":
    - [ ] Descifrar archivo con VaultManager
    - [ ] Copiar archivo descifrado a Documents/lockly/
    - [ ] Actualizar UI con icono de candado abierto 🔓 cuando el archivo esta en uso
    - [ ] Notificar: "Archivo disponible en Documents/lockly"
    - [ ] Abrir archivo con Intent chooser (opcional)
- [ ] Actualizar badge en lista inmediatamente

### Historia 16: Eliminación de Archivo Temporal
- [ ] Al hacer click en archivo en uso:
- [ ] Mostrar diálogo: "Este archivo está en uso"
- [ ] Mensaje: "¿Eliminar copia temporal?"
- [ ] Botones: "Cancelar" y "Eliminar"
- [ ] Al confirmar:
    - [ ] Eliminar archivo de Documents/lockly/
    - [ ] Actualizar UI, se elimina icono de candado abierto 🔒
    - [ ] Notificar: "Copia temporal eliminada"
- [ ] Actualizar badge inmediatamente

### Historia 17: Indicador Visual de Archivo en Uso
- [ ] Icono de candado abierto 🔓 cuando esta en uso
- [ ] Color diferente para archivos en uso (ej: texto amarillo)
- [ ] Animación al cambiar estado (opcional)
- [ ] Tooltip: "En uso" o "Cifrado"

---

## 🔄 Recuperación y Sincronización

### Historia 18: Reinstalación de App
- [ ] Al abrir app por primera vez en nuevo dispositivo:
- [ ] Detectar si es primera instalación
- [ ] Solicitar contraseña maestra
- [ ] Generar userId a partir de contraseña
- [ ] El usuario debe poder recuperar los archivos (descargarlos y listarlos en la boveda) (cuando estos existan)
- [ ] Mostrar mensaje: "Sincronizando archivos..."
- [ ] Si usuario ya tenia archivos en la nube, se debe poder descrifrar los archivos (usar) cuando estos se muestren en la boveda

### Historia 19: Validación de Archivos en la Nube
- [ ] Solicitar lista de archivos del usuario a API
- [ ] API retorna lista con metadata cifrada
- [ ] Descargar metadata de cada archivo
- [ ] Descifrar metadata localmente para obtener nombres originales
- [ ] Comparar archivos cloud vs archivos en DB local
- [ ] Identificar archivos que existen en cloud pero NO en local

### Historia 20: Descarga de Archivos Faltantes
- [ ] Crear lista de archivos pendientes de descargar
- [ ] Mostrar diálogo: "Se encontraron X archivos en la nube"
- [ ] Opción: "Descargar todos" o "Seleccionar"
- [ ] Crear WorkManager worker para download (`DownloadWorker.kt`)
- [ ] Por cada archivo a descargar:
    - [ ] Solicitar presigned download URL a API
    - [ ] Descargar archivo cifrado de B2
    - [ ] Guardar en directorio interno app
    - [ ] Insertar registro en DB local
    - [ ] Marcar como `syncStatus = SYNCED`
- [ ] Mostrar notificación con progreso de descarga
- [ ] Notificar al completar: "X archivos descargados"

### Historia 21: Sincronización Automática
- [ ] Implementar SyncWorker que corre periódicamente
- [ ] Verificar nuevos archivos en la nube cada 24 horas
- [ ] Subir archivos pendientes si hay conexión
- [ ] Notificar al usuario de cambios sincronizados
- [ ] Opción en ajustes para habilitar/deshabilitar sync automático

---

## 🌐 Integración con Backend API

### Historia 22: Cliente API con Retrofit
- [ ] Crear interface `CloudApiClient.kt`
- [ ] Endpoints:
    - `POST /api/upload-url` → genera presigned upload URL
    - `POST /api/download-url` → genera presigned download URL
    - `GET /api/files/:userId` → lista archivos del usuario (revisar para que se necesita)
- [ ] Modelos de request/response
- [ ] Configurar OkHttpClient con timeouts
- [ ] Logging interceptor para debug
- [ ] Manejo de errores HTTP (401, 403, 429, 500)
- [ ] Retry automático con exponential backoff

### Historia 23: Manejo de Presigned URLs
- [ ] Solicitar presigned URL antes de cada upload/download
- [ ] Validar que URL no haya expirado (< 5 minutos)
- [ ] Usar OkHttp para PUT/GET directo a B2
- [ ] No pasar archivos por backend API
- [ ] Tracking de progreso con ProgressListener
- [ ] Cancelar upload/download si usuario lo solicita

---

## 🔔 Notificaciones

### Historia 25: Sistema de Notificaciones
- [ ] Crear canal de notificaciones para la app
- [ ] Notificación persistente durante cifrado (progress)
- [ ] Notificación persistente durante upload (progress)
- [ ] Notificación persistente durante download (progress)
- [ ] Notificaciones de éxito (auto-dismiss después de 5s)
- [ ] Notificaciones de error (persistentes, con acción)
- [ ] Acción en notificación: "Reintentar"
- [ ] Acción en notificación: "Ver detalles"
- [ ] Icono y color según tipo de notificación

---

## 🎨 UI/UX

### Historia 26: Diseño y Experiencia de Usuario
- [ ] Material Design 3 components
- [ ] Theme con colores de seguridad (azul/verde para seguro)
- [ ] Dark mode support
- [ ] Animaciones de transición entre pantallas
- [ ] Loading states (shimmer effect o progress bars)
- [ ] Empty states con ilustraciones y mensajes claros
- [ ] Error states con iconos y mensajes descriptivos
- [ ] Snackbars para feedback rápido
- [ ] Confirmación con diálogos para acciones destructivas

### Historia 27: Accesibilidad
- [ ] Content descriptions en todos los iconos
- [ ] TalkBack compatible
- [ ] Tamaño de texto escalable
- [ ] Contraste adecuado (WCAG AA)
- [ ] Touch targets mínimo 48dp

---

## ⚙️ Configuración y Ajustes

### Historia 28: Pantalla de Ajustes
- [ ] Opción: Cambiar contraseña maestra (pero ya no se podrian descifrar archivos ya cifrados con otra contraseña)(opcional)
- [ ] Opción: Sincronización automática ON/OFF
- [ ] Opción: Eliminar archivos originales automáticamente
- [ ] Opción: Limpiar caché de archivos temporales
- [ ] Información: Espacio usado en la nube
- [ ] Información: Número de archivos cifrados
- [ ] Opción: Exportar logs (para debugging)
- [ ] Lock automático después de X minutos de inactividad

---

## 🔒 Seguridad Adicional

### Historia 29: Medidas de Seguridad
- [ ] Screenshot blocking en pantallas sensibles
- [ ] Lock automático después de X minutos de inactividad
- [ ] Validar integridad de archivos con checksum
- [ ] Detectar root/jailbreak (advertencia)
- [ ] Ofuscar código con R8/ProGuard
- [ ] No guardar contraseña maestra en memoria más tiempo del necesario
- [ ] Limpiar archivos temporales al cerrar app

---

## 📦 Build y Deploy

### Historia 33: Configuración de Build
- [ ] Configurar BuildConfig con API_URL
- [ ] Variantes de build: debug, release
- [ ] Signing config para release
- [ ] Minify y obfuscate con R8
- [ ] Mantener reglas de ProGuard para Retrofit, Room, etc.
- [ ] Version code y version name

### Historia 34: Preparación para Producción
- [ ] Remover logs de debug en release
- [ ] Configurar Crashlytics (Firebase o Sentry)
- [ ] Preparar screenshots para Play Store
- [ ] Escribir descripción y changelog
- [ ] Preparar privacy policy
- [ ] Testing en múltiples dispositivos y versiones Android

---

## 📋 Checklist de Revisión Final

### Funcionalidad Core
- [ ] Cifrado de archivos funciona correctamente
- [ ] Upload a B2 funciona correctamente
- [ ] Download de B2 funciona correctamente
- [ ] Sincronización multi-device funciona
- [ ] Biometría funciona correctamente
- [ ] UserId se genera correctamente

### Seguridad
- [ ] Archivos nunca se guardan sin cifrar
- [ ] Metadata está cifrada
- [ ] Nombres de archivo son UUIDs aleatorios
- [ ] Contraseña maestra nunca se envía al servidor
- [ ] Backend nunca ve archivos sin cifrar (zero-knowledge)

### UX
- [ ] App es intuitiva y fácil de usar
- [ ] Feedback visual claro en todas las acciones
- [ ] Manejo de errores con mensajes claros
- [ ] Indicadores de progreso para operaciones largas
- [ ] Estados vacíos con mensajes útiles

### Performance
- [ ] App no congela UI durante operaciones pesadas
- [ ] WorkManager para operaciones en background
- [ ] Imágenes se cargan eficientemente (Coil/Glide)
- [ ] Base de datos Room optimizada
- [ ] Uso de memoria controlado

### Edge Cases
- [ ] Manejo de permisos denegados
- [ ] Manejo de sin conexión a internet
- [ ] Manejo de espacio insuficiente
- [ ] Manejo de archivos muy grandes
- [ ] Manejo de contraseña incorrecta
- [ ] Manejo de API caída o lenta

---

## 🎯 Priorización (para Gemini)

### Must Have (MVP)
- ✅ Autenticación (biometría)
- ✅ Contraseña maestra para generación de userId
- ✅ Cifrado de archivos
- ✅ Upload a la nube
- ✅ Lista de archivos cifrados
- ✅ Uso temporal de archivos

### Should Have
- ✅ Sincronización al reinstalar
- ✅ Download de archivos faltantes
- ✅ Badges de estado
- ✅ Notificaciones de progreso

### Nice to Have
- ⚠️ Sincronización automática periódica
- ⚠️ Dark mode
- ⚠️ Ajustes avanzados
- ⚠️ Export de logs

---

## 📝 Notas para Gemini

**Al revisar código existente, verificar:**
1. ¿Existe VaultManager? → Confirmar implementación de cifrado
2. ¿Existe alguna integración con cloud? → Verificar si hay upload/download
3. ¿Hay Room database? → Verificar schema actual
4. ¿Hay WorkManager? → Verificar workers existentes
5. ¿Hay manejo de permisos? → Verificar solicitud de storage permissions

**Generar reporte:**
- ✅ Implementado correctamente
- ⚠️ Implementado parcialmente (especificar qué falta)
- ❌ No implementado
- 🤔 No se pudo verificar (especificar por qué)

---

**Última actualización:** 2025-01-14