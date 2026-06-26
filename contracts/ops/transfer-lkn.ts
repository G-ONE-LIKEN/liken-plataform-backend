import { createWalletClient, createPublicClient, http, parseAbi, parseEther } from "viem";
import { privateKeyToAccount } from "viem/accounts";
import { sepolia } from "viem/chains";
import { env } from "./env.js";

// Fondea a un tester con LKN desde la wallet admin.
//
// Uso:
//   npm run transfer-lkn -- <destinationAddress> <amount>
// Ejemplo:
//   npm run transfer-lkn -- 0x07307Ee2D30BCC52D000BE5fB44e5d6033bf7350 100

const ERC20_ABI = parseAbi([
  "function transfer(address to, uint256 amount) returns (bool)",
  "function balanceOf(address) view returns (uint256)",
]);

function parseArgs(): { to: `0x${string}`; amount: string } {
  const [, , toArg, amountArg] = process.argv;
  if (!toArg || !amountArg) {
    console.error("Uso: npm run transfer-lkn -- <destinationAddress> <amount>");
    process.exit(1);
  }
  if (!/^0x[a-fA-F0-9]{40}$/.test(toArg)) {
    console.error("La direccion destino debe ser 0x + 40 hex chars.");
    process.exit(1);
  }
  if (!/^\d+(\.\d+)?$/.test(amountArg) || Number(amountArg) <= 0) {
    console.error("El monto debe ser un numero positivo (ej. 100 o 12.5).");
    process.exit(1);
  }
  return { to: toArg as `0x${string}`, amount: amountArg };
}

async function main() {
  const { to, amount } = parseArgs();
  const account = privateKeyToAccount(env.adminPrivateKey);

  const publicClient = createPublicClient({ chain: sepolia, transport: http(env.rpcUrl) });
  const walletClient = createWalletClient({ account, chain: sepolia, transport: http(env.rpcUrl) });

  const adminBalance = await publicClient.readContract({
    address: env.lkn,
    abi: ERC20_ABI,
    functionName: "balanceOf",
    args: [account.address],
  });
  const amountWei = parseEther(amount);

  if (adminBalance < amountWei) {
    console.error(`Saldo insuficiente. Admin tiene ${adminBalance}, necesita ${amountWei}.`);
    process.exit(1);
  }

  console.log(`Enviando ${amount} LKN desde ${account.address} → ${to}...`);

  const hash = await walletClient.writeContract({
    address: env.lkn,
    abi: ERC20_ABI,
    functionName: "transfer",
    args: [to, amountWei],
  });

  console.log(`Tx enviada: ${hash}`);
  console.log("Esperando confirmacion...");

  const receipt = await publicClient.waitForTransactionReceipt({ hash });

  if (receipt.status === "success") {
    console.log(`Transferencia exitosa en bloque ${receipt.blockNumber}.`);
  } else {
    console.error("La transaccion falló on-chain.");
    process.exit(1);
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
