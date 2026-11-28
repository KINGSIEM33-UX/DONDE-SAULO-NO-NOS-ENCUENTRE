# Adora — Arquitectura de Invitaciones Seguras, Límites sin Backend y Plan de Migración

> **Estado:** Prototipo verificado localmente en rama `cue/adora-final-integration` (**NO desplegado en Firebase**, cero facturación / Spark-compatible).

---

## 1. Vulnerabilidades corregidas del diseño anterior

| Fallo anterior | Impacto | Corrección implementada |
|---|---|---|
| `groupId == inviteCode` (`QUEDA-1000..9999`) | Solo 9 000 posibilidades; enumerables en segundos | `groupId` (`v2_grp_<26 Base32>`, **130 bits**) separado de los tokens `/group_invites/{token}` (`INV-…`, **160 bits**) |
| `allow get: if isSignedIn()` en `/groups/{groupId}` | Cualquier autenticado con el `groupId` leía nombre, `memberIds` y `memberNames` | `allow get: if isGroupMember(groupId)` — **cero fuga de datos privados** por conocer `groupId` |
| Autounión por `update` en `/groups/{groupId}` | Conocer `groupId` permitía autounirse y leer salidas/chat/historias | Puerta cerrada por completo; unirse requiere token activo en `/group_invites/{token}` |
| Miembros podían alterar `memberIds` arbitrariamente | Un miembro normal podía añadir o expulsar terceros | Membresía por documento individual `/groups/{groupId}/members/{uid}` + roles (`OWNER`, `ADMIN`, `MEMBER`) y reglas estrictas de cambios propios |
| `get()` + `update()` no atómico en repositorio | Altas concurrentes se pisaban entre sí (lost update) | `runTransaction` dual atómica (`/group_invites/{token}.useCount + 1` acoplado con `create /groups/{groupId}/members/{uid}` vía `getAfter()`) |

---

## 2. Propiedades garantizadas en Spark (solo Firestore Rules + Transacciones)

1. **Unión atómica e idempotente sin pérdida de altas simultáneas:** Cada miembro escribe su propio documento `/groups/{groupId}/members/{uid}` y la transacción actualiza `/group_invites/{token}` usando **`FieldValue.increment(1)`** (en lugar de un literal `useCount + 1`, que provocaría `PERMISSION_DENIED` antes del reintento optimista `ABORTED` bajo contención real — verificado en el Firestore Emulator). Dos altas concurrentes sobre un token multiuso **ambas triunfan**; dos altas concurrentes sobre un token de 1 uso garantizan que **exactamente una triunfa** y la segunda falla. Para la idempotencia dentro de la transacción, `allow get` en `/groups/{groupId}/members/{memberUid}` permite leer únicamente el propio slot `memberUid == request.auth.uid`, que devuelve `exists = false` de forma idéntica exista o no el grupo (**cero fuga de información**), mientras que leer a otros miembros o listar `members` exige membresía estricta.
2. **Revocación, expiración y límite de usos:** Verificados en el servidor por `firestore.rules` usando `request.time` (reloj de servidor, no el del cliente), `revoked == false` y `useCount < maxUses`. Además, los no-administradores ni siquiera pueden hacer `get` de un token revocado, caducado, agotado o inexistente.
3. **Aislamiento de roles y cambios propios:**
   - `MEMBER`: solo puede editar su propio `displayName` o salir (`delete` de su propio doc). No puede crear/revocar invitaciones, no puede escalar su `role` ni añadir/expulsar terceros.
   - `ADMIN`: puede crear/revocar invitaciones, renombrar el grupo y expulsar `MEMBER` (nunca `OWNER` ni otros `ADMIN`).
   - `OWNER`: puede gestionar invitaciones, renombrar, expulsar miembros no propietarios y promover/demotar `MEMBER ↔ ADMIN`. La eliminación del grupo también está cerrada al cliente; requiere un proceso privilegiado que limpie primero todo el subárbol.
4. **Compatibilidad con la futura integración de reglas de AI Studio:** Las secciones de `outings`, `chat_messages` y `stories` en `firestore.rules` conservan sus blueprints y pasan automáticamente a validar membresía dual mediante `isGroupMember(groupId)` (subcolección v2 + fallback legacy), listas para fusionar las reglas adicionales de creación de contenido de AI Studio sin conflictos estructurales.

---

## 3. Límites demostrables SIN backend privilegiado (transparencia total)

### 3.0 Ciclo de vida ALTO-4: namespace v2 + registry/tombstone inmutable

Los grupos nuevos usan IDs con prefijo `v2_` (por ejemplo `v2_grp_<26 Base32>`, 130 bits) dentro de `/groups/{id}`. El prefijo es un namespace lógico distinto del espacio legacy (`QUEDA-*`, `GRUPO-*`): las Rules rechazan crear cualquier grupo nuevo con un ID legacy; esos documentos solo pueden existir como datos previos y requieren migración privilegiada segura. La app Android genera ya el prefijo `v2_`.

Cada alta v2 debe ser una sola batch con tres escrituras: `groups_id_registry/{id}`, `groups/{id}` y `groups/{id}/members/{ownerUid}` con `role: OWNER`. El registry es una lápida inmutable: `get/list`, `update` y `delete` están cerrados al cliente; la creación exige que el grupo y OWNER existan también en `getAfter()`, que el creador autenticado coincida y que el ID no tenga registry ni grupo previo. Por tanto, una batch incompleta, una suplantación de owner, un registro huérfano, un prefijo no-v2 o un bypass por escritura separada falla de forma atómica. Si un migrador privilegiado elimina el grupo, la lápida permanece y bloquea toda recreación, incluso con el ID conocido y bajo carreras simultáneas.

Esto no es autenticación por entropía: las Rules no pueden demostrar que el solicitante “posee” un ID aleatorio. En esta implementación un cliente no puede sembrar anticipadamente una lápida porque `groups_id_registry` exige el grupo+OWNER en la misma batch; si una futura regla/migración privilegiada permite crear tombstones independientes, un atacante autenticado que adivine un ID podría ocuparlo y provocar DoS. La mitigación principal sigue siendo la entropía y la autorización privilegiada de migraciones, no tratar el prefijo o el azar como secreto.

### 3.0 Ciclo de vida ALTO-4: cierre fail-closed sin borrado descendente

Las reglas no permiten borrar `/groups/{groupId}` desde el cliente. El registry v2 evita además la recreación del ID; esto es deliberadamente fail-closed porque Firestore Rules no puede enumerar y tombstonear de forma atómica todos los documentos descendientes (`members/*`, invitaciones y contenido). Permitir el borrado del padre dejaría restos con autoridad; exigir únicamente que el padre exista tampoco protege contra borrar y recrear el mismo `groupId`.

Para restos legacy o grupos antiguos sin registry, una membresía v2 solo es vigente cuando el padre existe, el `groupId` coincide y `member.joinedAt >= group.createdAt`; una invitación v2 solo es vigente cuando `invite.createdAt >= group.createdAt`. Los restos anteriores a la nueva generación pierden lectura y autoridad aunque el ID del padre vuelva a existir. La misma frontera se aplica a contenido grupal (`outings`, `chat_messages` y `stories`): un miembro de la generación nueva solo puede leer o modificar contenido con `createdAt >= group.createdAt`, y las nuevas escrituras grupales deben satisfacer esa condición. El owner/autor/emisor conserva acceso a su propio documento histórico, que es un ámbito de propiedad directa y no una lectura del grupo recreado; eso no concede acceso a documentos de terceros. La ruta normal de creación/redención permanece compatible con los payloads actuales y los grupos legacy conservan su fallback `memberIds`; los listados grupales quedan deliberadamente cerrados hasta una migración segura.

`createdAt` y `joinedAt` siguen siendo valores aportados por el cliente y las Rules no los convierten en una prueba de generación criptográficamente segura. La comparación es una barrera monotónica de ciclo de vida, reforzada por la existencia del padre, la invitación vigente y la transacción de alta; no sustituye un reloj de servidor ni evita que un cliente elija una fecha pasada (esa escritura se rechaza para contenido grupal actual, pero un cliente puede conservar sus propios documentos históricos). Si la aplicación necesita garantías fuertes frente a eliminación/recreación administrada, debe bloquear la reutilización del ID o mover la creación de generaciones y marcas de tiempo a un backend privilegiado; mientras tanto, las Rules permanecen fail-closed y no eliminan registros.

**Límite de las consultas Firestore:** Rules no puede demostrar para cada candidato la comparación dinámica `resource.createdAt >= get(parent).createdAt` en una consulta `list`. Por eso las listas del feed privado usan como frontera de autorización el `groupGenerationId` comparado con el grupo y su registry inmutable, además de exigir que la consulta quede acotada al grupo y generación actuales y que el usuario sea miembro actual. El cliente sigue sin poder crear posts con timestamp anterior al inicio del grupo: la regla de creación valida esa condición, y la lectura directa también la conserva. No se deben etiquetar posts históricos con la generación activa durante una migración; los datos antiguos sin generación válida quedan fuera de las consultas. Este diseño conserva queries válidas sin tratar un timestamp aportado por cliente como prueba de identidad de generación. La migración legacy y el despliegue siguen bloqueados hasta completar el inventario y el backfill privilegiado verificado descrito abajo.

Si en el futuro se necesita eliminar grupos, debe hacerse mediante una migración privilegiada que enumere y respalde primero todo el árbol, elimine miembros/invitaciones/contenido y solo después elimine el padre; no debe reutilizarse el ID hasta completar esa operación. Esta rama no borra datos ni implementa ese backend.

No se ha activado facturación ni Cloud Functions. Sin un backend privilegiado (Admin SDK / Cloud Function), **Firestore Rules + transacciones de cliente NO pueden garantizar estas 4 propiedades**:

1. **Sin hashing criptográfico de tokens en servidor:** Firestore Rules CEL carece de `sha256()`. El ID del documento en `/group_invites/{token}` es el propio token en texto plano (mitigado por **160 bits de entropía** y `allow list` bloqueado a no-admins, pero un administrador con acceso directo a la consola de Firestore vería el token en claro).
2. **Sin rate-limiting por IP/usuario en intentos de `get /group_invites/{token}`:** Firestore Rules no mantienen contadores de intentos fallidos. La resistencia a fuerza bruta descansa en los 160 bits ($2^{160} \approx 1.46 \times 10^{48}$ combinaciones).
3. **Expulsión completa de miembros legacy mientras exista el fallback `memberIds`:** Mientras la regla `isGroupMember()` mantenga el fallback temporal `uid in group.memberIds` para conservar compatibilidad con grupos legacy, expulsar a un miembro legacy borrando solo `/members/{uid}` **no revoca su acceso** hasta que el `OWNER` también retire su UID del array legacy `memberIds` (permitido de forma segura mediante poda estrictamente decreciente `existing().memberIds.hasAll(incoming().memberIds)`). Solo al completar la **Fase 3** (retirar el fallback `memberIds`) la expulsión por subcolección es única y absoluta.
4. **Incompatibilidad inevitable con clientes antiguos al cerrar el autounirse legacy:**
   - Los **miembros ya existentes** en `memberIds` de grupos legacy siguen pudiendo leer el grupo, salidas, chat e historias.
   - Sin embargo, **los clientes antiguos (anteriores a este commit) que intenten unirse con el antiguo flujo `joinGroupByCode` escribiendo en `/groups/{code}.memberIds` recibirán `PERMISSION_DENIED`** en cuanto se desplieguen estas reglas.
   - **No existe forma segura de permitir que clientes antiguos se autounan por `groupId` sin dejar abierta la vulnerabilidad original.** Por ello, el sistema **NO debe declararse completamente cerrado en producción** hasta completar las fases de migración abajo descritas.

---

## 4. Migración legacy — blocker de despliegue (NO IMPLEMENTADA)

El plan anterior de self-backfill desde el cliente **no puede arrancar sobre un grupo legacy real que carezca de `generationId` y registro**. Las reglas actuales exigen que `isCurrentGroupGeneration()` ya sea verdadero antes de permitir la creación del documento `/groups/{groupId}/members/{uid}`; por tanto, el cliente no puede crear por sí mismo el estado que necesita para pasar esa comprobación. La prueba de backfill legacy del emulador prepara previamente esos campos, así que no valida el caso de producción sin registro/generación. No se debe desplegar estas reglas esperando que los clientes completen ese backfill.

**Estado:** no existe todavía un migrador privilegiado verificado para los grupos legacy reales. Mientras siga pendiente, no desplegar `firestore.rules` ni dar por resuelta la migración. No se han migrado, borrado ni reescrito datos de producción.

### Trabajo obligatorio antes de cualquier despliegue
1. **Inventario solo lectura y respaldo:** listar grupos legacy/v2, generaciones y registros existentes, miembros, invitaciones y documentos descendientes; identificar campos ausentes, huérfanos, timestamps y posibles colisiones. Para cada RSVP, cotejar la ruta `/outings/{outingId}/rsvps/{uid}` con `outingId`, `userId`, grupo/generación y el documento padre; aislar para reconciliación los RSVPs huérfanos o inconsistentes y nunca etiquetarlos automáticamente con la generación activa. Guardar un export verificable antes de cualquier escritura.
2. **Diseñar un migrador privilegiado e idempotente:** usar una vía autorizada de servidor/Admin SDK para asignar la generación y registro que requieren las reglas y crear membresías a partir de datos verificados. Preservar IDs, `createdAt` y contenido existente; no inferir roles ni sobrescribir campos sin reconciliar conflictos. El cliente no puede sustituir este paso.
3. **Preflight y ensayo:** ejecutar primero un modo de solo lectura/dry-run y luego probar el migrador en una copia de datos. Incluir fixtures legacy sin generación ni registro, con miembros faltantes, documentos huérfanos, timestamps antiguos y reintentos. Comparar recuentos y probar lecturas/escrituras permitidas y denegadas con las reglas exactas.
4. **Corte controlado:** definir copia de seguridad, ventana de ejecución, reconciliación, criterios de éxito y rollback antes de tocar datos reales. Solo tras verificar el backfill completo se puede evaluar el despliegue de reglas y la retirada posterior del fallback `memberIds`.

La migración y el despliegue quedan fuera de esta rama. Cualquier regla que deje fuera a grupos legacy sin backfill válido es fail-closed de forma intencional; no debe “arreglarse” abriendo de nuevo la lectura o la auto-unión por `groupId`.

---

## 5. Prototipo de backend privilegiado (NO desplegado — referencia futura si se habilita Blaze)

Si en el futuro el proyecto activa un backend privilegiado (p. ej. Cloud Functions / Cloud Run), estas dos operaciones moverían las garantías restantes a servidor (hashing SHA-256 + rate limiting + ocultación total de `groupId` antes de validar):

```js
// PROTOTIPO DE REFERENCIA (NO DESPLEGADO — requiere backend privilegiado)
// POST /invites/redeem { rawToken } con Firebase ID Token verificado:
// 1. tokenHash = sha256(normalize(rawToken))
// 2. Rate-limit por uid/IP (p. ej. 10 intentos / 15 min)
// 3. adminDb.runTransaction:
//    - Lee /group_invites_hashed/{tokenHash} (nunca legible por clientes)
//    - Valida !revoked && expiresAt > now && useCount < maxUses
//    - Si /groups/{groupId}/members/{uid} no existe:
//        * Incrementa useCount + 1
//        * Crea /groups/{groupId}/members/{uid} { role: "MEMBER", ... }
//    - Devuelve { groupId, groupName } solo tras éxito
```
