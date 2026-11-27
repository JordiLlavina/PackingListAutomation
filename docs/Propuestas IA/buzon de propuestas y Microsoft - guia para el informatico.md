# Buzón de propuestas y Microsoft — guía para el informático

Fecha: 2026-11-27. Para quien administra Microsoft 365 / Entra y el servidor de Puntotres.

Son **dos peticiones independientes**. La primera (el buzón) hace falta pronto. La
segunda (login con cuentas de Microsoft) es para más adelante y se recoge aquí solo
para tener todo en un sitio.

## 1. Qué se quiere hacer, en dos párrafos

El programa que ya corre en el servidor del almacén (`/opt/packinglist`, puerto 8080)
va a poder **recibir trabajo por correo**: alguien de la oficina reenvía a una
dirección reservada un email con, por ejemplo, la ficha técnica de una piel, y el
programa lo lee, extrae los datos con IA y deja una **propuesta** que una persona
acepta o descarta en pantalla. Nada se guarda sin que alguien lo acepte.

El servidor **no se abre a internet**. Es el programa el que sale a preguntar a
Microsoft cada minuto o dos si hay correo nuevo (Microsoft Graph, HTTPS de salida).
No hay webhooks, ni puertos de entrada, ni reenvíos automáticos hacia el servidor.

## 2. Lo que se pide para el buzón (resumen)

| # | Qué | Cómo se comprueba |
|---|---|---|
| 1 | Un **buzón compartido** nuevo, reservado para esto | Aparece en Exchange; se le puede escribir desde una cuenta de la empresa |
| 2 | Que el buzón **solo acepte correo de cuentas autenticadas de la organización** | Un correo desde una cuenta externa (Gmail) se rechaza |
| 3 | Un **registro de aplicación en Entra** con permisos de correo, **limitado a ese buzón** | Con sus credenciales se lee ese buzón y da error al intentar leer cualquier otro |
| 4 | Salida del servidor a `login.microsoftonline.com` y `graph.microsoft.com` por el 443 | `curl -I https://graph.microsoft.com` desde el servidor responde |
| 5 | Cuatro valores entregados a Jordi (apartado 6) y puestos en el servicio | El servicio arranca sin `WARN` de configuración |

## 3. El buzón

- **Tipo**: buzón compartido de Exchange Online. No necesita licencia mientras no
  pase de 50 GB, y nadie inicia sesión en él.
- **Dirección**: la que diga Jordi (provisional: `ia@puntotres.com`). Una sola; no
  hacen falta alias.
- **Solo correo interno**. Con Exchange Online PowerShell:

  ```powershell
  Set-Mailbox -Identity ia@puntotres.com -RequireSenderAuthenticationEnabled $true
  ```

  Si preferís una regla de flujo de correo que rechace lo que venga de fuera de la
  organización para ese destinatario, vale igual. Lo que importa es que **desde
  fuera no entre nada**: el programa da por bueno el remitente que ve.
- **Acceso de personas**: ninguno necesario. Si queréis que Jordi pueda abrirlo en
  Outlook para ver qué ha llegado, dadle acceso completo de lectura.
- **Retención**: el programa mueve cada correo tratado a una carpeta `Procesados`
  (o `Rechazados`). Podéis poner la política de retención que uséis en el resto.

## 4. El registro de aplicación en Entra

Entra → Registros de aplicaciones → Nuevo registro.

- **Nombre**: `Puntotres - Buzón de propuestas` (o el que sigáis por convención).
- **Tipo de cuenta**: solo este directorio (single tenant).
- **URI de redirección**: ninguna. Es un servicio sin usuario delante (flujo
  *client credentials*).
- **Permisos de API** → Microsoft Graph → **permisos de aplicación** (no delegados):
  - `Mail.ReadWrite` — leer los correos y sus adjuntos, marcarlos y moverlos de carpeta.
  - `Mail.Send` — contestar a quien reenvió con el resumen de lo que se ha entendido.
  - Conceder el **consentimiento de administrador**.
- **Credencial**: un *client secret* (o un certificado, si lo preferís). El secreto
  **caduca** (máximo 24 meses): apuntad la fecha y quién lo custodia el mismo día.
  Cuando caduque, el programa deja de leer el buzón y lo avisa en pantalla, pero
  nadie recibirá un correo diciéndolo.

### Limitarlo a ese buzón (imprescindible)

Tal cual, esos dos permisos de aplicación valen para **todos los buzones de la
empresa**. Hay que acotarlos al buzón de propuestas. Dos formas:

- **Recomendada: RBAC para aplicaciones de Exchange Online.** Se registra el
  *service principal* de la aplicación en Exchange (`New-ServicePrincipal`), se crea
  un ámbito de administración que solo contenga el buzón (`New-ManagementScope`) y se
  le asignan los roles `Application Mail.ReadWrite` y `Application Mail.Send` con ese
  ámbito (`New-ManagementRoleAssignment`). En ese caso los permisos de Graph del
  apartado anterior **no se conceden en Entra**: se dan solo por aquí.
- **Alternativa (método anterior): directiva de acceso de aplicación.** Un grupo de
  seguridad con correo que contenga solo el buzón y
  `New-ApplicationAccessPolicy -AccessRight RestrictAccess` para el id de la aplicación.

Cualquiera de las dos vale. Comprobación con `Test-ServicePrincipalAuthorization`
(RBAC) o `Test-ApplicationAccessPolicy` (directiva): el buzón de propuestas debe dar
acceso y **cualquier otro buzón, denegado**.

## 5. Cortafuegos

Solo **salida** del servidor, por el 443, a:

- `login.microsoftonline.com` (obtener el token)
- `graph.microsoft.com` (leer y contestar el correo)

Se suman a los que ya usa (`api.anthropic.com`, Azure Speech, servidores de push).
**Ninguna entrada nueva.**

## 6. Lo que hay que entregar a Jordi y dónde se pone

En `/etc/packinglist.env` (permisos `600`, como las demás claves):

```
BUZON_TENANT_ID=<id del directorio>
BUZON_CLIENT_ID=<id de la aplicación registrada>
BUZON_CLIENT_SECRET=<el secreto; o la ruta del certificado si se usa certificado>
BUZON_DIRECCION=ia@puntotres.com
```

Y `systemctl restart packinglist`. Sin estas variables el programa arranca igual y
todo lo demás funciona; solo queda apagada la entrada por correo.

(Los nombres de las variables son provisionales hasta que la función esté programada.)

## 7. Lo que NO hace falta

- Licencia para el buzón.
- Abrir puertos de entrada, publicar el servidor o crear registros DNS.
- Power Automate, conectores, reglas de reenvío o complementos de Outlook.
- Instalar nada en los PCs ni en los móviles: se reenvía desde el Outlook de siempre.
- Dar al programa acceso a los buzones de las personas.

## 8. Privacidad: qué sale de la empresa

El texto del correo reenviado y sus PDF se envían a la API de Anthropic para leerlos,
igual que ya se hace hoy con las fotos de las hojas del almacén. Solo se envía lo que
alguien reenvía a ese buzón a propósito; el programa no ve ningún otro correo.

## 9. Aparte: Claude leyendo Outlook en una conversación

Es otra cosa distinta del buzón y **no depende de él**: el conector de Microsoft 365
de Claude deja que una persona, hablando con Claude, le pida que lea *su propio*
correo. Actúa con los permisos de esa persona, no con los de una aplicación. Si en el
tenant el consentimiento de usuarios está restringido, un administrador tiene que
aprobar ese conector una vez. No se pide nada más para esto.

## 10. Para más adelante: login con cuentas de Microsoft

**No hacerlo ahora.** Cuando toque, el diseño completo está en
`TO DO/2026-09-19-login-microsoft-y-actividad-design.md` (apartados 4.1 y 11). En
resumen se pedirá:

1. **Otro registro de aplicación en Entra**, distinto del del buzón: single tenant,
   URIs de redirección web `https://<nombre público>/login/oauth2/code/azure` y
   `http://localhost:8080/login/oauth2/code/azure`, permisos solo `openid`, `profile`,
   `email` (nada de Graph), y su *client secret* con la caducidad apuntada.
   Valores: `ENTRA_TENANT_ID`, `ENTRA_CLIENT_ID`, `ENTRA_CLIENT_SECRET`.
2. **Publicación sin puertos de entrada** si se puede (Cloudflare Tunnel o Entra
   Application Proxy); si hay que abrir el 443, la máquina en una VLAN aislada.
3. **TLS** válido con renovación automática y cabeceras `X-Forwarded-*` correctas en
   el proxy (lo montado para `modelos.puntotres.com` se reutiliza).
4. **PostgreSQL** en lugar de la base embebida.
5. **Copia diaria fuera de la máquina**, con restauración probada, y disco cifrado.
6. Actualizaciones de seguridad automáticas y administración solo por SSH con clave.
7. **DNS interno** para que el nombre público resuelva también dentro de la oficina.
8. Saber **qué licencia de Microsoft 365** hay: Entra Application Proxy requiere
   Entra ID P1 (incluida en Business Premium).

Se usan dos registros separados a propósito: el del login no debe tener permisos de
correo, y el del buzón no debe poder iniciar sesiones de usuario.
