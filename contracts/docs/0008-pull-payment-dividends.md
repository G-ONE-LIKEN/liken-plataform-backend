# 0008 - Patron Pull Payment para distribucion de dividendos

## Contexto
El sistema necesita distribuir ingresos en USDC entre todos los holders de un
ProjectToken de forma proporcional a su participacion. Existen dos enfoques:

**Push**: la plataforma itera sobre todos los holders y les transfiere USDC
directamente en una sola transaccion.

Problemas del push:
- Si hay muchos holders, la transaccion supera el gas limit del bloque y falla.
- Un holder malicioso puede deployar un contrato que revierta en `receive()`,
  bloqueando el pago de todos los holders siguientes (griefing attack).
- Enviar ETH o tokens en loops es un antipatron de seguridad documentado.

**Pull**: la plataforma deposita el total en el contrato. Cada holder retira
lo que le corresponde cuando quiere, en una transaccion separada.

## Decision
Usar el patron pull con el algoritmo **"dividends per share"**: