// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

// forge-lint: disable(erc20-unchecked-transfer)

import {Test} from "forge-std/Test.sol";
import {OfferingContract} from "../src/OfferingContract.sol";
import {LinkenToken} from "../src/LinkenToken.sol";
import {ERC20} from "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import {ProjectRegistry} from "../src/ProjectRegistry.sol";

contract MockUSDC is ERC20 {
    constructor() ERC20("USD Coin", "USDC") {}

    function mint(address to, uint256 amount) external {
        _mint(to, amount);
    }

    function decimals() public pure override returns (uint8) {
        return 6;
    }
}

contract OfferingContractTest is Test {
    OfferingContract offering;
    LinkenToken lkn;
    MockUSDC usdc;
    ProjectRegistry registry;
    uint256 projectId;

    address platform = makeAddr("platform");
    address emisor = makeAddr("emisor");
    address treasury = makeAddr("treasury");
    address alice = makeAddr("alice");
    address bob = makeAddr("bob");

    // Parámetros del ejemplo del documento
    uint256 constant TGE_SUPPLY = 200_000 * 1e18;
    uint256 constant TOKEN_PRICE = 10_000_000; // USD 10 / LKN (6 dec)
    uint256 constant SOFT_CAP = 500_000 * 1e6; // USD 500k
    uint256 constant HARD_CAP = 2_000_000 * 1e6; // USD 2M
    uint256 constant DURATION = 30 days;

    function setUp() public {
        // Deploy LKN — TGE al emisor
        vm.prank(platform);
        lkn = new LinkenToken(platform, emisor, TGE_SUPPLY);

        // Deploy USDC mock
        usdc = new MockUSDC();

        // Deploy ProjectRegistry
        vm.prank(platform);
        registry = new ProjectRegistry(platform);

        // Registrar proyecto de prueba (earlyBird < standard, ambos > 0)
        vm.prank(platform);
        projectId = registry.registerProject(
            "Parque Solar Mendoza",
            "Desc",
            emisor,
            TOKEN_PRICE, // earlyBirdPrice = $10 (ronda actual)
            TOKEN_PRICE * 2 // standardPrice  = $20 (post-apertura)
        );

        // Deploy OfferingContract — ahora con registry y projectId
        vm.prank(platform);
        offering = new OfferingContract(
            address(lkn),
            address(usdc),
            treasury,
            TOKEN_PRICE,
            SOFT_CAP,
            HARD_CAP,
            block.timestamp + DURATION,
            platform,
            emisor,
            address(registry), // ← nuevo
            projectId // ← nuevo
        );

        // Otorgar OFFERING_ROLE al contrato en el registry
        bytes32 offeringRole = registry.OFFERING_ROLE();

        vm.prank(platform);
        registry.grantRole(offeringRole, address(offering));

        // Emisor aprueba y deposita LKN
        vm.startPrank(emisor);
        lkn.approve(address(offering), TGE_SUPPLY);
        offering.deposit(TGE_SUPPLY);
        offering.openRound();
        vm.stopPrank();

        // USDC para inversores
        usdc.mint(alice, 1_000_000 * 1e6);
        usdc.mint(bob, 1_000_000 * 1e6);
        vm.prank(alice);
        usdc.approve(address(offering), type(uint256).max);
        vm.prank(bob);
        usdc.approve(address(offering), type(uint256).max);

        // Treasury aprueba USDC para refunds
        vm.prank(treasury);
        usdc.approve(address(offering), type(uint256).max);
    }

    // ── Deploy y estado inicial ───────────────────────────────

    function test_InitialState() public view {
        assertEq(uint8(offering.state()), uint8(OfferingContract.RoundState.OPEN));
        assertEq(offering.lknDeposited(), TGE_SUPPLY);
        assertEq(offering.lknSold(), 0);
        assertEq(offering.totalRaised(), 0);
    }

    function test_ConstructorZeroLKNReverts() public {
        vm.expectRevert("OC: zero lkn");
        new OfferingContract(
            address(0),
            address(usdc),
            treasury,
            TOKEN_PRICE,
            SOFT_CAP,
            HARD_CAP,
            block.timestamp + DURATION,
            platform,
            emisor,
            address(registry),
            projectId
        );
    }

    function test_ConstructorHardCapLTESoftCapReverts() public {
        vm.expectRevert("OC: hard cap <= soft cap");
        new OfferingContract(
            address(lkn),
            address(usdc),
            treasury,
            TOKEN_PRICE,
            SOFT_CAP,
            SOFT_CAP,
            block.timestamp + DURATION,
            platform,
            emisor,
            address(registry),
            projectId
        );
    }

    function test_ConstructorDeadlineInPastReverts() public {
        vm.expectRevert("OC: deadline in past");
        new OfferingContract(
            address(lkn),
            address(usdc),
            treasury,
            TOKEN_PRICE,
            SOFT_CAP,
            HARD_CAP,
            block.timestamp - 1,
            platform,
            emisor,
            address(registry),
            projectId
        );
    }

    // ── Deposit ───────────────────────────────────────────────

    function test_DepositOnOpenRoundReverts() public {
        vm.prank(emisor);
        vm.expectRevert("OC: round not pending");
        offering.deposit(100 * 1e18);
    }

    function test_NonEmisorCannotDeposit() public {
        // Deploy nuevo contrato en PENDING para testear
        vm.prank(platform);
        OfferingContract fresh = new OfferingContract(
            address(lkn),
            address(usdc),
            treasury,
            TOKEN_PRICE,
            SOFT_CAP,
            HARD_CAP,
            block.timestamp + DURATION,
            platform,
            emisor,
            address(registry),
            projectId
        );
        vm.prank(alice);
        vm.expectRevert();
        fresh.deposit(100 * 1e18);
    }

    // ── Buy ───────────────────────────────────────────────────

    function test_BuyTransfersLKNAndUSDC() public {
        // 10 USDC → 1 LKN
        vm.prank(alice);
        offering.buy(10 * 1e6);

        assertEq(lkn.balanceOf(alice), 1e18);
        assertEq(usdc.balanceOf(treasury), 10 * 1e6);
        assertEq(offering.totalRaised(), 10 * 1e6);
        assertEq(offering.lknSold(), 1e18);
    }

    function test_BuyUpdatesContributions() public {
        vm.prank(alice);
        offering.buy(100 * 1e6);
        assertEq(offering.contributions(alice), 100 * 1e6);
    }

    function test_BuyZeroReverts() public {
        vm.prank(alice);
        vm.expectRevert("OC: zero amount");
        offering.buy(0);
    }

    function test_BuyExceedsHardCapReverts() public {
        vm.prank(alice);
        vm.expectRevert("OC: exceeds hard cap");
        offering.buy(HARD_CAP + 1);
    }

    function test_BuyAfterDeadlineReverts() public {
        vm.warp(block.timestamp + DURATION + 1);
        vm.prank(alice);
        vm.expectRevert("OC: deadline passed");
        offering.buy(10 * 1e6);
    }

    function test_HardCapClosesRoundAutomatically() public {
        usdc.mint(alice, HARD_CAP); // Alice compra exactamente el hard cap
        vm.prank(alice);
        offering.buy(HARD_CAP);

        assertEq(uint8(offering.state()), uint8(OfferingContract.RoundState.FINALIZED));
    }

    function test_PriceConversion() public {
        // 100 USDC a $10/LKN = 10 LKN
        vm.prank(alice);
        offering.buy(100 * 1e6);
        assertEq(lkn.balanceOf(alice), 10 * 1e18);
    }

    // ── Finalize ─────────────────────────────────────────────

    function test_FinalizeAfterSoftCap() public {
        // Alice compra suficiente para superar soft cap
        usdc.mint(alice, SOFT_CAP);
        vm.prank(alice);
        offering.buy(SOFT_CAP);

        uint256 emisorLKNBefore = lkn.balanceOf(emisor);

        vm.prank(emisor);
        offering.finalize();

        assertEq(uint8(offering.state()), uint8(OfferingContract.RoundState.FINALIZED));
        // Emisor recupera los LKN no vendidos
        assertGt(lkn.balanceOf(emisor), emisorLKNBefore);
    }

    function test_FinalizeBeforeSoftCapReverts() public {
        vm.prank(alice);
        offering.buy(100 * 1e6); // muy poco

        vm.prank(emisor);
        vm.expectRevert("OC: soft cap not reached");
        offering.finalize();
    }

    function test_NonEmisorCannotFinalize() public {
        usdc.mint(alice, SOFT_CAP);
        vm.prank(alice);
        offering.buy(SOFT_CAP);

        vm.prank(alice);
        vm.expectRevert();
        offering.finalize();
    }

    function test_FinalizeReturnsUnsoldLKN() public {
        // Solo compra 10 LKN de 200.000 disponibles
        vm.prank(alice);
        offering.buy(SOFT_CAP);

        uint256 unsoldBefore = offering.lknAvailable();
        assertGt(unsoldBefore, 0);

        vm.prank(emisor);
        offering.finalize();

        // Emisor recuperó los no vendidos (checkeando que el contrato se vacio)
        assertEq(lkn.balanceOf(address(offering)), 0);
    }

    // ── Refund ───────────────────────────────────────────────

    function test_RefundAfterDeadlineWithoutSoftCap() public {
        vm.prank(alice);
        offering.buy(100 * 1e6); // no llega al soft cap

        // Adelantar tiempo
        vm.warp(block.timestamp + DURATION + 1);

        // Treasury tiene USDC para devolver
        usdc.mint(treasury, SOFT_CAP);

        uint256 aliceBefore = usdc.balanceOf(alice);

        vm.prank(alice);
        offering.refund();

        assertEq(usdc.balanceOf(alice), aliceBefore + 100 * 1e6);
        assertEq(offering.contributions(alice), 0);
        assertEq(uint8(offering.state()), uint8(OfferingContract.RoundState.FAILED));
    }

    function test_RefundBeforeDeadlineReverts() public {
        vm.prank(alice);
        offering.buy(100 * 1e6);

        vm.prank(alice);
        vm.expectRevert("OC: round not failed");
        offering.refund();
    }

    function test_RefundWithNoContributionReverts() public {
        vm.warp(block.timestamp + DURATION + 1);

        vm.prank(bob);
        vm.expectRevert("OC: nothing to refund");
        offering.refund();
    }

    function test_RefundCannotBeCalledTwice() public {
        vm.prank(alice);
        offering.buy(100 * 1e6);

        vm.warp(block.timestamp + DURATION + 1);
        usdc.mint(treasury, SOFT_CAP);

        vm.startPrank(alice);
        offering.refund();
        vm.expectRevert("OC: nothing to refund");
        offering.refund();
        vm.stopPrank();
    }

    function test_MultipleInvestorsCanRefund() public {
        vm.prank(alice);
        offering.buy(100 * 1e6);

        vm.prank(bob);
        offering.buy(200 * 1e6);

        vm.warp(block.timestamp + DURATION + 1);
        usdc.mint(treasury, SOFT_CAP);

        vm.prank(alice);
        offering.refund();
        vm.prank(bob);
        offering.refund();

        assertEq(offering.contributions(alice), 0);
        assertEq(offering.contributions(bob), 0);
    }

    // ── isActive ─────────────────────────────────────────────

    function test_IsActiveWhenOpen() public view {
        assertTrue(offering.isActive());
    }

    function test_IsActiveReturnsFalseAfterDeadline() public {
        vm.warp(block.timestamp + DURATION + 1);
        assertFalse(offering.isActive());
    }

    // ── Fuzz ─────────────────────────────────────────────────

    function testFuzz_BuyAnyValidAmount(uint256 usdcAmount) public {
        usdcAmount = bound(usdcAmount, 1e6, HARD_CAP);

        usdc.mint(alice, usdcAmount);
        vm.prank(alice);
        usdc.approve(address(offering), usdcAmount);

        vm.prank(alice);
        offering.buy(usdcAmount);

        uint256 expectedLKN = (usdcAmount * 1e18) / TOKEN_PRICE;
        assertEq(lkn.balanceOf(alice), expectedLKN);
    }

    function testFuzz_ContributionsAlwaysLeHardCap(uint256 a, uint256 b) public {
        a = bound(a, 1 * 1e6, HARD_CAP / 2);
        b = bound(b, 1 * 1e6, HARD_CAP / 2);

        usdc.mint(alice, a);
        usdc.mint(bob, b);
        vm.prank(alice);
        usdc.approve(address(offering), a);
        vm.prank(bob);
        usdc.approve(address(offering), b);

        vm.prank(alice);
        offering.buy(a);
        vm.prank(bob);
        offering.buy(b);

        assertLe(offering.totalRaised(), HARD_CAP);
    }

    function test_FinalizeActivatesProjectInRegistry() public {
        usdc.mint(alice, SOFT_CAP);
        vm.prank(alice);
        offering.buy(SOFT_CAP);

        vm.prank(emisor);
        offering.finalize();

        ProjectRegistry.Project memory p = registry.getProject(projectId);
        assertEq(uint8(p.stage), uint8(ProjectRegistry.Stage.ACTIVE));
    }

    function test_HardCapAlsoActivatesProject() public {
        usdc.mint(alice, HARD_CAP);
        vm.prank(alice);
        usdc.approve(address(offering), HARD_CAP);
        vm.prank(alice);
        offering.buy(HARD_CAP);

        ProjectRegistry.Project memory p = registry.getProject(projectId);
        assertEq(uint8(p.stage), uint8(ProjectRegistry.Stage.ACTIVE));
    }
}
