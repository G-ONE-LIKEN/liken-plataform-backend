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
 */
contract DeployCore is Script {
    uint256 private constant DEFAULT_TGE_SUPPLY = 1_000_000 * 1e18;

    function run() external {
        uint256 privateKey = vm.envUint("PRIVATE_KEY");
        address platformAdmin = vm.envAddress("PLATFORM_ADMIN");
        address emisor = vm.envOr("EMISOR_ADDRESS", platformAdmin);
        address usdc = vm.envAddress("USDC_ADDRESS");
        uint256 tgeSupply = vm.envOr("TGE_SUPPLY", DEFAULT_TGE_SUPPLY);

        require(privateKey != 0, "DC: zero private key");
        require(platformAdmin != address(0), "DC: zero admin");
        require(emisor != address(0), "DC: zero emisor");
        require(usdc != address(0), "DC: zero usdc");
        require(tgeSupply > 0, "DC: zero supply");

        address publicationSigner = vm.addr(privateKey);

        console2.log("================ DEPLOY CORE LINKEN ================");
        console2.log("publicationSigner :", publicationSigner);
        console2.log("platformAdmin     :", platformAdmin);
        console2.log("emisor            :", emisor);
        console2.log("usdc              :", usdc);
        console2.log("tgeSupply         :", tgeSupply);
        console2.log("----------------------------------------------------");

        vm.startBroadcast(privateKey);

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
