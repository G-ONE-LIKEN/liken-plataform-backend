// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Script, console2} from "forge-std/Script.sol";

import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import {LinkenToken} from "../src/LinkenToken.sol";
import {ProjectRegistry} from "../src/ProjectRegistry.sol";
import {OfferingContract} from "../src/OfferingContract.sol";

/**
 * @title DeployProjectOffering
 * @notice Publica on-chain un proyecto ya existente en el backend:
 *   1. registra el proyecto en el ProjectRegistry global
 *   2. deploya un OfferingContract dedicado
 *   3. otorga OFFERING_ROLE al offering
 *   4. deposita LKN en escrow y abre la ronda
 *
 * Uso recomendado con cuenta nombrada (sin exponer PRIVATE_KEY en .env):
 *   cast wallet import dev --interactive
 *   forge script script/DeployProjectOffering.s.sol:DeployProjectOffering \
 *     --rpc-url $SEPOLIA_RPC_URL \
 *     --account dev --broadcast --verify
 *
 * Variables requeridas en .env:
 *   LKN_ADDRESS
 *   REGISTRY_ADDRESS
 *   USDC_ADDRESS
 *   PLATFORM_ADMIN
 *   EMISOR               ← debe coincidir con la wallet que firma (broadcaster)
 *   TREASURY
 *   PROJECT_OWNER
 *   PROJECT_NAME
 *   PROJECT_DESCRIPTION
 *   EARLY_BIRD_PRICE     ← USDC por LKN en early bird, 6 decimales (ej: 8000000 = $8)
 *   STANDARD_PRICE       ← USDC por LKN post-apertura, 6 decimales (ej: 10000000 = $10)
 *   TOKEN_PRICE          ← precio activo para esta ronda (default: EARLY_BIRD_PRICE)
 *   SOFT_CAP             ← mínimo USDC a recaudar, 6 decimales
 *   HARD_CAP             ← máximo USDC a recaudar, 6 decimales
 *   DEADLINE_TIMESTAMP   ← Unix timestamp límite (ej: $(date -d '+30 days' +%s))
 *   ESCROW_LKN_AMOUNT_WEI ← LKN a depositar en escrow, 18 decimales
 */
contract DeployProjectOffering is Script {
    function run() external {
        address lknAddress = vm.envAddress("LKN_ADDRESS");
        address registryAddress = vm.envAddress("REGISTRY_ADDRESS");
        address usdcAddress = vm.envAddress("USDC_ADDRESS");
        address platformAdmin = vm.envAddress("PLATFORM_ADMIN");
        address emisor = vm.envAddress("EMISOR");
        address treasury = vm.envAddress("TREASURY");
        address projectOwner = vm.envAddress("PROJECT_OWNER");

        string memory projectName = vm.envString("PROJECT_NAME");
        string memory projectDescription = vm.envString("PROJECT_DESCRIPTION");

        uint256 earlyBirdPrice = vm.envUint("EARLY_BIRD_PRICE");
        uint256 standardPrice = vm.envUint("STANDARD_PRICE");
        uint256 tokenPrice = vm.envOr("TOKEN_PRICE", earlyBirdPrice);
        uint256 softCap = vm.envUint("SOFT_CAP");
        uint256 hardCap = vm.envUint("HARD_CAP");
        uint256 deadline = vm.envUint("DEADLINE_TIMESTAMP");
        uint256 escrowLknAmount = vm.envUint("ESCROW_LKN_AMOUNT_WEI");

        require(lknAddress != address(0), "DP: zero LKN");
        require(registryAddress != address(0), "DP: zero registry");
        require(usdcAddress != address(0), "DP: zero USDC");
        require(platformAdmin != address(0), "DP: zero admin");
        require(emisor != address(0), "DP: zero emisor");
        require(treasury != address(0), "DP: zero treasury");
        require(projectOwner != address(0), "DP: zero project owner");
        require(earlyBirdPrice > 0, "DP: zero early bird price");
        require(standardPrice > earlyBirdPrice, "DP: standard price <= early bird price");
        require(softCap > 0, "DP: zero soft cap");
        require(hardCap > softCap, "DP: hard cap <= soft cap");
        require(deadline > block.timestamp, "DP: deadline in past");
        require(escrowLknAmount > 0, "DP: zero escrow");

        LinkenToken lkn = LinkenToken(lknAddress);
        ProjectRegistry registry = ProjectRegistry(registryAddress);

        console2.log("================ PROJECT PUBLICATION ================");
        console2.log("registry             :", registryAddress);
        console2.log("lkn                  :", lknAddress);
        console2.log("usdc                 :", usdcAddress);
        console2.log("platformAdmin        :", platformAdmin);
        console2.log("emisor               :", emisor);
        console2.log("treasury             :", treasury);
        console2.log("projectOwner         :", projectOwner);
        console2.log("earlyBirdPrice       :", earlyBirdPrice);
        console2.log("standardPrice        :", standardPrice);
        console2.log("tokenPrice           :", tokenPrice);
        console2.log("softCap              :", softCap);
        console2.log("hardCap              :", hardCap);
        console2.log("escrowLknAmountWei   :", escrowLknAmount);
        console2.log("deadline             :", deadline);
        console2.log("----------------------------------------------------");

        vm.startBroadcast();

        uint256 registryProjectId =
            registry.registerProject(projectName, projectDescription, projectOwner, earlyBirdPrice, standardPrice);

        OfferingContract offering = new OfferingContract(
            lknAddress,
            usdcAddress,
            treasury,
            tokenPrice,
            softCap,
            hardCap,
            deadline,
            platformAdmin,
            emisor,
            registryAddress,
            registryProjectId
        );

        registry.grantRole(registry.OFFERING_ROLE(), address(offering));
        lkn.approve(address(offering), escrowLknAmount);
        offering.deposit(escrowLknAmount);
        offering.openRound();

        vm.stopBroadcast();

        console2.log("REGISTRY_PROJECT_ID  =", registryProjectId);
        console2.log("OFFERING_ADDRESS     =", address(offering));
        console2.log("ROUND_STATE          = OPEN");
        console2.log("====================================================");
    }
}
