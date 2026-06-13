// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Script, console2} from "forge-std/Script.sol";
import {ERC20} from "@openzeppelin/contracts/token/ERC20/ERC20.sol";

import {LinkenToken} from "../src/LinkenToken.sol";
import {ProjectRegistry} from "../src/ProjectRegistry.sol";
import {OfferingContract} from "../src/OfferingContract.sol";
import {DividendDistributor} from "../src/DividendDistributor.sol";

/**
 * @title DeployAll
 * @notice Script de deploy completo de la plataforma Linken.
 *
 * Orden (sigue el README + ADR-0014):
 *   1. LinkenToken (TGE: emite supply al emisor)
 *   2. ProjectRegistry
 *   3. registry.registerProject(...) → obtiene projectId
 *   4. OfferingContract(..., registry, projectId)
 *   5. registry.grantRole(OFFERING_ROLE, offering)
 *   6. emisor: lkn.approve(offering, supply) + offering.deposit(supply)
 *   7. emisor: offering.openRound()
 *   8. DividendDistributor(linken, usdc, platformAdmin)
 *   9. lkn.setDistributor(distributor)
 *
 * Modo de uso:
 *
 *   Local (anvil) — un solo broadcaster que cumple los 3 roles:
 *     export PLATFORM_ADMIN=0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266   # anvil[0]
 *     export EMISOR=$PLATFORM_ADMIN
 *     export TREASURY=$PLATFORM_ADMIN
 *     export USDC=0x0000000000000000000000000000000000000000             # cero → deploy MockUSDC
 *     forge script script/DeployAll.s.sol:DeployAll \
 *       --rpc-url http://127.0.0.1:8545 \
 *       --private-key 0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80 \
 *       --broadcast
 *
 *   Sepolia (un proyecto piloto):
 *     export PLATFORM_ADMIN=0x...        # admin de la plataforma
 *     export EMISOR=0x...                # SPE / dueño del parque
 *     export TREASURY=0x...              # cuenta que recibe USDC
 *     export USDC=0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238  # USDC Sepolia (Circle)
 *     forge script script/DeployAll.s.sol:DeployAll \
 *       --rpc-url $SEPOLIA_RPC_URL \
 *       --account dev --broadcast --verify
 *
 * Notas:
 * - Si los roles `platformAdmin`/`emisor` estan en diferentes claves, este script asume que
 *   `tx.origin == platformAdmin == emisor` (un solo broadcaster). Para produccion real,
 *   separar en varios scripts: deploy + setup + open.
 * - El script imprime todas las addresses al final para copiar al `.env` / al backend.
 */
contract DeployAll is Script {
    // ── Parametros del proyecto piloto (override por env) ──
    uint256 constant DEFAULT_TGE_SUPPLY = 200_000 * 1e18;       // parque ~5MW
    uint256 constant DEFAULT_EARLY_BIRD_PRICE = 8_000_000;      // $8 / LKN (6 dec)
    uint256 constant DEFAULT_STANDARD_PRICE = 10_000_000;       // $10 / LKN (6 dec)
    uint256 constant DEFAULT_TOKEN_PRICE = DEFAULT_EARLY_BIRD_PRICE;
    uint256 constant DEFAULT_SOFT_CAP = 500_000 * 1e6;          // $500k
    uint256 constant DEFAULT_HARD_CAP = 1_600_000 * 1e6;        // $1.6M (200k LKN * $8)
    uint256 constant DEFAULT_DURATION = 30 days;

    function run() external {
        // ── Cargar params del env ──
        address platformAdmin = vm.envAddress("PLATFORM_ADMIN");
        address emisor = vm.envOr("EMISOR", platformAdmin);
        address treasury = vm.envOr("TREASURY", platformAdmin);
        address usdcAddr = vm.envOr("USDC", address(0));

        uint256 tgeSupply = vm.envOr("TGE_SUPPLY", DEFAULT_TGE_SUPPLY);
        uint256 earlyBirdPrice = vm.envOr("EARLY_BIRD_PRICE", DEFAULT_EARLY_BIRD_PRICE);
        uint256 standardPrice = vm.envOr("STANDARD_PRICE", DEFAULT_STANDARD_PRICE);
        uint256 tokenPrice = vm.envOr("TOKEN_PRICE", earlyBirdPrice);
        uint256 softCap = vm.envOr("SOFT_CAP", DEFAULT_SOFT_CAP);
        uint256 hardCap = vm.envOr("HARD_CAP", DEFAULT_HARD_CAP);
        uint256 duration = vm.envOr("DURATION_SECONDS", DEFAULT_DURATION);

        string memory projectName = vm.envOr("PROJECT_NAME", string("Parque Solar Piloto"));
        string memory projectDesc = vm.envOr(
            "PROJECT_DESCRIPTION", string("Proyecto piloto de tokenizacion de energia renovable.")
        );

        console2.log("================ DEPLOY LINKEN ================");
        console2.log("platformAdmin :", platformAdmin);
        console2.log("emisor        :", emisor);
        console2.log("treasury      :", treasury);
        console2.log("usdc (in)     :", usdcAddr);
        console2.log("tgeSupply     :", tgeSupply);
        console2.log("earlyBirdPrice:", earlyBirdPrice);
        console2.log("standardPrice :", standardPrice);
        console2.log("tokenPrice    :", tokenPrice);
        console2.log("softCap       :", softCap);
        console2.log("hardCap       :", hardCap);
        console2.log("duration      :", duration);
        console2.log("-----------------------------------------------");

        vm.startBroadcast();

        // ── 0. USDC (mock si no se paso address) ──
        if (usdcAddr == address(0)) {
            MockUSDC mock = new MockUSDC();
            // Mintear un poco al emisor/treasury para tests locales
            mock.mint(treasury, 10_000_000 * 1e6);
            usdcAddr = address(mock);
            console2.log("MockUSDC deployed (LOCAL):", usdcAddr);
        }

        // ── 1. LinkenToken (TGE → emisor) ──
        LinkenToken lkn = new LinkenToken(platformAdmin, emisor, tgeSupply + 50_000 * 1e18);
        console2.log("LinkenToken           :", address(lkn));

        // ── 2. ProjectRegistry ──
        ProjectRegistry registry = new ProjectRegistry(platformAdmin);
        console2.log("ProjectRegistry       :", address(registry));

        // ── 3. registerProject ──
        uint256 projectId = registry.registerProject(
            projectName, projectDesc, emisor, earlyBirdPrice, standardPrice
        );
        console2.log("projectId             :", projectId);

        // ── 4. OfferingContract ──
        uint256 deadline = block.timestamp + duration;
        OfferingContract offering = new OfferingContract(
            address(lkn),
            usdcAddr,
            treasury,
            tokenPrice,
            softCap,
            hardCap,
            deadline,
            platformAdmin,
            emisor,
            address(registry),
            projectId
        );
        console2.log("OfferingContract      :", address(offering));
        console2.log("deadline              :", deadline);

        // ── 5. grant OFFERING_ROLE ──
        registry.grantRole(registry.OFFERING_ROLE(), address(offering));
        console2.log("OFFERING_ROLE granted to offering");

        // ── 6. emisor aprueba y deposita todo el supply en escrow ──
        lkn.approve(address(offering), tgeSupply);
        offering.deposit(tgeSupply);
        console2.log("LKN deposited in escrow:", tgeSupply);

        // ── 7. abrir la ronda ──
        offering.openRound();
        console2.log("Round OPEN");

        // ── 8. DividendDistributor ──
        DividendDistributor distributor = new DividendDistributor(address(lkn), usdcAddr, platformAdmin);
        console2.log("DividendDistributor   :", address(distributor));

        // ── 9. conectar distributor al token (hook on transfer) ──
        lkn.setDistributor(address(distributor));
        console2.log("Distributor wired into LinkenToken");

        vm.stopBroadcast();

        console2.log("================= ADDRESSES =================");
        console2.log("LINKEN_ADDRESS     =", address(lkn));
        console2.log("REGISTRY_ADDRESS   =", address(registry));
        console2.log("OFFERING_ADDRESS   =", address(offering));
        console2.log("DISTRIBUTOR_ADDRESS=", address(distributor));
        console2.log("USDC_ADDRESS       =", usdcAddr);
        console2.log("PROJECT_ID         =", projectId);
        console2.log("=============================================");
    }
}

/**
 * @notice Mock USDC para deploys locales (Anvil). En Sepolia/mainnet usar el USDC real.
 *         Decimales 6 — igual que el USDC oficial de Circle.
 */
contract MockUSDC is ERC20 {
    constructor() ERC20("USD Coin", "USDC") {}

    function decimals() public pure override returns (uint8) {
        return 6;
    }

    function mint(address to, uint256 amount) external {
        _mint(to, amount);
    }
}
