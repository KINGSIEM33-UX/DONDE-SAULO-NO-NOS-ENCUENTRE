# Prototipo de seguridad de Firebase Storage

## Hallazgos del flujo actual

`MediaStorageHelper` copia imágenes a un archivo local y, para usuarios autenticados, sube a:

- `users/{uid}/album/{filename}.jpg`
- `users/{uid}/stories/{filename}.jpg`
- `users/{uid}/voice_notes/{filename}.m4a`

Después llama a `getDownloadUrl()` y guarda la URL HTTPS en `albumPhotos`, `mediaUri`, `avatarUrl` o `audioUrl`. El objeto no lleva `groupId`, aunque el documento Firestore sí suele llevarlo. La reproducción de audio y la carga de imágenes consumen directamente esa URL remota; no reconstruyen una referencia Storage ni vuelven a autorizar al miembro.

## Límites y decisión

Cloud Storage Rules sí pueden comprobar `request.resource.size`, `request.resource.contentType`, UID y, mediante `firestore.get/exists`, membresías. Firebase documenta que esas lecturas desde Storage Rules solo acceden a la base Firestore **default** cuando hay varias bases. Este proyecto configura Firestore con la base nombrada `ai-studio-android-dondesau-...`; por ello una regla que consulte `/databases/(default)/documents/groups/...` no representa la membresía de producción actual. No se habilita una falsa autorización de miembros contra la base equivocada.

Las URLs de descarga de `getDownloadUrl()` contienen un token bearer. Quien posee una URL puede descargar el objeto sin presentar el contexto Auth que evalúan las Storage Rules; cambiar una regla no revoca una URL ya copiada. El modelo actual, por tanto, no puede prometer privacidad de miembros. La ruta legacy conserva únicamente lectura/borrado del propietario para nuevas operaciones SDK y documenta esta limitación; no se cambia la app ni se rompen URLs existentes.

## Prototipo implementado

`storage.rules` contiene:

1. Compatibilidad explícita con `users/{uid}/{folder}/{fileName}`. Solo el propietario autenticado puede crear/actualizar/borrar; se aceptan `album` y `stories` como imagen JPEG/PNG/WebP de menos de 10 MiB, y `voice_notes` como audio permitido de menos de 10 MiB. Lectura por SDK: solo propietario.
2. Ruta de migración `groups/{groupId}/media/{uid}/{folder}/{fileName}`. Solo un miembro puede leer; solo el miembro cuyo UID aparece en la ruta puede escribir/borrar. Usa la membresía de `(default)` de forma deliberada y no debe desplegarse como autorización de la base nombrada actual sin migrar la base o cambiar el modelo de autorización.
3. Denegación por defecto para cualquier otra ruta.

El límite es estricto (`< 10 MiB`), no inclusivo. Las reglas no validan extensión ni contenido real; `contentType` es metadata proporcionada por el cliente y debe tratarse como defensa de abuso, no como escaneo antivirus.

## Migración compatible propuesta

1. Elegir una única fuente de autorización compatible con Storage Rules: migrar la membresía necesaria a `(default)` o emitir claims Auth de membresía desde un backend confiable. No copiar automáticamente la base nombrada ni ampliar lecturas públicas.
2. Cambiar el cliente para aceptar un `groupId` explícito y subir a `groups/{groupId}/media/{uid}/{folder}/{filename}`. El mismo cambio debe sustituir las URLs persistidas por una referencia de objeto (por ejemplo, `storagePath`) o por una capa de descarga con SDK; no basta con cambiar la regla.
3. Para descargas privadas, reconstruir `StorageReference` desde `storagePath` y usar descarga SDK (`getBytes`/`getFile`/`getStream`) bajo Auth. Mantener `downloadUrl` solo como formato legacy y planificar rotación/eliminación de tokens y limpieza de objetos antiguos.
4. Migrar documentos existentes de forma gradual: escribir el nuevo path, verificar lectura de miembro/no miembro, actualizar Firestore y solo entonces retirar el objeto legacy. Invitaciones aún no integradas no deben recibir autorización implícita.
5. Antes de desplegar, probar contra una representación real de la fuente de membresía y revisar IAM para permitir a Storage Rules la lectura Firestore cuando corresponda.

No se instalaron SDKs/dependencias, no se accedió a Firebase real y no se desplegó nada en este prototipo.
