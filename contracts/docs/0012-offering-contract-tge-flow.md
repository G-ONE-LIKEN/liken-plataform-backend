# 0012 - OfferingContract: flujo de venta primaria de tokens (TGE)

## Contexto

Después del TGE, el emisor (SPE dueño del parque) tiene el supply completo
de LKN en su wallet. Necesita un mecanismo para vender esos tokens a inversores
a precio fijo, con garantías mínimas para ambas partes:
- El inversor no debería perder su USDC si el proyecto no llega al mínimo de
  financiamiento necesario para arrancar.
- El emisor no debería poder retirar fondos si no se alcanzó el mínimo.

## Decisión

Implementar un `OfferingContract` con tres parámetros de ronda y patrón
escrow + pull payment para refunds.

### Parámetros de ronda

| Parámetro | Descripción | Ejemplo (parque 5MW) |
|---|---|---|
| `tokenPrice` | USDC por LKN (6 decimales) | 10_000_000 = USD 10/LKN |
| `softCap` | Mínimo USDC a recaudar para que el proyecto arranque | 500_000 * 1e6 = USD 500k |
| `hardCap` | Máximo USDC a recaudar — cierra la ronda automáticamente | 2_000_000 * 1e6 = USD 2M |
| `deadline` | Timestamp límite para alcanzar el soft cap | block.timestamp + 30 días |

### Flujo completo

#### PRE-RONDA

1. Emisor despliega LinkenToken → recibe 200.000 LKN (TGE)
2. Emisor despliega OfferingContract con precio, soft cap, hard cap, deadline
3. Emisor aprueba LKN al OfferingContract (approve)
4. Emisor llama deposit(amount) → LKN entran al contrato como escrow

#### RONDA ABIERTA

1. Inversor aprueba USDC al OfferingContract
2. Inversor llama buy(usdcAmount)
3. Contrato transfiere USDC del inversor al treasury
4. Contrato transfiere LKN del escrow al inversor
5. Si totalRaised >= hardCap → ronda cierra automáticamente

#### CIERRE EXITOSO (totalRaised >= softCap)

1. Emisor llama finalize() → marca la ronda como exitosa
2. USDC disponibles para el treasury (ya fueron transferidos en cada compra)

#### CIERRE FALLIDO (deadline pasó y totalRaised < softCap)
1. Cualquier inversor puede llamar refund()
2. Contrato devuelve los LKN al emisor (o los quema)
3. Cada inversor recupera su USDC proporcional

### Por qué el emisor deposita LKN en el contrato (escrow)

Sin esto, el emisor podría vender LKN a inversores y luego transferirlos
a otra wallet antes de que todos compren, dejando al contrato sin tokens
para entregar. El depósito previo garantiza que los LKN están bloqueados
y disponibles para los compradores.

### Por qué el USDC va directo al treasury (no queda en escrow)

Para el soft cap, el contrato registra cuánto pagó cada inversor en `contributions[address]`. Si la ronda falla, el inversor llama `refund()` y el treasury devuelve su USDC. Esto requiere que el treasury sea un contrato o una wallet controlada que pueda devolver fondos — en el PoC el treasury es la wallet del emisor.

Alternativa descartada: guardar el USDC en el contrato hasta el cierre. Se descartó porque agrega complejidad y surface de ataque (el contrato tendría que manejar fondos de USDC además de LKN).

### Patrón pull payment para refunds

Si la ronda falla, el contrato NO itera sobre todos los inversores para devolver el USDC (eso costaría una fortuna en gas con muchos inversores y podría exceder el gas limit). En cambio, cada inversor llama `refund()` individualmente y recupera su parte — el mismo patrón pull que usa DividendDistributor para los dividendos.

## Consecuencias

- El emisor debe tener LKN disponibles antes de abrir la ronda.
- El precio es fijo durante toda la ronda — no hay variaciones.
- Si el soft cap no se alcanza antes del deadline, los inversores pueden
  recuperar su USDC llamando refund(). El treasury debe tener fondos
  suficientes para honrar los refunds.
- El hard cap protege al emisor de sobre-suscripción — no puede vender
  más tokens de los que depositó ni recaudar más de lo planificado.
- En el PoC, el oráculo de kWh no está conectado al OfferingContract — la ronda es manual. En producción, el cierre exitoso podría disparar automáticamente la configuración del DividendDistributor.