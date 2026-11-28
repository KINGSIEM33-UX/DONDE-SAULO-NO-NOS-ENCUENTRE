# Diseño: seguridad aislada de Storage para Adora

## Contexto

La app actual sube medios a `users/{uid}/{folder}/{filename}`, persiste `getDownloadUrl()` y descarga usando esa URL. Los documentos sociales incluyen `groupId`, pero el objeto Storage no. Firebase Storage Rules puede consultar Firestore, con un máximo de dos documentos por evaluación, pero cuando hay múltiples bases solo puede consultar `(default)`. El proyecto usa una base Firestore nombrada.

## Decisión

Añadir `storage.rules` sin cambiar el cliente: la ruta legacy admite únicamente operaciones autenticadas del propietario y valida carpeta, tipo MIME y tamaño inferior a 10 MiB. Añadir una ruta futura `groups/{groupId}/media/{uid}/{folder}/{filename}` que autoriza miembros consultando `(default)` y exige que el UID de la ruta sea el UID autenticado. Denegar cualquier otra ruta.

No se intentará convertir `downloadUrl` en una URL privada: su token bearer funciona fuera del contexto de Rules. Tampoco se autorizará una membresía consultando la base nombrada porque produciría una falsa sensación de seguridad. La migración exige cambiar path, modelo de autorización y lectura a Storage SDK; las invitaciones no integradas quedan fuera.

## Verificación

Se añade `storage.test.js` para emulador con casos de propietario, UID ajeno, tipos inválidos, tamaño, miembro/no miembro y suplantación del UID de ruta. `firebase*.json` y un workflow separado configuran Storage en `9199`. No se instalan dependencias ni se contacta Firebase real desde este prototipo.
