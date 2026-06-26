import { config } from "dotenv";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

// Carga el .env del root del backend (un nivel arriba de contracts/),
// reusa las mismas variables que ya usa el resto de la plataforma.
const __dirname = fileURLToPath(new URL(".", import.meta.url));
config({ path: resolve(__dirname, "../../.env") });

function required(name: string): string {
  const value = process.env[name];
  if (!value || value.trim() === "") {
    throw new Error(
      `Falta la variable de entorno "${name}". Configurala en backend/.env (ver .env.example).`,
    );
  }
  return value;
}

export const env = {
  rpcUrl: required("WEB3_RPC_URL"),
  adminPrivateKey: required("PUBLICATION_SIGNER_PRIVATE_KEY") as `0x${string}`,
  marketplace: required("MARKETPLACE_ADDRESS") as `0x${string}`,
  lkn: required("LKN_ADDRESS") as `0x${string}`,
  usdc: required("USDC_ADDRESS") as `0x${string}`,
};
