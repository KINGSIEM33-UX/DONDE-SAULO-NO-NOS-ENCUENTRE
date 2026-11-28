# Adora — Prototipo Aislado de Autorización de Contenido Grupal, Inmutabilidad y Contadores Verificables

> **Estado:** Prototipo aislado en rama `cue/adora-content-authz-prototype-20261128` (basada en `8c55702`). **NO desplegado en Firebase y NO integrado todavía en la rama principal de integración** (que contiene protecciones adicionales de sesión, media y feed). Cero servicios de pago (100 % Spark-compatible).

---

## 1. Vulnerabilidades de auditoría cerradas en este prototipo

| Vulnerabilidad detectada | Corrección en `firestore.rules` + cliente |
|---|---|
| No miembros (y ex-miembros) podían crear `outings`, `chat_messages` y `stories` para grupos ajenos o ámbitos `personal_<otroUid>` | Helper `canAccessGroupScope(groupId)` / `canAccessStoryScope(data)` obligatorio en `create`, `update` y `delete`: exige membresía actual (`isGroupMember` v2 + fallback legacy `memberIds`) o ámbito personal propio (`personal_<uid>`). |
| Miembros (y dueños) podían mutar `groupId` u `ownerId` de una salida | `ownerId`, `groupId` y `createdAt` son estrictamente inmutables en `outings`, `chat_messages` (`senderId`/`groupId`) y `stories` (`authorId`/`groupId`). Eliminado `groupId` de `Outing.toUpdateMap()`. |
| Miembros no propietarios podían editar, cancelar (`status = 'CANCELLED'`), mover coordenadas, alterar `albumPhotos` o borrar salidas ajenas | Campos editoriales de `outings`, `text`/`transcription` de `chat_messages` y `caption` de `stories` restringidos estrictamente al creador/propietario actual (`existing().ownerId/senderId/authorId == request.auth.uid`). |
| Miembros podían alterar el RSVP de terceros, sobrescribir `myRsvp` compartido o falsificar contadores (`rsvpYesCount`, `likesCount`) y arrays `reactions` | Transiciones verificadas por inducción matemática en servidor (ver §2) + escritura transaccional por clave propia (`FieldPath.of("rsvpByUser", uid)` / `reactionsByUser` / `likedByUser`) + `FieldValue.increment(delta)`. |

---

## 2. Contadores verificables sin backend: diseño elegido y trade-offs honestos

### Cómo se garantizan contadores verificables en Spark (sin Cloud Functions)

1. **Caso base (`create`):**
   - `outings`: `isValidInitialOutingRsvp` exige que `rsvpByUser` sea `{}` con contadores `(0, 0, 0)` y `myRsvp == 'NONE'`, **o** exactamente `{[uid]: choice}` con el único contador correspondiente a `1` y los otros dos a `0`. Es imposible crear una salida con `rsvpYesCount: 99` o con UIDs de terceros.
   - `stories`: `isValidInitialStoryLikes` exige `likesCount == 0` con `likedByUser == {}` (o `1` solo con `{[uid]: true}`).
   - `chat_messages`: exige `reactions == []` y `reactionsByUser == {}` al crear.
2. **Paso inductivo (`update`):**
   - Las ediciones editoriales del propietario **no pueden tocar** `rsvpByUser`, `rsvpYesCount`, `rsvpMaybeCount`, `rsvpNoCount`, `myRsvp`, `reactionsByUser`, `likedByUser` ni `likesCount`.
   - Las actualizaciones interactivas de cualquier miembro actual **solo pueden tocar su propia clave `request.auth.uid`** en el mapa (`newMap.diff(oldMap).affectedKeys().hasOnly([request.auth.uid])`) y ajustar los contadores exactamente según el delta matemático `(newChoice == X ? 1 : 0) - (oldChoice == X ? 1 : 0)` (o `+1/-1` en `likesCount`).
3. **Concurrencia real sin pérdida de altas/votos:**
   - El cliente usa `db.runTransaction` + `FieldPath.of("rsvpByUser", uid)` (nunca sobrescribe el mapa entero) + `FieldValue.increment(delta)`. Bajo contención simultánea, el segundo commit supera las reglas antes de recibir `ABORTED`, reintenta automáticamente y **ambas escrituras triunfan con contadores exactos** (verificado en el Firestore Emulator con `Promise.all`).

### Trade-offs y limitaciones honestas sin backend privilegiado

1. **Documentos legacy con deriva previa:** Los documentos creados antes de desplegar estas reglas (p. ej. una salida legacy con `rsvpYesCount = 1` sin campo `rsvpByUser`, o una historia legacy con `likesCount = 2` sin `likedByUser`) **se preservan intactos sin migración destructiva**. Las nuevas interacciones aplican deltas exactos sobre esa línea base (`oldCount + delta`), pero Firestore Rules CEL no puede iterar sobre mapas para recalcular desde cero una deriva histórica ya existente sin un proceso de reconciliación backend.
2. **Límite de tamaño y throughput por documento único:** Los mapas `rsvpByUser`, `reactionsByUser` y `likedByUser` están acotados a `<= 50` entradas por documento (adecuado para grupos privados de amigos) y sujetos al límite blando de Firestore de ~1 escritura sostenida/segundo por documento. Si en el futuro Adora soportara eventos masivos (>50 asistentes o decenas de RSVPs por segundo), habría que migrar a subcolecciones `/outings/{id}/rsvps/{uid}` con agregación en servidor (Cloud Function / Cloud Run).
3. **Compatibilidad con clientes antiguos:** Al desplegar estas reglas, los clientes antiguos que envíen `groupId` o contadores dentro de `Outing.toUpdateMap()`, que sobrescriban el array `reactions`, que envíen `myRsvp` al votar RSVP o que creen `stories` con `likesCount = 1` sin `likedByUser` recibirán `PERMISSION_DENIED`. Se debe actualizar el cliente antes del despliegue de reglas (misma política por fases que las invitaciones).
4. **Grupos legacy (`memberIds`) y expulsión:** Mientras permanezca activo el fallback de lectura `isLegacyGroupMember()` en `firestore.rules` (Fase 2), la revocación de escritura a un ex-miembro de grupo legacy exige también podar su UID de `memberIds` (o completar Fase 3 retirando el fallback).

---

## 3. Cómo combinar este prototipo aislado con la rama de integración (sin revertir sesión, media ni feed)

Este prototipo **NO asume ni sustituye** las protecciones adicionales de sesión, media o feed que puedan existir en la rama de integración principal. Ruta recomendada de combinación por archivos:

1. **`firestore.rules`:**
   - Fusionar los helpers de ámbito (`isPersonalScope`, `canAccessGroupScope`, `canAccessStoryScope`) y de deltas propios (`isValidInitialOutingRsvp`, `isValidSelfRsvpUpdate`, `isValidSelfChatReactionUpdate`, `isValidInitialStoryLikes`, `isValidSelfStoryLikeUpdate`) manteniendo intactos los bloques de `/groups`, `/members`, `/group_invites` y cualquier restricción extra de validación de URIs de media o campos de sesión presentes en la integración.
   - Si la integración restringe `mediaUri` / `audioUrl` / `placePhotoUrl` / `albumPhotos` (p. ej. solo `https://` de Firebase Storage o presets), conservar esas comprobaciones dentro de `isValidOuting`, `isValidChatMessage` e `isValidStory`.
2. **`app/src/main/java/com/example/data/SocialContentAuthzPolicy.kt` (nuevo archivo sin conflictos):**
   - Incorporar íntegro junto a `SocialSessionPolicy.kt` (no modifica `SocialSessionPolicy` ni el ciclo de vida de sesión/feed).
3. **`SocialModels.kt` y `SocialRepository.kt`:**
   - Adoptar los cambios puntuales de `Outing.toCreateMap` (contadores iniciales consistentes), `Outing.toUpdateMap` (solo campos editoriales, sin `groupId` ni RSVP), los campos opcionales `reactionsByUser` / `likedByUser`, y las 3 transacciones con `FieldPath` + `FieldValue.increment` (`updateOutingRsvpTransactional`, `toggleMessageReactionTransactional`, `toggleStoryLikeTransactional`), preservando cualquier lógica adicional de validación de media o paginación de feed de la rama de integración.
4. **`MainViewModel.kt`, `World1OutingsScreen.kt`, `World1MapScreen.kt`, `World1ChatScreen.kt`:**
   - Aplicar solo las guardas de propiedad (`canEditOrCancelOuting`, `canDeleteOuting`, `canWriteToGroupScope`), la lectura `effectiveOutingRsvpForUser` y el render `effectiveReactions()`, conservando sin cambios los estados de carga/error de feed, subida de media y protecciones de sesión de la integración.
5. **Orden de despliegue seguro (cuando se autorice):**
   - 1º integrar y compilar la app cliente combinada + ejecutar `firestore.test.js` completo (incluyendo casos de sesión/media/feed de la integración) → 2º publicar actualización de cliente → 3º desplegar `firestore.rules`.
