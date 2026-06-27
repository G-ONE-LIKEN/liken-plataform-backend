import { createPublicClient, http, parseAbi, formatUnits } from "viem";
import { sepolia } from "viem/chains";
import { env } from "./env.js";

// Lee balances y allowances de LKN/USDC sobre el marketplace en Sepolia.
//
// Uso:
//   npm run check-balances -- <sellerAddress> <buyerAddress>
//
// Sin args devuelve los balances de la wallet admin contra el marketplace.

const ERC20_ABI = parseAbi([
  "function balanceOf(address) view returns (uint256)",
  "function allowance(address, address) view returns (uint256)",
  "function decimals() view returns (uint8)",
]);

function parseArgs(): { seller: `0x${string}`; buyer: `0x${string}` } {
  const [, , sellerArg, buyerArg] = process.argv;
  if (!sellerArg || !buyerArg) {
    console.error(
      "Uso: npm run check-balances -- <sellerAddress> <buyerAddress>",
    );
    process.exit(1);
  }
  if (!/^0x[a-fA-F0-9]{40}$/.test(sellerArg) || !/^0x[a-fA-F0-9]{40}$/.test(buyerArg)) {
    console.error("Las direcciones deben ser 0x + 40 hex chars.");
    process.exit(1);
  }
  return {
    seller: sellerArg as `0x${string}`,
    buyer: buyerArg as `0x${string}`,
  };
}

async function main() {
  const { seller, buyer } = parseArgs();
  const client = createPublicClient({ chain: sepolia, transport: http(env.rpcUrl) });

  const [
    sellerLknBalance,
    sellerLknAllowance,
    buyerUsdcBalance,
    buyerUsdcAllowance,
    lknDecimals,
    usdcDecimals,
  ] = await Promise.all([
    client.readContract({ address: env.lkn, abi: ERC20_ABI, functionName: "balanceOf", args: [seller] }),
    client.readContract({ address: env.lkn, abi: ERC20_ABI, functionName: "allowance", args: [seller, env.marketplace] }),
    client.readContract({ address: env.usdc, abi: ERC20_ABI, functionName: "balanceOf", args: [buyer] }),
    client.readContract({ address: env.usdc, abi: ERC20_ABI, functionName: "allowance", args: [buyer, env.marketplace] }),
    client.readContract({ address: env.lkn, abi: ERC20_ABI, functionName: "decimals" }),
    client.readContract({ address: env.usdc, abi: ERC20_ABI, functionName: "decimals" }),
  ]);

  console.log("─── Seller (vendedor) ───────────────────────────────");
  console.log(`  Address:        ${seller}`);
  console.log(`  LKN balance:    ${formatUnits(sellerLknBalance, lknDecimals)} LKN`);
  console.log(`  LKN allowance:  ${formatUnits(sellerLknAllowance, lknDecimals)} LKN  → marketplace`);
  console.log("─── Buyer (comprador) ───────────────────────────────");
  console.log(`  Address:        ${buyer}`);
  console.log(`  USDC balance:   ${formatUnits(buyerUsdcBalance, usdcDecimals)} USDC`);
  console.log(`  USDC allowance: ${formatUnits(buyerUsdcAllowance, usdcDecimals)} USDC → marketplace`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
