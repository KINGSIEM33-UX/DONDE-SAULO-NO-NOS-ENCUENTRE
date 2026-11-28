# Adora — Arquitectura de Invitaciones Seguras, Límites sin Backend y Plan de Migración

> **Estado:** Prototipo verificado localmente en rama `cue/adora-invites-prototype-20261128` (**NO desplegado en Firebase**, cero facturación / Spark-compatible).

---

## 1. Vulnerabilidades corregidas del diseño anterior

| Fallo anterior | Impacto | Corrección implementada |
|---|---|---|
| `groupId == inviteCode` (`QUEDA-1000..9999`) | Solo 9 000 posibilidades; enumerables en segundos | `groupId` (`grp_<26 Base32>`, **130 bits**) separado de los tokens `/group_invites/{token}` (`INV-…`, **160 bits**) |
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
   - `OWNER`: todos los permisos + promover/demotar `MEMBER ↔ ADMIN` + eliminar el grupo.
4. **Compatibilidad con la futura integración de reglas de AI Studio:** Las secciones de `outings`, `chat_messages` y `stories` en `firestore.rules` conservan sus blueprints y pasan automáticamente a validar membresía dual mediante `isGroupMember(groupId)` (subcolección v2 + fallback legacy), listas para fusionar las reglas adicionales de creación de contenido de AI Studio sin conflictos estructurales.

---

## 3. Límites demostrables SIN backend privilegiado (transparencia total)

### 3.0 Ciclo de vida ALTO-4: cierre fail-closed sin borrado descendente

Las reglas no permiten borrar `/groups/{groupId}` desde el cliente. Esta decisión es deliberadamente fail-closed: Firestore Rules no puede enumerar y tombstonear de forma atómica todos los documentos descendientes (`members/*`, invitaciones y contenido). Permitir el borrado del padre dejaría restos con autoridad; exigir únicamente que el padre exista tampoco protege contra borrar y recrear el mismo `groupId`.

Para defenderse también de una recreación administrada del mismo ID, una membresía v2 solo es vigente cuando el padre existe, el `groupId` coincide y `member.joinedAt >= group.createdAt`; una invitación v2 solo es vigente cuando `invite.createdAt >= group.createdAt`. Los restos anteriores a la nueva generación pierden lectura y autoridad aunque el ID del padre vuelva a existir. La ruta normal de creación/redención permanece compatible con los payloads actuales y los grupos legacy conservan su fallback `memberIds`.

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

## 4. Camino de migración y backfill reversible (3 fases, sin pérdida de datos)

### Fase 1 — Despliegue de app cliente (100 % compatible y reversible)
- Publicar la actualización de Android con soporte dual (lee `/members/{uid}` + fallback `memberIds`).
- Cuando un miembro existente abre su grupo legacy (`QUEDA-2026`, etc.), la app ejecuta **self-backfill perezoso e idempotente** (`backfillLegacyMembershipIfNeeded`): crea su propio `/groups/{groupId}/members/{uid}` con su rol exacto (`OWNER` si `createdBy == uid`, `MEMBER` en caso contrario), **sin modificar ni borrar `groupId`, `outings`, `chat_messages` ni `stories`**.
- **Rollback de Fase 1:** Trivial — los documentos `/members/{uid}` no rompen clientes antiguos y los campos legacy en `/groups/{groupId}` siguen intactos.

### Fase 2 — Despliegue de `firestore.rules` (cierra la puerta de autounión por `groupId`)
- Verificar que los usuarios activos ya usan la app actualizada.
- Desplegar `firestore.rules`. A partir de aquí:
  - Ningún no-miembro puede leer `/groups/{groupId}` ni autounirse con `groupId`.
  - Los dueños de grupos legacy (`createdBy == uid`) ya pueden emitir tokens de alta entropía (`INV-…`) para su mismo `groupId` legacy sin migrar salidas/chat/historias.
  - Los miembros legacy aún pendientes siguen pudiendo leer sus datos y completar su self-backfill.
- **Rollback de Fase 2:** Revertir `firestore.rules` (solo si fuese necesario en emergencia, sabiendo que reabriría la vulnerabilidad).

### Fase 3 — Retirada del fallback `memberIds` (cierre definitivo)
- Tras confirmar que todos los grupos legacy activos tienen poblada su subcolección `/groups/{groupId}/members/{uid}`:
  - Eliminar el fallback `isLegacyGroupMember()` de `firestore.rules` y opcionalmente limpiar `memberIds`/`memberNames`/`inviteCode` obsoletos mediante script de backfill con copia de seguridad previa.

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
