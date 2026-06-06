# 0004 - Stack Frontend

## Contexto
El frontend necesita conectarse a wallets, leer estado del contrato y enviar transacciones
en Sepolia. Los profesores especificaron el stack tecnológico a utilizar.

## Decisión
Usar Next.js 14 + RainbowKit v2 + wagmi v2 + viem.

## Consecuencias
- RainbowKit provee UI de conexión multi-wallet lista para usar, sin necesidad de construirla desde cero.
- wagmi v2 expone hooks de React (`useReadContract`, `useWriteContract`) que simplifican la interacción con el contrato.
- viem reemplaza a ethers.js como librería de bajo nivel: tipado más estricto y menor tamaño de bundle.
- El stack requiere un WalletConnect Project ID gratuito para funcionar en producción.
- Next.js App Router con SSR requiere marcar los componentes que usan hooks de wagmi con `"use client"`.