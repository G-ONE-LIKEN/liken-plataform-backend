# KYC con Didit — Guía de prueba end-to-end

Guía explicativa para probar el flujo completo de verificación de identidad automatizada
con Didit en el entorno local, backend de Liken.

---

## Contexto

El módulo KYC con Didit permite que un usuario verifique su identidad de forma automática,
sin intervención manual de un administrador. El flujo tiene tres etapas:

1. El backend crea una sesión en Didit y devuelve una URL al usuario
2. El usuario completa la verificación en el widget de Didit (foto de DNI + selfie o el tipo de KYC customizado que se elija en Didit)
3. Didit llama al webhook del backend con el resultado, y el backend actualiza el estado del usuario automáticamente

---

## Requisitos previos

- Stack local levantado y todos los servicios en estado `healthy` (`docker compose ps`)
- Cuenta en [didit.me](https://didit.me) con una aplicación configurada
- ngrok instalado (`ngrok --version`)
- Variables de entorno en `.env`:
  ```
  DIDIT_API_KEY=<tu_api_key>
  DIDIT_WORKFLOW_ID=<tu_workflow_id>
  DIDIT_CALLBACK_URL=http://localhost:3000/dashboard
  ```

---

## Configuración en Didit Dashboard (una sola vez)

Estos pasos se realizan en [didit.me](https://didit.me) antes de probar:

1. Crear una cuenta y una nueva aplicación
2. Ir a **Flujos de trabajo → Custom KYC**
3. Configurar el flujo con al menos el paso **ID Verification** (verificación de documento)
4. Copiar el **Workflow ID** al `.env` como `DIDIT_WORKFLOW_ID`
5. Ir a **API Keys** y copiar la clave al `.env` como `DIDIT_API_KEY`
6. En la sección **Webhook**:
   - Evento suscrito: `status.updated` (es el que notifica cambios de estado de sesión)
   - URL del webhook: se configura con la URL de ngrok antes de cada prueba (ver sección siguiente)
7. Guardar los cambios

> **Por qué `status.updated`:** Es el evento que Didit dispara cuando una sesión cambia de estado
> (`Not Started` → `In Progress` → `Approved` o `Declined`). Es el único evento relevante para
> actualizar el `kycStatus` del usuario en Liken.

---

## Por que uso de ngrok en local

El webhook de Didit es una llamada HTTP que **Didit hace desde sus servidores hacia tu backend**.
En producción esto funciona porque el backend tiene una URL pública. En local, `localhost` no es
accesible desde internet, entonces Didit no puede llamar al webhook.

**ngrok** resuelve esto creando un túnel público que apunta a tu puerto local. Didit llama a la
URL de ngrok, y ngrok reenvía el tráfico a tu máquina.

El webhook está expuesto en el `user-service` (puerto externo `9090`) en la ruta
`/internal/kyc/webhook/didit`, que no requiere JWT.

### En producción (GKE / liken.lat)

ngrok no se usa en producción. En su lugar, el equipo debe decidir cómo exponer la ruta
`/internal/kyc/webhook/didit` públicamente. Las opciones son:

- Agregar una ruta sin autenticación en el `api-gateway` solo para ese path
- Crear un Ingress dedicado en GKE apuntando directamente al `user-service`
- Usar un LoadBalancer separado para el webhook

Esta decisión de arquitectura queda pendiente para cuando se haga el deploy a producción.

---

## Pasos para probar el flujo completo

### 1. Levantar ngrok

En una terminal separada:

```bash
ngrok http 9090
```

Copiar la URL que aparece, por ejemplo:
```
https://abc123.ngrok-free.app -> http://localhost:9090                   
```

### 2. Configurar el webhook en Didit Dashboard

En [didit.me](https://didit.me) → tu aplicación → **Flujos de trabajo → Custom KYC → Webhook**:

```                                                                                                    
                                                                                                                           
URL: https://abc123.ngrok-free.app/internal/kyc/webhook/didit
```

Guardar. Esta URL cambia cada vez que se reinicia ngrok, por lo que hay que actualizarla
en el Dashboard antes de cada sesión de prueba.

### 3. Preparar el usuario de prueba

Si el usuario ya tiene KYC aprobado de una prueba anterior, resetearlo:

```bash
docker exec -it liken_postgres psql -U liken_user -d user_db \
  -c "UPDATE users SET kyc_status='NOT_STARTED', didit_session_id=NULL, kyc_verified_at=NULL WHERE email='admin@admin.com';"
```

Verificar que quedó reseteado:

```bash
docker exec -it liken_postgres psql -U liken_user -d user_db \
  -c "SELECT email, kyc_status, didit_session_id, kyc_verified_at FROM users WHERE email='admin@admin.com';"
```

Resultado esperado:
```
kyc_status = NOT_STARTED | didit_session_id = (vacío) | kyc_verified_at = (vacío)
```

### 4. Abrir monitor de logs

En otra terminal, antes de iniciar el flujo:

```bash
docker compose logs -f user-service | grep -i "didit\|webhook\|kyc"
```

Dejar esta terminal visible durante toda la prueba para observar en tiempo real.

### 5. Login

```bash
curl -X POST http://localhost:8090/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "admin@admin.com", "password": "admin123"}'
```

Copiar el valor de `accessToken` de la respuesta. El token expira en 15 minutos.

### 6. Iniciar sesión KYC

```bash
curl -X POST http://localhost:8090/api/users/me/kyc/didit/initiate \
  -H "Authorization: Bearer <TOKEN>"
```

Reemplazando <TOKEN> por el valor del `accessToken` de la respuesta.

Respuesta esperada:
```json
{
  "message": "Sesión KYC iniciada",
  "data": { "verificationUrl": "https://verify.didit.me/session/..." },
  "status": 200
}
```

En los logs debería aparecer:
```
DiditService - Creando sesión Didit para userId: 1
DiditService - Sesión Didit creada: <session_id> para userId: 1
KycService   - KYC Didit: sesión iniciada para userId=1 sessionId=<session_id>
```

### 7. Completar verificación en el widget de Didit

Abrir la `verificationUrl` en el navegador. El widget de Didit pedirá:

- Foto del frente del DNI
- Foto del dorso del DNI
- Selfie (liveness check)

- o lo que sea haya customizado de KYC, ejemplo solo Identificación de ID. 

Completar todos los pasos. Se puede usar el DNI real de cualquier persona — Didit verifica
que el documento sea auténtico y que la persona frente a la cámara coincida con la foto,
pero **no cruza los datos del DNI con el email o nombre del usuario en Liken**.


### 8. Observar los webhooks en los logs

A medida que avanza la verificación, Didit envía webhooks intermedios. En los logs se verán:

```
KycWebhookController - Webhook Didit recibido: {..., "status":"Not Started", ...}
KycService - KYC Didit webhook: userId=1 ... status=Not Started

KycWebhookController - Webhook Didit recibido: {..., "status":"In Progress", ...}
KycService - KYC Didit webhook: userId=1 ... status=In Progress
KycService - KYC Didit: usuario admin@admin.com en revision adicional

KycWebhookController - Webhook Didit recibido: {..., "status":"Approved", ...}
KycService - KYC Didit webhook: userId=1 ... status=Approved
KycService - KYC Didit: usuario admin@admin.com APROBADO automaticamente
```

### 9. Verificar resultado en la base de datos

```bash
docker exec -it liken_postgres psql -U liken_user -d user_db \
  -c "SELECT email, kyc_status, didit_session_id, kyc_verified_at FROM users WHERE email='admin@admin.com';"
```

Resultado esperado tras aprobación:
```
kyc_status = APPROVED
didit_session_id = <uuid de la sesión>
kyc_verified_at = <timestamp de la aprobación>
```

---

## Estados del flujo

| Estado Didit   | Estado en Liken | Descripción                              |
|----------------|-----------------|------------------------------------------|
| `Not Started`  | (sin cambio)    | Sesión creada, usuario no ingresó aún    |
| `In Progress`  | `PENDING`       | Usuario completando la verificación      |
| `Approved`     | `APPROVED`      | Verificación exitosa, usuario aprobado   |
| `Declined`     | `REJECTED`      | Verificación fallida                     |

---
## Test admin
Para probar el flujo KYC se utiliza el usuario admin (`admin@admin.com`) que es creado
automáticamente por el `DataSeeder` al arrancar el stack, sin pasar por el flujo de registro.
El comportamiento del KYC es idéntico para cualquier usuario.
