// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

// forge-lint: disable(erc20-unchecked-transfer)

import {Test} from "forge-std/Test.sol";
import {LinkenToken} from "../src/LinkenToken.sol";

contract LinkenTokenTest is Test {
    LinkenToken token;

    address platform = makeAddr("platform");
    address emisor = makeAddr("emisor"); // SPE dueño del parque
    address alice = makeAddr("alice");
    address bob = makeAddr("bob");

    uint256 constant TGE_SUPPLY = 200_000 * 1e18; // parque 5MW

    function setUp() public {
        vm.prank(platform);
        token = new LinkenToken(platform, emisor, TGE_SUPPLY);
    }

    // ── TGE ──────────────────────────────────────────────────

    function test_NameAndSymbol() public view {
        assertEq(token.name(), "Linken");
        assertEq(token.symbol(), "LKN");
    }

    function test_TGESupplyGoesToRecipient() public view {
        assertEq(token.balanceOf(emisor), TGE_SUPPLY);
        assertEq(token.totalSupply(), TGE_SUPPLY);
    }

    function test_InitialSupplyIsImmutable() public view {
        assertEq(token.initialSupply(), TGE_SUPPLY);
    }

    function test_NoMintAfterTGE() public view {
        // No existe función mint() — verificamos que el supply no creció
        assertEq(token.totalSupply(), TGE_SUPPLY);
    }

    function test_ConstructorZeroAdminReverts() public {
        vm.expectRevert("LKN: zero admin");
        new LinkenToken(address(0), emisor, TGE_SUPPLY);
    }

    function test_ConstructorZeroRecipientReverts() public {
        vm.expectRevert("LKN: zero recipient");
        new LinkenToken(platform, address(0), TGE_SUPPLY);
    }

    function test_ConstructorZeroSupplyReverts() public {
        vm.expectRevert("LKN: zero supply");
        new LinkenToken(platform, emisor, 0);
    }

    // ── Burn ─────────────────────────────────────────────────

    function test_HolderCanBurn() public {
        vm.prank(emisor);
        token.transfer(alice, 1_000 * 1e18);

        vm.prank(alice);
        token.burn(400 * 1e18);

        assertEq(token.balanceOf(alice), 600 * 1e18);
        assertEq(token.totalSupply(), TGE_SUPPLY - 400 * 1e18);
    }

    function test_BurnReducesTotalSupply() public {
        vm.prank(emisor);
        token.burn(TGE_SUPPLY);
        assertEq(token.totalSupply(), 0);
    }

    function test_BurnZeroReverts() public {
        vm.expectRevert("LKN: amount must be > 0");
        token.burn(0);
    }

    function test_BurnMoreThanBalanceReverts() public {
        vm.prank(alice);
        vm.expectRevert();
        token.burn(1);
    }

    function test_BurnFromWithAllowance() public {
        vm.prank(emisor);
        token.transfer(alice, 1_000 * 1e18);

        vm.prank(alice);
        token.approve(bob, 500 * 1e18);

        vm.prank(bob);
        token.burnFrom(alice, 500 * 1e18);

        assertEq(token.balanceOf(alice), 500 * 1e18);
    }

    function test_BurnFromZeroReverts() public {
        vm.expectRevert("LKN: amount must be > 0");
        token.burnFrom(alice, 0);
    }

    function test_BurnFromWithoutAllowanceReverts() public {
        vm.prank(emisor);
        token.transfer(alice, 1_000 * 1e18);

        vm.prank(bob);
        vm.expectRevert();
        token.burnFrom(alice, 100 * 1e18);
    }

    // ── Distributor ───────────────────────────────────────────

    function test_SetDistributor() public {
        vm.prank(platform);
        token.setDistributor(address(123));
        assertEq(address(token.dividendDistributor()), address(123));
    }

    function test_SetDistributorZeroReverts() public {
        vm.prank(platform);
        vm.expectRevert("LKN: zero distributor");
        token.setDistributor(address(0));
    }

    function test_NonAdminCannotSetDistributor() public {
        vm.prank(alice);
        vm.expectRevert();
        token.setDistributor(address(123));
    }

    // ── Fuzz ─────────────────────────────────────────────────

    function testFuzz_TGEAnyValidSupply(uint256 supply) public {
        supply = bound(supply, 1, type(uint128).max);
        LinkenToken t = new LinkenToken(platform, emisor, supply);
        assertEq(t.totalSupply(), supply);
        assertEq(t.balanceOf(emisor), supply);
        assertEq(t.initialSupply(), supply);
    }

    function testFuzz_BurnAnyValidAmount(uint256 amount) public {
        amount = bound(amount, 1, TGE_SUPPLY);

        vm.prank(emisor);
        token.transfer(alice, amount);

        vm.prank(alice);
        token.burn(amount);

        assertEq(token.totalSupply(), TGE_SUPPLY - amount);
    }

    function testFuzz_TransferAnyValidAmount(uint256 amount) public {
        amount = bound(amount, 1, TGE_SUPPLY);

        vm.prank(emisor);
        token.transfer(alice, amount);

        assertEq(token.balanceOf(alice), amount);
        assertEq(token.balanceOf(emisor), TGE_SUPPLY - amount);
    }

    // ── Invariant: supply nunca crece post-TGE ────────────────

    function invariant_SupplyNeverExceedsTGE() public view {
        assertLe(token.totalSupply(), token.initialSupply());
    }
}
