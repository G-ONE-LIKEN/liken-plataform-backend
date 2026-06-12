# 0004 - Stack Frontend

## Contexto
El frontend necesita conectarse a wallets, leer estado del contrato y enviar transacciones
en Sepolia. Los profesores especificaron el stack tecnologico a utilizar.

## Decision
Usar Next.js 14 + RainbowKit v2 + wagmi v2 + viem.

## Consecuencias
- RainbowKit provee UI de conexion multi-wallet lista para usar, sin necesidad de construirla desde cero.
- wagmi v2 expone hooks de React (`useReadContract`, `useWriteContract`) que simplifican la interaccion con el contrato.
- viem reemplaza a ethers.js como libreria de bajo nivel: tipado mas estricto y menor tamaño de bundle.
- El stack requiere un WalletConnect Project ID gratuito para funcionar en produccion.
- Next.js App Router con SSR requiere marcar los componentes que usan hooks de wagmi con `"use client"`.