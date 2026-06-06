// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Script, console2} from "forge-std/Script.sol";

import {LinkenToken} from "../src/LinkenToken.sol";
import {ProjectRegistry} from "../src/ProjectRegistry.sol";
import {DividendDistributor} from "../src/DividendDistributor.sol";

/**
 * @title DeployCore
 * @notice Deploya solo los contratos globales de la plataforma.
 *         No crea OfferingContract ni deposita LKN en escrow.
 *
 * Uso recomendado con cuenta nombrada (sin exponer PRIVATE_KEY en .env):
 *   cast wallet import dev --interactive
 *   forge script script/DeployCore.s.sol:DeployCore \
 *     --rpc-url $SEPOLIA_RPC_URL \
 *     --account dev --broadcast --verify
 */
contract DeployCore is Script {
    uint256 private constant DEFAULT_TGE_SUPPLY = 1_000_000 * 1e18;

    function run() external {
        address platformAdmin = vm.envAddress("PLATFORM_ADMIN");
        address emisor = vm.envOr("EMISOR_ADDRESS", platformAdmin);
        address usdc = vm.envAddress("USDC_ADDRESS");
        uint256 tgeSupply = vm.envOr("TGE_SUPPLY", DEFAULT_TGE_SUPPLY);

        require(platformAdmin != address(0), "DC: zero admin");
        require(emisor != address(0), "DC: zero emisor");
        require(usdc != address(0), "DC: zero usdc");
        require(tgeSupply > 0, "DC: zero supply");

        console2.log("================ DEPLOY CORE LINKEN ================");
        console2.log("platformAdmin     :", platformAdmin);
        console2.log("emisor            :", emisor);
        console2.log("usdc              :", usdc);
        console2.log("tgeSupply         :", tgeSupply);
        console2.log("----------------------------------------------------");

        vm.startBroadcast();

        LinkenToken lkn = new LinkenToken(platformAdmin, emisor, tgeSupply);
        ProjectRegistry registry = new ProjectRegistry(platformAdmin);
        DividendDistributor distributor = new DividendDistributor(address(lkn), usdc, platformAdmin);
        lkn.setDistributor(address(distributor));

        vm.stopBroadcast();

        console2.log("================= CORE ADDRESSES =================");
        console2.log("LKN_ADDRESS        =", address(lkn));
        console2.log("REGISTRY_ADDRESS   =", address(registry));
        console2.log("DISTRIBUTOR_ADDRESS=", address(distributor));
        console2.log("USDC_ADDRESS       =", usdc);
        console2.log("==================================================");
    }
}
