# 0008 - Patrón Pull Payment para distribución de dividendos

## Contexto
El sistema necesita distribuir ingresos en USDC entre todos los holders de un
ProjectToken de forma proporcional a su participación. Existen dos enfoques:

**Push**: la plataforma itera sobre todos los holders y les transfiere USDC
directamente en una sola transacción.

Problemas del push:
- Si hay muchos holders, la transacción supera el gas limit del bloque y falla.
- Un holder malicioso puede deployar un contrato que revierta en `receive()`,
  bloqueando el pago de todos los holders siguientes (griefing attack).
- Enviar ETH o tokens en loops es un antipatrón de seguridad documentado.

**Pull**: la plataforma deposita el total en el contrato. Cada holder retira
lo que le corresponde cuando quiere, en una transacción separada.

## Decisión
Usar el patrón pull con el algoritmo **"dividends per share"**: