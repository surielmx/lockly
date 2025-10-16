# Checklist - Historias de Usuario - Lockly App

## 📋 Índice
- [Módulo de Autenticación](#módulo-de-autenticación)
- [Módulo de Sincronización](#módulo-de-sincronización)
- [Módulo Tab Bóveda](#módulo-tab-bóveda)
- [Módulo Tab Explorador](#módulo-tab-explorador)

---

## Módulo de Autenticación

### HU-001: Configuración inicial de contraseña maestra ✅ IMPLEMENTADO
**Como** usuario nuevo
**Quiero** establecer una contraseña maestra la primera vez que abro la app
**Para** proteger mis archivos cifrados

#### Criterios de Aceptación:
- [x] Al abrir la app por primera vez, se muestra una pantalla de configuración
- [x] El usuario puede ingresar una contraseña maestra (solo la primera vez)
- [x] La contraseña debe tener validación de fortaleza (mínimo X caracteres)
- [x] Se solicita confirmar la contraseña maestra
- [x] La contraseña genera un userId de exactamente 64 caracteres
- [x] El userId se almacena de forma segura en el dispositivo

**Notas de prueba:** Verificar longitud del userId, validación de contraseña, y almacenamiento seguro.

**Estado**: ✅ Completamente implementado (2025-10-15) - Ver documentación en `checklist/HU-001-IMPLEMENTADO.md`

---

### HU-002: Registro de huella biométrica inicial
**Como** usuario nuevo  
**Quiero** registrar mi huella biométrica después de la contraseña maestra  
**Para** acceder rápidamente a la app en el futuro

#### Criterios de Aceptación:
- [x] Después de establecer la contraseña maestra, se solicita la huella biométrica
- [x] El sistema valida que el dispositivo tiene capacidad biométrica
- [x] Se muestra un mensaje claro solicitando la huella
- [x] La huella se registra correctamente
- [x] Se almacena la preferencia de autenticación biométrica

**Notas de prueba:** Probar en dispositivos con y sin lector biométrico.

**Estado**: ✅ Completamente implementado (2025-10-15) - Ver documentación en `checklist/HU-002-IMPLEMENTADO.md`

---

### HU-003: Autenticación biométrica en aperturas subsecuentes
**Como** usuario registrado
**Quiero** usar mi huella biométrica al abrir la app
**Para** acceder rápidamente sin escribir la contraseña

#### Criterios de Aceptación:
- [x] Al abrir la app (segunda vez en adelante), aparece inmediatamente el prompt biométrico
- [x] El prompt no requiere interacción adicional del usuario
- [x] La autenticación exitosa da acceso a la app
- [x] La autenticación fallida muestra un mensaje de error
- [x] Se permite reintentar la huella biométrica

**Notas de prueba:** Verificar que aparece automáticamente, sin pantallas intermedias.

**Estado**: ✅ Completamente implementado (2025-10-15) - Ver documentación en `checklist/HU-003-IMPLEMENTADO.md`

---

### HU-004: Cancelación de autenticación biométrica
**Como** usuario
**Quiero** poder cancelar el prompt de huella biométrica
**Para** tener la opción de reactivarlo cuando lo necesite

#### Criterios de Aceptación:
- [x] El usuario puede cancelar el prompt biométrico
- [x] Después de cancelar, se muestra una opción para reactivar la huella
- [x] El botón/opción de reactivación es visible y claro
- [x] Al reactivar, aparece nuevamente el prompt biométrico

**Notas de prueba:** Probar flujo de cancelar y reactivar múltiples veces.

**Estado**: ✅ Completamente implementado (2025-10-15) - Ver documentación en `checklist/HU-004-IMPLEMENTADO.md`

---

### HU-005: Reingreso de contraseña maestra en reinstalación
**Como** usuario que reinstala la app
**Quiero** ingresar mi contraseña maestra nuevamente
**Para** recuperar acceso a mis archivos en la nube

#### Criterios de Aceptación:
- [x] La app detecta que es una instalación nueva/limpia
- [x] Se muestra la pantalla de ingreso de contraseña maestra
- [x] Se valida que la contraseña genere el mismo userId de 64 caracteres
- [x] Después de ingresar la contraseña, se solicita la huella biométrica
- [x] Se mantiene la misma experiencia que en HU-001

**Notas de prueba:** Reinstalar la app y verificar flujo completo.

**Estado**: ✅ Completamente implementado (2025-10-15) - Ver documentación en `checklist/HU-005-IMPLEMENTADO.md`

---

### HU-006: Uso de contraseña maestra en nuevo dispositivo
**Como** usuario en un dispositivo diferente
**Quiero** ingresar mi contraseña maestra
**Para** sincronizar mis archivos desde la nube

#### Criterios de Aceptación:
- [x] En un dispositivo nuevo, la app solicita la contraseña maestra
- [x] La contraseña genera el mismo userId de 64 caracteres
- [x] Se verifica la identidad contra los archivos en la nube
- [x] Se procede con el flujo de sincronización (ver HU-007)
- [x] Se solicita configurar la huella biométrica en el nuevo dispositivo

**Notas de prueba:** Instalar en un segundo dispositivo con la misma cuenta en la nube.

**Estado**: ✅ Completamente implementado (2025-10-15) - Ver documentación en `checklist/HU-006-IMPLEMENTADO.md`

---

## Módulo de Sincronización

### HU-007: Verificación de archivos en la nube al primer inicio
**Como** usuario que ingresa a la app
**Quiero** que la app verifique si tengo archivos en la nube  
**Para** sincronizarlos con el dispositivo actual

#### Criterios de Aceptación:
- [ ] La app verifica archivos en la nube usando el userId
- [ ] Esta verificación ocurre solo en el primer inicio después de la autenticación
- [ ] Se muestra un indicador de carga durante la verificación
- [ ] Si hay archivos, se activa el flujo de sincronización (HU-008)
- [ ] La verificación NO se repite en inicios subsecuentes

**Notas de prueba:** Probar con cuenta con archivos y sin archivos.

---

### HU-008: Notificación de archivos disponibles para sincronizar
**Como** usuario con archivos en la nube  
**Quiero** recibir una notificación clara  
**Para** decidir si quiero sincronizar los archivos

#### Criterios de Aceptación:
- [ ] Se muestra un banner en la pantalla principal
- [ ] El banner indica claramente que hay archivos para sincronizar
- [ ] El banner contiene un único botón CTA (ej: "Sincronizar archivos")
- [ ] El banner es visible pero no bloquea la interfaz
- [ ] El banner persiste hasta que el usuario tome acción

**Notas de prueba:** Verificar diseño y posición del banner.

---

### HU-009: Descarga de archivos en lotes (background)
**Como** usuario que inicia la sincronización  
**Quiero** que los archivos se descarguen en segundo plano  
**Para** poder usar otras apps mientras se completa el proceso

#### Criterios de Aceptación:
- [ ] Al presionar el CTA del banner, inicia la descarga
- [ ] Los archivos se descargan en lotes de 10 (ajustable según pruebas)
- [ ] El proceso continúa en background
- [ ] Se muestra una notificación persistente con el progreso
- [ ] El usuario puede salir de la app y el proceso continúa
- [ ] Se muestra progreso: "Descargando X de Y archivos"

**Notas de prueba:** Probar con diferentes cantidades de archivos (5, 20, 50, 100+).

---

### HU-010: Finalización de sincronización
**Como** usuario  
**Quiero** ser notificado cuando la sincronización termine  
**Para** saber que mis archivos están disponibles

#### Criterios de Aceptación:
- [ ] Al completar la descarga, se muestra una notificación
- [ ] La notificación indica éxito: "X archivos sincronizados"
- [ ] Los archivos descargados aparecen en la tab "Bóveda"
- [ ] Se actualiza el estado de los iconos de nube en cada archivo
- [ ] El banner de sincronización desaparece

**Notas de prueba:** Verificar que todos los archivos aparecen correctamente.

---

## Módulo Tab Bóveda

### HU-011: Visualización de breadcrumb en Bóveda
**Como** usuario en la tab Bóveda  
**Quiero** ver un breadcrumb de navegación  
**Para** saber en qué carpeta me encuentro

#### Criterios de Aceptación:
- [ ] El breadcrumb se muestra en la parte superior de la tab
- [ ] Las carpetas anteriores se muestran en color blanco
- [ ] La carpeta actual se muestra en color azul
- [ ] El breadcrumb es funcional (permite navegar hacia atrás)
- [ ] El separador entre carpetas es claro (ej: `/` o `>`)

**Notas de prueba:** Navegar por múltiples niveles de carpetas.

---

### HU-012: Lista de archivos/carpetas cifrados en Bóveda
**Como** usuario  
**Quiero** ver mis archivos y carpetas cifrados en una lista  
**Para** navegar por mi contenido protegido

#### Criterios de Aceptación:
- [ ] Los archivos y carpetas se muestran en formato lista
- [ ] La lista está ordenada alfabéticamente
- [ ] Las carpetas aparecen antes que los archivos
- [ ] Cada elemento muestra el nombre original (antes del cifrado)
- [ ] La estructura de carpetas es idéntica a la tab "Explorador"
- [ ] Los nombres de archivos y carpetas no deben truncarse, se muestran completos

**Notas de prueba:** Comparar estructura con la tab Explorador.

---

### HU-013: Iconos de tipo de archivo en Bóveda
**Como** usuario  
**Quiero** ver iconos que representen el tipo de archivo  
**Para** identificar rápidamente el contenido

#### Criterios de Aceptación:
- [ ] Cada archivo muestra el icono correspondiente a su formato
- [ ] Formatos soportados: PDF, DOC, XLS, JPG, PNG, MP4, etc.
- [ ] Los archivos sin extensión conocida muestran un icono genérico
- [ ] Los iconos son claros y distinguibles
- [ ] El tamaño de los iconos es consistente
- [ ] Los nombres de archivo se muestran completos sin truncamiento

**Notas de prueba:** Crear archivos de diferentes formatos y verificar iconos.

---

### HU-014: Indicadores de estado de nube y uso
**Como** usuario  
**Quiero** ver el estado de sincronización y uso de cada archivo  
**Para** saber qué archivos están en la nube y cuáles están en uso

#### Criterios de Aceptación:
- [ ] Cada archivo muestra un icono de nube si está sincronizado
- [ ] Si el archivo no está en la nube, se muestra otro indicador
- [ ] Los archivos en uso muestran un candado abierto en color amarillo
- [ ] Los archivos no en uso no muestran el candado (o muestran candado cerrado)
- [ ] Los iconos son visibles y no se sobreponen

**Notas de prueba:** Verificar iconos con archivos sincronizados, no sincronizados, en uso.

---

### HU-015: Navegación entre carpetas en Bóveda
**Como** usuario  
**Quiero** poder hacer clic en las carpetas  
**Para** explorar su contenido

#### Criterios de Aceptación:
- [ ] Al hacer clic en una carpeta, se abre su contenido
- [ ] El breadcrumb se actualiza con la nueva ubicación
- [ ] Se mantiene el orden alfabético
- [ ] Se puede regresar usando el breadcrumb
- [ ] La navegación es fluida sin retrasos

**Notas de prueba:** Navegar por 3-4 niveles de profundidad.

---

### HU-016: Selección de un solo archivo para usar
**Como** usuario  
**Quiero** seleccionar un archivo cifrado  
**Para** usarlo temporalmente

#### Criterios de Aceptación:
- [ ] Al hacer clic/long press en un archivo, se selecciona
- [ ] El archivo seleccionado muestra un indicador visual
- [ ] Se inicia automáticamente el proceso de descifrado
- [ ] El archivo descifrado se copia a `documents/lockly`
- [ ] El icono cambia a candado abierto amarillo
- [ ] El archivo es accesible desde otras apps

**Notas de prueba:** Abrir el archivo desde un gestor de archivos o app asociada.

---

### HU-017: Selección de múltiples archivos para usar
**Como** usuario  
**Quiero** seleccionar varios archivos cifrados a la vez  
**Para** usarlos simultáneamente

#### Criterios de Aceptación:
- [ ] Se puede seleccionar más de un archivo (checkbox o similar)
- [ ] Se muestra un contador de archivos seleccionados
- [ ] Al confirmar la selección, aparece un dialog de advertencia
- [ ] El dialog explica que se crearán copias temporales descifradas
- [ ] El dialog tiene opciones: "Cancelar" y "Confirmar"
- [ ] Al confirmar, todos los archivos se descifran y copian a `documents/lockly`
- [ ] Todos los archivos muestran el candado abierto amarillo

**Notas de prueba:** Seleccionar 3-5 archivos y verificar descifrado correcto.

---

### HU-018: Eliminación de archivos temporales (uso)
**Como** usuario  
**Quiero** eliminar los archivos temporales que ya no necesito  
**Para** liberar espacio y proteger mi información

#### Criterios de Aceptación:
- [ ] Se puede deseleccionar uno o varios archivos en uso
- [ ] Al deseleccionar, aparece un dialog de confirmación
- [ ] El dialog pregunta: "¿Eliminar archivo(s) temporal(es)?"
- [ ] Opciones: "Cancelar" y "Eliminar"
- [ ] Al confirmar, los archivos se eliminan de `documents/lockly`
- [ ] Los iconos de candado abierto desaparecen/cambian a cerrado
- [ ] Los archivos cifrados en Bóveda permanecen intactos

**Notas de prueba:** Verificar que los archivos desaparecen de documents/lockly.

---

## Módulo Tab Explorador

### HU-019: Visualización de breadcrumb en Explorador
**Como** usuario en la tab Explorador  
**Quiero** ver un breadcrumb de navegación  
**Para** saber en qué carpeta me encuentro

#### Criterios de Aceptación:
- [ ] El breadcrumb se muestra en la parte superior de la tab
- [ ] Funciona igual que en la tab Bóveda (HU-011)
- [ ] Las carpetas anteriores en blanco, actual en azul
- [ ] Permite navegación hacia atrás

**Notas de prueba:** Verificar paridad con breadcrumb de Bóveda.

---

### HU-020: Lista de carpetas del sistema
**Como** usuario  
**Quiero** ver las carpetas de mi dispositivo  
**Para** seleccionar archivos para cifrar

#### Criterios de Aceptación:
- [ ] Se muestran todas las carpetas del dispositivo
- [ ] Se excluyen carpetas ocultas (que inician con `.`)
- [ ] Se excluyen carpetas del sistema (Android, data, etc.)
- [ ] Las carpetas se muestran en orden alfabético
- [ ] La lista es scrolleable

**Notas de prueba:** Comparar con un gestor de archivos para validar exclusiones.

---

### HU-021: Información de carpetas (nombre y cantidad)
**Como** usuario  
**Quiero** ver el nombre y número de items de cada carpeta  
**Para** decidir qué carpeta explorar

#### Criterios de Aceptación:
- [ ] Cada carpeta muestra su nombre
- [ ] Cada carpeta muestra el número de items que contiene
- [ ] El formato es claro: "Nombre de carpeta (X items)"
- [ ] Abajo dle nombre de carpeta: "X items"
- [ ] El conteo es preciso
- [ ] Los nombres de carpetas no deben truncarse

**Notas de prueba:** Verificar conteo manual de items en varias carpetas.

---

### HU-022: Carpetas vacías deshabilitadas
**Como** usuario  
**Quiero** que las carpetas vacías se vean deshabilitadas  
**Para** saber que no tienen contenido

#### Criterios de Aceptación:
- [ ] Las carpetas con 0 items usan un tono de color claro/gris
- [ ] El texto aparece en un estilo deshabilitado
- [ ] Las carpetas vacías no responden al clic
- [ ] Se muestra "(0 items)" claramente

**Notas de prueba:** Crear carpetas vacías y verificar comportamiento.

---

### HU-023: Navegación entre carpetas en Explorador
**Como** usuario  
**Quiero** hacer clic en carpetas con contenido  
**Para** explorar sus archivos

#### Criterios de Aceptación:
- [ ] Solo las carpetas con items > 0 son clicables
- [ ] Al hacer clic, se muestra el contenido de la carpeta
- [ ] El breadcrumb se actualiza
- [ ] Se mantiene el orden alfabético
- [ ] Se puede navegar hacia atrás
- [ ] Los nombres de archivos se muestran completos sin truncamiento

**Notas de prueba:** Navegar por diferentes niveles de carpetas.

---

### HU-024: Selección de archivos para cifrar
**Como** usuario  
**Quiero** seleccionar uno o varios archivos  
**Para** cifrarlos y subirlos a la nube

#### Criterios de Aceptación:
- [ ] Se pueden seleccionar archivos individuales
- [ ] Se pueden seleccionar múltiples archivos (checkbox/multiselección)
- [ ] Se muestra un contador de archivos seleccionados
- [ ] Los archivos seleccionados tienen un indicador visual
- [ ] Hay un botón para proceder con el cifrado

**Notas de prueba:** Seleccionar 1, 5 y 10+ archivos.

---

### HU-025: Confirmación de cifrado
**Como** usuario  
**Quiero** confirmar la acción de cifrado  
**Para** evitar cifrar archivos por error

#### Criterios de Aceptación:
- [ ] Al presionar el botón de cifrado, aparece un dialog
- [ ] El dialog muestra el número de archivos a cifrar
- [ ] Opciones: "Cancelar" y "Cifrar"
- [ ] Al cancelar, se deseleccionan los archivos
- [ ] Al confirmar, inicia el proceso de cifrado

**Notas de prueba:** Probar ambos flujos (cancelar y confirmar).

---

### HU-026: Proceso de cifrado y subida a la nube
**Como** usuario  
**Quiero** que mis archivos se cifren y suban automáticamente  
**Para** proteger mi información

#### Criterios de Aceptación:
- [ ] Los archivos seleccionados se cifran uno por uno
- [ ] Se muestra un indicador de progreso: "Cifrando X de Y"
- [ ] Los archivos cifrados se guardan en `data/data/com.developermx.lockly/files/` o `/cache/`
- [ ] Una vez cifrados, se suben automáticamente a la nube
- [ ] Los archivos subidos a la nube, tienen el nombre oricinal del archivo seguido de ".enc"
- [ ] Se muestra progreso de subida: "Subiendo X de Y"
- [ ] El proceso puede correr en background

**Notas de prueba:** Cifrar archivos de diferentes tamaños (pequeños y grandes).

---

### HU-027: Actualización de Bóveda tras cifrado
**Como** usuario  
**Quiero** ver los archivos recién cifrados en la Bóveda  
**Para** confirmar que el proceso fue exitoso

#### Criterios de Aceptación:
- [ ] Al completar el cifrado, los archivos aparecen en la tab Bóveda
- [ ] La ruta/path es idéntica a la ubicación original
- [ ] Los nombres de archivo son los originales
- [ ] Los iconos de nube indican que están sincronizados
- [ ] La estructura de carpetas se mantiene

**Notas de prueba:** Comparar rutas entre Explorador y Bóveda.

---

### HU-028: Confirmación de eliminación de archivos originales
**Como** usuario  
**Quiero** decidir si eliminar los archivos originales  
**Para** mantener el control de mi información

#### Criterios de Aceptación:
- [ ] Al completar el cifrado y subida, aparece un dialog
- [ ] El dialog pregunta: "¿Eliminar archivos originales?"
- [ ] Opciones: "Conservar" y "Eliminar"
- [ ] El dialog explica que los archivos ya están cifrados y en la nube
- [ ] Se puede cerrar el dialog sin seleccionar (equivalente a Conservar)

**Notas de prueba:** Verificar ambas opciones.

---

### HU-029: Eliminación de archivos originales
**Como** usuario  
**Quiero** eliminar los archivos originales tras cifrarlos  
**Para** liberar espacio en mi dispositivo

#### Criterios de Aceptación:
- [ ] Al seleccionar "Eliminar", se borran los archivos originales del dispositivo
- [ ] Los archivos se eliminan de la ubicación original en Explorador
- [ ] Los archivos cifrados permanecen en Bóveda y en la nube
- [ ] Se muestra un mensaje de confirmación: "X archivos eliminados"

**Notas de prueba:** Verificar que los archivos originales ya no existen.

---

### HU-030: Advertencia de seguridad al eliminar originales
**Como** usuario  
**Quiero** ser advertido sobre los riesgos  
**Para** tomar una decisión informada al eliminar archivos originales

#### Criterios de Aceptación:
- [ ] Al seleccionar "Eliminar", se muestra una advertencia adicional
- [ ] La advertencia menciona el riesgo de extravío del dispositivo
- [ ] Se explica que aunque estén cifrados, los archivos podrían recuperarse del dispositivo perdido
- [ ] El usuario debe confirmar nuevamente: "Entiendo los riesgos" y "Cancelar"
- [ ] Solo después de esta segunda confirmación se eliminan los archivos

**Notas de prueba:** Verificar que aparecen ambos dialogs en secuencia.

---

# Continuación del Checklist - Integración con API

---

## Módulo de Integración API

### HU-031: Verificación de salud del servidor al iniciar
**Como** desarrollador  
**Quiero** verificar que el servidor esté disponible al iniciar la app  
**Para** asegurar que las operaciones de sincronización funcionarán correctamente

#### Criterios de Aceptación:
- [ ] Al iniciar la app, se llama al endpoint `GET /health`
- [ ] Si el servidor responde con `status: "ok"`, se continúa normalmente
- [ ] Si el servidor no responde o falla, se muestra un mensaje al usuario
- [ ] El mensaje indica: "Servidor no disponible. Modo sin conexión"
- [ ] La app permite trabajar localmente sin sincronización
- [ ] Se reintenta la conexión en segundo plano cada 30 segundos

**Notas de prueba:** Probar con servidor activo, inactivo y con conexión intermitente.

---

### HU-032: Obtener lista de archivos del usuario desde la nube
**Como** usuario autenticado  
**Quiero** que la app obtenga mi lista de archivos desde la nube  
**Para** saber qué archivos tengo respaldados

#### Criterios de Aceptación:
- [ ] Al completar la autenticación, se llama a `GET /api/files/:userId`
- [ ] El userId se genera correctamente desde la contraseña maestra
- [ ] Se procesa la respuesta con la lista de archivos (fileName, size, lastModified)
- [ ] Los archivos se guardan en la base de datos local
- [ ] Si no hay archivos (`count: 0`), se muestra mensaje: "Sin archivos en la nube"
- [ ] Si hay error 400, se valida y regenera el userId
- [ ] Si hay error 429, se implementa retry con exponential backoff

**Notas de prueba:** Probar con cuenta nueva (sin archivos) y cuenta existente (con archivos).

---

### HU-033: Verificación de cuota de almacenamiento
**Como** usuario  
**Quiero** ver mi cuota de almacenamiento disponible  
**Para** saber cuánto espacio tengo para subir archivos

#### Criterios de Aceptación:
- [ ] Se llama a `GET /api/quota/:userId` al abrir la tab Bóveda
- [ ] Se muestra un indicador visual con la información de cuota
- [ ] Formato mostrado: "X MB de Y MB usados (Z%)"
- [ ] La barra de progreso refleja el `usedPercentage`
- [ ] Colores según porcentaje: Verde (<75%), Amarillo (75-89%), Rojo (≥90%)
- [ ] La información se actualiza después de cada subida o eliminación
- [ ] Se cachea la información por 5 minutos para reducir peticiones

**Notas de prueba:** Subir archivos grandes y verificar actualización de cuota.

---

### HU-034: Obtención de URL para subir un solo archivo
**Como** usuario que va a cifrar un archivo  
**Quiero** obtener una URL de subida desde el servidor  
**Para** poder subir el archivo cifrado a la nube

#### Criterios de Aceptación:
- [ ] Antes de cifrar, se verifica la cuota disponible localmente
- [ ] Se llama a `POST /api/upload-url` con userId, fileName.enc y contentType
- [ ] El fileName debe ser UUID + ".enc" (ej: `13785622-523b-4c06-b6bd-d4ea0cf98a5d.enc`)
- [ ] Se recibe la URL pre-firmada con expiración de 5 minutos
- [ ] Se almacena el `key` para futuras referencias
- [ ] Si hay error 403 (cuota excedida), se muestra dialog específico
- [ ] El dialog indica: "Espacio insuficiente. Usado: X de Y MB"
- [ ] Si hay error 400, se valida el formato del fileName

**Notas de prueba:** Intentar subir con cuota llena y con espacio disponible.

---

### HU-035: Obtención de URLs para subir múltiples archivos (batch)
**Como** usuario que va a cifrar varios archivos  
**Quiero** obtener múltiples URLs de subida en una sola petición  
**Para** optimizar el proceso de sincronización

#### Criterios de Aceptación:
- [ ] Se llama a `POST /api/upload-urls/batch` con array de archivos (máx 100)
- [ ] Cada archivo incluye: fileName.enc y contentType
- [ ] Se recibe un array con las URLs correspondientes a cada archivo
- [ ] Las URLs tienen 5 minutos de expiración
- [ ] Si hay más de 100 archivos, se dividen en múltiples peticiones
- [ ] Se muestra progreso: "Preparando subida X de Y archivos"
- [ ] Si hay error parcial, se reportan los archivos que fallaron
- [ ] Se implementa retry con exponential backoff para error 429

**Notas de prueba:** Probar con 1, 10, 50, 100 y 150 archivos.

---

### HU-036: Subida de archivo cifrado a la nube (upload directo)
**Como** usuario  
**Quiero** que mis archivos cifrados se suban directamente a B2  
**Para** mantener la seguridad zero-knowledge

#### Criterios de Aceptación:
- [ ] Se usa la URL pre-firmada obtenida del servidor
- [ ] El archivo se sube directamente a B2 usando PUT request
- [ ] El contentType debe coincidir con el solicitado en la URL
- [ ] Se muestra progreso de subida en porcentaje
- [ ] Si la subida falla, se reintenta hasta 3 veces
- [ ] Si las 3 reintentos fallan, se notifica al usuario
- [ ] La URL expira en 5 minutos; si expira, se solicita una nueva
- [ ] Se verifica que el archivo se subió correctamente (status 200)

**Notas de prueba:** Probar con archivos de diferentes tamaños (1MB, 10MB, 50MB).

---

### HU-037: Subida paralela de múltiples archivos
**Como** usuario que sube varios archivos  
**Quiero** que se suban en paralelo  
**Para** acelerar el proceso de sincronización

#### Criterios de Aceptación:
- [ ] Los archivos se suben en paralelo (máximo 3 simultáneos)
- [ ] Se muestra progreso global: "Subiendo X de Y archivos (Z%)"
- [ ] Se muestra progreso individual de cada archivo activo
- [ ] Si un archivo falla, los demás continúan
- [ ] Al finalizar, se muestra resumen: "X exitosos, Y fallidos"
- [ ] Los archivos fallidos se pueden reintentar individualmente
- [ ] Se actualiza la cuota después de todas las subidas

**Notas de prueba:** Subir 10 archivos y simular fallo en algunos.

---

### HU-038: Actualización de lista de archivos tras subida exitosa
**Como** usuario  
**Quiero** que los archivos subidos aparezcan inmediatamente en la Bóveda  
**Para** confirmar que la sincronización fue exitosa

#### Criterios de Aceptación:
- [ ] Después de cada subida exitosa, se actualiza la base de datos local
- [ ] El archivo aparece en la tab Bóveda con icono de nube sincronizada
- [ ] Se almacena: fileName.enc, size, lastModified, contentType, ruta original
- [ ] La ruta original se mantiene para mostrar en breadcrumb
- [ ] El icono de estado cambia a "sincronizado" (ej: checkmark verde)
- [ ] Se actualiza el contador de archivos en la nube
- [ ] No se requiere llamar a `GET /api/files/:userId` nuevamente

**Notas de prueba:** Verificar que el archivo aparece inmediatamente sin recargar.

---

### HU-039: Obtención de URL para descargar un solo archivo
**Como** usuario que quiere usar un archivo cifrado  
**Quiero** obtener una URL de descarga desde el servidor  
**Para** poder descargar el archivo desde la nube

#### Criterios de Aceptación:
- [ ] Se llama a `POST /api/download-url` con userId y fileName.enc
- [ ] Se recibe la URL pre-firmada con expiración de 5 minutos
- [ ] Si el archivo no existe, se recibe error 404
- [ ] Se muestra mensaje: "Archivo no encontrado en la nube"
- [ ] Si hay error 400, se valida el formato del fileName
- [ ] Si hay error 429, se implementa retry con exponential backoff
- [ ] La URL se usa inmediatamente para la descarga

**Notas de prueba:** Intentar descargar archivo existente y no existente.

---

### HU-040: Obtención de URLs para descargar múltiples archivos (batch)
**Como** usuario que sincroniza archivos por primera vez  
**Quiero** obtener múltiples URLs de descarga en una sola petición  
**Para** optimizar el proceso de sincronización

#### Criterios de Aceptación:
- [ ] Se llama a `POST /api/download-urls/batch` con array de fileNames (máx 100)
- [ ] Se recibe un array con las URLs correspondientes
- [ ] Las URLs tienen 5 minutos de expiración
- [ ] Si hay más de 100 archivos, se dividen en lotes de 100
- [ ] Se muestra progreso: "Preparando descarga de X archivos"
- [ ] Si algún archivo no existe, se reporta en la respuesta
- [ ] Se continúa con los archivos que sí existen
- [ ] Se implementa retry para error 429

**Notas de prueba:** Probar con diferentes cantidades de archivos en lotes.

---

### HU-041: Descarga paralela de múltiples archivos (sincronización)
**Como** usuario que sincroniza por primera vez  
**Quiero** que los archivos se descarguen en paralelo  
**Para** acelerar el proceso de sincronización

#### Criterios de Aceptación:
- [ ] Los archivos se descargan en lotes de 10 (ajustable)
- [ ] Se descargan hasta 3 archivos simultáneamente
- [ ] El proceso corre en background (WorkManager)
- [ ] Se muestra notificación persistente con progreso
- [ ] Formato: "Descargando X de Y archivos (Z%)"
- [ ] El usuario puede salir de la app y el proceso continúa
- [ ] Si un archivo falla, los demás continúan
- [ ] Al finalizar, se muestra notificación: "Sincronización completa"

**Notas de prueba:** Sincronizar 50 archivos y verificar que continúa en background.

---

### HU-042: Eliminación de archivos en la nube (batch)
**Como** usuario que elimina archivos cifrados  
**Quiero** eliminar los archivos de la nube automáticamente  
**Para** liberar espacio en mi cuota

#### Criterios de Aceptación:
- [ ] Al eliminar archivos de la Bóveda, se llama a `DELETE /api/files/batch`
- [ ] Se envía array con los fileNames.enc a eliminar (máx 1000)
- [ ] Se recibe respuesta con archivos eliminados exitosamente
- [ ] Si hay errores parciales, se muestran en la respuesta
- [ ] Se muestra dialog con resumen: "X eliminados, Y fallidos"
- [ ] Los archivos eliminados se remueven de la base de datos local
- [ ] Se actualiza la cuota de almacenamiento después de eliminar
- [ ] Si hay error 429, se implementa retry

**Notas de prueba:** Eliminar 5 archivos y verificar actualización de cuota.

---

### HU-043: Manejo de cuota excedida al intentar subir
**Como** usuario  
**Quiero** ser notificado cuando mi cuota esté llena  
**Para** saber que no puedo subir más archivos

#### Criterios de Aceptación:
- [ ] Antes de cifrar, se verifica la cuota local
- [ ] Si `remaining < tamaño del archivo`, se previene la subida
- [ ] Se muestra dialog: "Espacio insuficiente"
- [ ] El dialog muestra: "Usado: X MB de Y MB (Z%)"
- [ ] Se ofrecen opciones: "Eliminar archivos" o "Cancelar"
- [ ] Si selecciona "Eliminar archivos", se abre la tab Bóveda
- [ ] Si el servidor responde 403, se muestra el mismo dialog
- [ ] Se actualiza la cuota desde el servidor para confirmar

**Notas de prueba:** Llenar la cuota al 100% e intentar subir un archivo.

---

### HU-044: Manejo de rate limiting (429)
**Como** desarrollador  
**Quiero** manejar correctamente los errores de rate limiting  
**Para** no saturar el servidor y ofrecer buena experiencia al usuario

#### Criterios de Aceptación:
- [ ] Al recibir error 429, se implementa exponential backoff
- [ ] Primer reintento: esperar 1 segundo
- [ ] Segundo reintento: esperar 2 segundos
- [ ] Tercer reintento: esperar 4 segundos
- [ ] Máximo 3 reintentos automáticos
- [ ] Se muestra mensaje al usuario: "Servidor ocupado, reintentando..."
- [ ] Si los 3 reintentos fallan, se muestra: "Demasiadas peticiones, intenta más tarde"
- [ ] El usuario puede cancelar los reintentos

**Notas de prueba:** Simular múltiples peticiones rápidas para activar rate limit.

---

### HU-045: Caché de información de cuota
**Como** desarrollador  
**Quiero** cachear la información de cuota localmente  
**Para** reducir peticiones al servidor y mejorar el rendimiento

#### Criterios de Aceptación:
- [ ] La información de cuota se guarda en SharedPreferences
- [ ] Se cachea por máximo 5 minutos
- [ ] Después de 5 minutos, se solicita actualización al servidor
- [ ] Al subir o eliminar archivos, se actualiza el caché inmediatamente
- [ ] Se calcula localmente: `used = used + fileSize` tras subida
- [ ] Se calcula localmente: `used = used - fileSize` tras eliminación
- [ ] Cada 30 minutos se sincroniza con el servidor para validar
- [ ] Si hay discrepancia >5%, se usa el valor del servidor

**Notas de prueba:** Verificar que no se hace petición GET /quota en cada apertura.

---

### HU-046: Validación de datos antes de enviar al servidor
**Como** desarrollador  
**Quiero** validar todos los datos localmente antes de enviarlos  
**Para** evitar errores 400 y mejorar la experiencia del usuario

#### Criterios de Aceptación:
- [ ] Se valida userId: 3-64 caracteres, alfanuméricos + guiones
- [ ] Se valida fileName: termina en ".enc", máximo 255 caracteres
- [ ] Se valida contentType: formato MIME válido
- [ ] Se sanitizan nombres de archivo antes de generar fileName.enc
- [ ] Se muestran errores de validación antes de llamar al API
- [ ] Formato de error: "Nombre de archivo inválido: [detalle]"
- [ ] No se realizan peticiones si la validación falla

**Notas de prueba:** Intentar operaciones con datos inválidos (userId corto, fileName sin .enc).

---

### HU-047: Manejo de errores de red y timeouts
**Como** usuario  
**Quiero** ser notificado cuando hay problemas de conexión  
**Para** entender por qué las operaciones fallan

#### Criterios de Aceptación:
- [ ] Los timeouts están configurados en 30 segundos
- [ ] Si hay error de red, se muestra: "Sin conexión a internet"
- [ ] Si hay timeout, se muestra: "Tiempo de espera agotado"
- [ ] Se ofrece opción de reintentar
- [ ] Las operaciones en background se guardan para reintentar después
- [ ] Cuando se recupera conexión, se procesan operaciones pendientes
- [ ] Se muestra indicador visual del estado de conexión

**Notas de prueba:** Desactivar WiFi/datos y probar diferentes operaciones.

---

### HU-048: Sincronización automática al recuperar conexión
**Como** usuario  
**Quiero** que las operaciones pendientes se completen automáticamente  
**Para** no tener que reiniciar manualmente los procesos

#### Criterios de Aceptación:
- [ ] Se detecta cuando el dispositivo recupera conexión
- [ ] Se procesan automáticamente las subidas pendientes
- [ ] Se procesan automáticamente las descargas pendientes
- [ ] Se muestra notificación: "Sincronizando archivos pendientes"
- [ ] El usuario puede ver el progreso de la sincronización
- [ ] Las operaciones se procesan en orden FIFO
- [ ] Si alguna operación falla, se marca para revisión manual

**Notas de prueba:** Encriptar archivos sin conexión, luego activar WiFi.

---

### HU-049: Logging y monitoreo de operaciones API
**Como** desarrollador  
**Quiero** registrar todas las operaciones con el API  
**Para** facilitar debugging y detectar problemas

#### Criterios de Aceptación:
- [ ] Se registran todas las peticiones (método, endpoint, timestamp)
- [ ] Se registran todas las respuestas (status code, tiempo de respuesta)
- [ ] Se registran errores con stack trace completo
- [ ] Los logs se guardan localmente por máximo 7 días
- [ ] En modo debug, los logs se muestran en Logcat
- [ ] En producción, solo se registran errores
- [ ] Se incluye información: userId (anonimizado), fileName (hash)
- [ ] Los logs no contienen información sensible (contraseñas, contenido)

**Notas de prueba:** Revisar logs después de operaciones exitosas y fallidas.

---

### HU-050: Visualización completa de nombres largos
**Como** usuario
**Quiero**  ver los nombres completos de archivos y carpetas sin truncamiento
**Para** identificar correctamente cada elemento
Criterios de Aceptación:

- [ ] Los nombres largos se muestran en múltiples líneas si es necesario
- [ ] No se usa truncamiento con "..." a menos que el nombre exceda 3 líneas
- [ ] Los nombres son legibles en dispositivos de diferentes tamaños
- [ ] Se mantiene la alineación correcta con iconos y otros elementos

 ---

 ---

## Configuración de Ambiente y Seguridad

### HU-051: Configuración de URL del API para pruebas locales
**Como** desarrollador  
**Quiero** configurar la URL del API para pruebas locales  
**Para** poder probar la integración sin depender del servidor de producción

#### Criterios de Aceptación:
- [ ] La URL base para pruebas locales es: `http://192.168.1.14:3000`
- [ ] La URL se configura en una constante o archivo de configuración
- [ ] Se debe usar BuildConfig para diferenciar entre debug y release
- [ ] En modo debug, se usa la URL local: `http://192.168.1.14:3000`
- [ ] En modo release, se usa la URL de producción
- [ ] La URL es fácilmente modificable para otros desarrolladores

**Notas de prueba:** Verificar que las peticiones lleguen correctamente a la IP local.

---

### HU-052: Habilitación de políticas de seguridad para IP local
**Como** desarrollador  
**Quiero** habilitar las políticas de seguridad necesarias  
**Para** permitir conexiones HTTP a la IP local durante desarrollo

#### Criterios de Aceptación:
- [ ] Se configura `android:usesCleartextTraffic="true"` en AndroidManifest.xml para debug
- [ ] Se crea un archivo `network_security_config.xml` en res/xml/
- [ ] Se permite tráfico cleartext solo para la IP: `192.168.1.14`
- [ ] La configuración solo aplica en build variant de debug
- [ ] En release, cleartext está deshabilitado (solo HTTPS)
- [ ] Se documenta la configuración en README del proyecto

**Notas de prueba:** Verificar que las peticiones HTTP funcionen en debug y estén bloqueadas en release.

---

### HU-053: Actualización de políticas al cambiar URL de pruebas
**Como** desarrollador  
**Quiero** seguir las políticas de seguridad al cambiar la URL  
**Para** mantener la seguridad del proyecto

#### Criterios de Aceptación:
- [ ] Al cambiar la IP local, se debe actualizar `network_security_config.xml`
- [ ] Se debe actualizar la constante BASE_URL en la configuración
- [ ] Se debe verificar que la nueva IP esté en la lista de dominios permitidos
- [ ] Se debe documentar el cambio en el archivo de configuración
- [ ] Se debe notificar al equipo del cambio de URL
- [ ] Las políticas de seguridad deben revisarse antes de cada release

**Notas de prueba:** Cambiar la IP a otra diferente y verificar que se actualicen todas las configuraciones necesarias.

---

**Ejemplo de configuración:**
```xml
<!-- AndroidManifest.xml (solo en debug) -->
<application
    android:usesCleartextTraffic="true"
    android:networkSecurityConfig="@xml/network_security_config">
    ...
</application>
```
```xml
<!-- res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">192.168.1.14</domain>
    </domain-config>
</network-security-config>
```
```kotlin
// ApiClient.kt
object ApiConfig {
    const val LOCAL_URL = "http://192.168.1.14:3000/"
    const val PRODUCTION_URL = "https://api.lockly.com/"
    
    val BASE_URL = if (BuildConfig.DEBUG) LOCAL_URL else PRODUCTION_URL
}
```

---

## 📊 Resumen de Integración API

### Endpoints Utilizados por Funcionalidad

| Funcionalidad | Endpoint(s) | Frecuencia |
|---------------|-------------|------------|
| Inicio de app | GET /health | Una vez al iniciar |
| Autenticación | GET /api/files/:userId | Una vez por sesión |
| Verificar cuota | GET /api/quota/:userId | Cada 5 minutos (caché) |
| Subir 1 archivo | POST /api/upload-url | Por archivo |
| Subir N archivos | POST /api/upload-urls/batch | Por lote (<100) |
| Descargar 1 archivo | POST /api/download-url | Por archivo |
| Sincronización inicial | POST /api/download-urls/batch | Por lote (<100) |
| Eliminar archivos | DELETE /api/files/batch | Por operación |

### Optimizaciones Implementadas

- ✅ **Batch operations**: Reducen peticiones de N a 1
- ✅ **Caché de cuota**: Reduce peticiones en 80%
- ✅ **Retry con backoff**: Maneja rate limiting automáticamente
- ✅ **Operaciones paralelas**: Subidas/descargas simultáneas (máx 3)
- ✅ **Background sync**: Operaciones no bloquean la UI
- ✅ **Validación local**: Previene errores 400
- ✅ **Zero-knowledge**: Archivos cifrados antes de subir

### Matriz de Pruebas de Integración

- [ ] Flujo completo: Cifrar → Subir → Eliminar local → Descargar → Descifrar
- [ ] Sincronización con 100+ archivos
- [ ] Operación con cuota al 90%, 95%, 100%
- [ ] Rate limiting: 60 peticiones en 5 minutos
- [ ] Pérdida de conexión durante subida/descarga
- [ ] Expiración de URLs pre-firmadas (>5 minutos)
- [ ] Servidor caído durante operaciones
- [ ] Múltiples dispositivos sincronizando simultáneamente

---

## 📝 Notas Finales de API

- Las URLs pre-firmadas expiran en **5 minutos**
- Límite de **100 archivos** por petición batch de upload/download
- Límite de **1000 archivos** por petición batch de delete
- Rate limiting: **50-100 peticiones por 15 minutos** según endpoint
- Todas las operaciones deben implementar **retry con exponential backoff**
- La cuota debe actualizarse después de **cada subida/eliminación**
- Los archivos deben cifrarse **antes** de subir (zero-knowledge)

## 📊 Resumen de Pruebas

### Matriz de Compatibilidad
- [ ] Android 13+
- [ ] Dispositivos con sensor biométrico
- [ ] Dispositivos sin sensor biométrico
- [ ] Diferentes proveedores de almacenamiento en la nube

### Pruebas de Integración
- [ ] Flujo completo: Instalación → Cifrado → Desinstalación → Reinstalación → Sincronización
- [ ] Flujo de múltiples dispositivos
- [ ] Pruebas de rendimiento con 100+ archivos

### Pruebas de Seguridad
- [ ] Verificar que los archivos cifrados no son legibles
- [ ] Confirmar que el userId no es reversible a la contraseña
- [ ] Validar que los archivos temporales se eliminan correctamente
- [ ] Verificar que no hay fugas de información en logs

---

## 📝 Notas Finales

- Cada historia de usuario puede probarse de forma independiente
- Se recomienda completar un módulo completo antes de pasar al siguiente
- Las historias están numeradas para facilitar el seguimiento (HU-001 a HU-030)
- Ajustar el tamaño de los lotes de descarga según pruebas de rendimiento real