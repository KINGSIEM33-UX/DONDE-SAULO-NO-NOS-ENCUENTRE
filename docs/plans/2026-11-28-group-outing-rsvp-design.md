# Diseño: RSVP individual para salidas de grupo

## Qué muestra la auditoría

La pantalla ya ofrece «Asistiré», «Aún no lo tengo claro» y «No asistiré», pero la ruta cloud actual modifica `rsvpByUser`, contadores y `myRsvp` en el documento padre de la salida. Las reglas solo autorizan ese documento al propietario, de modo que el control visible no funciona para la mayoría de miembros y mezcla respuestas personales con datos editables de la salida. La rama ya valida grupo y generación para las salidas; la migración legacy sigue sin estar resuelta y no se desplegará nada desde esta tarea.

## Alternativas consideradas

1. **Seguir guardando el mapa de respuestas en la salida.** Es lo más sencillo de consultar, pero obliga a dar permisos de escritura sobre el documento compartido y expone a cambios accidentales o maliciosos de contadores y campos ajenos a la respuesta. Se descarta.
2. **Un documento por UID bajo la propia salida (`/outings/{outingId}/rsvps/{uid}`).** Aísla cada respuesta y permite que las reglas mantengan inmutables los demás campos. Tanto la consulta `collectionGroup("rsvps")` como la consulta de lista directa se descartan: las pruebas del emulador no pudieron demostrar de forma segura el vínculo entre cada documento consultado, su UID de ruta y la salida padre. La implementación usa `get`/listeners de documentos RSVP por ruta exacta, donde Rules valida UID, salida padre, generación y membresías.
3. **Una subcolección de respuestas por grupo.** Facilita una escucha única por grupo, pero desvía el dato de su salida y hace menos directa la validación de que la respuesta corresponde al evento concreto.

**Decisión:** usar la opción 2 con un listener por cada combinación salida×miembro v2 actual. Las lecturas de una respuesta existente solo se permiten si su autor sigue siendo miembro; una lectura vacía solo se permite si el usuario que consulta y el UID de la ruta son miembros v2 actuales de la generación de la salida. No se concede `list` ni `collectionGroup`. El coste escala como **N salidas × M miembros**, incluyendo rutas aún sin respuesta; cambios de grupo o de membresía pueden volver a crear listeners. Se acepta para el grupo pequeño del prototipo, pero antes de grupos grandes se debe medir lecturas/coste y diseñar una agregación de servidor autorizada. No se relajan las reglas para ahorrar listeners.

## Contrato de datos y permisos

Cada respuesta vive en `/outings/{outingId}/rsvps/{uid}` y contiene `outingId`, `userId`, `groupId`, `groupGenerationId`, `choice`, `createdAt` y `updatedAt`. `userId` debe coincidir con el UID autenticado y con el ID del documento. `choice` solo admite `YES`, `MAYBE` o `NO`; borrar el documento significa «Sin responder».

Crear, cambiar o borrar requiere que el solicitante tenga un documento de miembro v2 de la generación actual y que la salida padre exista y conserve el mismo grupo y generación. Las respuestas deben contener exactamente los siete campos del esquema: `outingId`, `userId`, `groupId`, `groupGenerationId`, `choice`, `createdAt` y `updatedAt`; un campo adicional invalida también la lectura. En una respuesta ya creada, solo cambian `choice` y `updatedAt`; se puede leer solo si el UID autor sigue siendo miembro v2. Un `get` vacío (la persona aún no respondió) se autoriza únicamente cuando la salida padre es de la generación actual y tanto quien consulta como el UID de ruta son miembros v2 actuales; esto deja ver quién aún no ha contestado dentro del grupo, requisito del listener, pero no revela estados de personas externas o expulsadas. Un documento huérfano, una ruta que no coincide con `outingId`, un UID distinto, una respuesta de otra generación, un miembro legacy sin backfill válido o un grupo sin registro verificable falla de forma cerrada. Las listas y collection-group permanecen denegadas.

Las respuestas y los contadores de una salida de grupo se derivan de esos documentos, no se escriben en la salida. Las nuevas salidas grupales comienzan con contadores cero y sin respuesta inicial; el creador puede responder después. Las salidas personales mantienen su ruta de propietario y su comportamiento actual. No se migran automáticamente mapas ni contadores legacy: una migración de confianza necesita su propio respaldo, validación y plan de compatibilidad.

Como Firestore Rules no puede enumerar y borrar de forma atómica todas las respuestas de otros miembros, una salida grupal no se elimina físicamente desde el cliente: se conserva y se marca como cancelada. Esto evita respuestas huérfanas que pudieran reaparecer si se reutiliza el mismo ID; las salidas personales siguen pudiéndose borrar.

## Implementación y verificación

1. Añadir el modelo/payload de respuesta; retirar el índice RSVP, porque no se ejecutan consultas RSVP.
2. Cambiar el repositorio para escuchar documentos RSVP puntuales por cada pareja salida×miembro vigente y escribir transaccionalmente solo la respuesta propia, comprobando padre, generación y membresía.
3. Integrar el estado agregado en el ViewModel y las pantallas de salidas/mapa; conservar el camino personal y bloquear el RSVP compartido si la membresía v2 no está verificada.
4. Endurecer las reglas para impedir que ni siquiera el propietario altere los campos RSVP del documento padre grupal; conservarlos solo para contenido personal.
5. Añadir pruebas Firestore de respuestas existentes, GET vacío propio/de otro miembro vigente, denegación a externos/miembros expulsados, rutas huérfanas/inconsistentes y concurrencia; añadir pruebas JVM de serialización, estados y payloads.
6. Ejecutar las pruebas Firestore contra un emulador aislado en puerto libre, revisar JSON/diff y dejar un commit local en la rama de integración.

Referencias oficiales consultadas: [Firestore Rules — Resource](https://firebase.google.com/docs/reference/rules/rules.firestore.Resource) y [Firestore Rules — Path](https://firebase.google.com/docs/reference/rules/rules.Path). Estas referencias respaldan la validación de datos y rutas; no reemplazan las pruebas con el emulador.

La ausencia de una migración legacy confiable sigue siendo un bloqueo explícito para desplegar estas reglas en producción. La verificación final de la rama debe usar solo el SDK Android/Build Tools ya instalados; no instalar componentes Android adicionales. No cambiar `main`, no hacer push ni despliegue de Firebase.
