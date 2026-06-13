// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {Test} from "forge-std/Test.sol";
import {LknMarketplace} from "../src/LknMarketplace.sol";
import {LinkenToken} from "../src/LinkenToken.sol";
import {ERC20} from "@openzeppelin/contracts/token/ERC20/ERC20.sol";

contract MockUSDC is ERC20 {
    constructor() ERC20("USD Coin", "USDC") {}

    function decimals() public pure override returns (uint8) {
        return 6;
    }

    function mint(address to, uint256 amount) external {
        _mint(to, amount);
    }
}

contract LknMarketplaceTest is Test {
    LknMarketplace marketplace;
    LinkenToken lkn;
    MockUSDC usdc;

    address admin = makeAddr("admin");
    address settler = makeAddr("settler");
    address treasury = makeAddr("treasury");
    address seller = makeAddr("seller");
    address buyer = makeAddr("buyer");

    uint256 constant TGE_SUPPLY = 200_000 * 1e18;

    function setUp() public {
        // Desplegar tokens
        vm.prank(admin);
        lkn = new LinkenToken(admin, seller, TGE_SUPPLY); // emisor = seller

        usdc = new MockUSDC();

        // Desplegar marketplace
        vm.prank(admin);
        marketplace = new LknMarketplace(admin, treasury);

        // Otorgar SETTLER_ROLE
        bytes32 settlerRole = marketplace.SETTLER_ROLE();
        vm.prank(admin);
        marketplace.grantRole(settlerRole, settler);

        // Fondear USDC al comprador
        usdc.mint(buyer, 1000 * 1e6); // 1000 USDC (6 decimales)
    }

    function test_SettleTradeSuccess() public {
        uint256 orderId = 123;
        uint256 tokenAmount = 100 * 1e18; // 100 LKN
        uint256 usdcAmount = 800 * 1e6;  // 800 USDC ($8 por LKN)
        uint256 feePercent = 100;        // 1% (100 / 10000)

        // Vendedor aprueba LKN al marketplace
        vm.prank(seller);
        lkn.approve(address(marketplace), tokenAmount);

        // Comprador aprueba USDC al marketplace
        vm.prank(buyer);
        usdc.approve(address(marketplace), usdcAmount);

        // Settler ejecuta settleTrade
        vm.prank(settler);
        marketplace.settleTrade(
            orderId,
            seller,
            buyer,
            address(lkn),
            address(usdc),
            tokenAmount,
            usdcAmount,
            feePercent
        );

        // Comprobar transferencia de LKN
        assertEq(lkn.balanceOf(buyer), tokenAmount);
        assertEq(lkn.balanceOf(seller), TGE_SUPPLY - tokenAmount);

        // Comprobar transferencia de USDC (800 total, 1% = 8 USDC para treasury, 792 para seller)
        uint256 expectedFee = 8 * 1e6;
        uint256 expectedNet = 792 * 1e6;

        assertEq(usdc.balanceOf(treasury), expectedFee);
        assertEq(usdc.balanceOf(seller), expectedNet);
        assertEq(usdc.balanceOf(buyer), 200 * 1e6); // 1000 - 800
    }

    function test_SettleTradeRevertsForNonSettler() public {
        uint256 orderId = 123;
        uint256 tokenAmount = 100 * 1e18;
        uint256 usdcAmount = 800 * 1e6;
        uint256 feePercent = 100;

        vm.prank(buyer);
        vm.expectRevert();
        marketplace.settleTrade(
            orderId,
            seller,
            buyer,
            address(lkn),
            address(usdc),
            tokenAmount,
            usdcAmount,
            feePercent
        );
    }
}
