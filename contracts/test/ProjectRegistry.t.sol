// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

// contracts/test/ProjectRegistry.t.sol

import {Test} from "forge-std/Test.sol";
import {ProjectRegistry} from "../src/ProjectRegistry.sol";

contract ProjectRegistryTest is Test {
    ProjectRegistry registry;

    address platform = makeAddr("platform");
    address creator = makeAddr("creator");
    address alice = makeAddr("alice");

    uint256 constant EARLY = 100_000; // 0.10 USDC/LKN
    uint256 constant STD = 250_000; // 0.25 USDC/LKN

    function setUp() public {
        vm.prank(platform);
        registry = new ProjectRegistry(platform);

        // Darle CREATOR_ROLE al creator
        bytes32 role = registry.CREATOR_ROLE();
        vm.prank(platform);
        registry.grantRole(role, creator);
    }

    function test_RegisterProject() public {
        vm.prank(creator);
        uint256 id = registry.registerProject("Campo Solar", "Desc", creator, EARLY, STD);
        assertEq(id, 1);
        assertEq(registry.projectCount(), 1);
    }

    function test_DefaultStageIsFunding() public {
        vm.prank(creator);
        uint256 id = registry.registerProject("Solar", "Desc", creator, EARLY, STD);
        ProjectRegistry.Project memory p = registry.getProject(id);
        assertEq(uint8(p.stage), uint8(ProjectRegistry.Stage.FUNDING));
    }

    function test_CurrentPriceIsFundingPrice() public {
        vm.prank(creator);
        uint256 id = registry.registerProject("Solar", "Desc", creator, EARLY, STD);
        assertEq(registry.currentPrice(id), EARLY);
    }

    function test_CurrentPriceAfterActivation() public {
        vm.prank(creator);
        uint256 id = registry.registerProject("Solar", "Desc", creator, EARLY, STD);

        vm.prank(creator);
        registry.setStage(id, ProjectRegistry.Stage.ACTIVE);

        assertEq(registry.currentPrice(id), STD);
    }

    function test_PausedProjectReverts() public {
        vm.prank(creator);
        uint256 id = registry.registerProject("Solar", "Desc", creator, EARLY, STD);

        vm.prank(creator);
        registry.setStage(id, ProjectRegistry.Stage.PAUSED);

        vm.expectRevert("PR: project paused");
        registry.currentPrice(id);
    }

    function test_EarlyBirdMustBeCheaper() public {
        vm.prank(creator);
        vm.expectRevert("PR: early bird must be cheaper");
        registry.registerProject("Solar", "Desc", creator, STD, EARLY);
    }

    function test_NonCreatorCannotRegister() public {
        vm.prank(alice);
        vm.expectRevert();
        registry.registerProject("Solar", "Desc", alice, EARLY, STD);
    }

    function test_OwnerCanChangeStage() public {
        vm.prank(creator);
        uint256 id = registry.registerProject("Solar", "Desc", creator, EARLY, STD);

        vm.prank(creator);
        registry.setStage(id, ProjectRegistry.Stage.ACTIVE);

        assertEq(uint8(registry.getProject(id).stage), uint8(ProjectRegistry.Stage.ACTIVE));
    }

    function test_NonOwnerCannotChangeStage() public {
        vm.prank(creator);
        uint256 id = registry.registerProject("Solar", "Desc", creator, EARLY, STD);

        vm.prank(alice);
        vm.expectRevert("PR: not authorized");
        registry.setStage(id, ProjectRegistry.Stage.ACTIVE);
    }

    function testFuzz_RegisterMultipleProjects(uint8 count) public {
        count = uint8(bound(count, 1, 20));
        vm.startPrank(creator);
        for (uint256 i = 0; i < count; i++) {
            registry.registerProject("P", "D", creator, EARLY, STD);
        }
        vm.stopPrank();
        assertEq(registry.projectCount(), count);
    }

    function test_ConstructorZeroAdminReverts() public {
        vm.expectRevert("PR: zero admin");
        new ProjectRegistry(address(0));
    }

    function test_RegisterEmptyNameReverts() public {
        vm.prank(creator);
        vm.expectRevert("PR: empty name");

        registry.registerProject("", "Desc", creator, EARLY, STD);
    }

    function test_RegisterZeroOwnerReverts() public {
        vm.prank(creator);

        vm.expectRevert("PR: zero owner");

        registry.registerProject("Solar", "Desc", address(0), EARLY, STD);
    }

    function test_RegisterZeroEarlyPriceReverts() public {
        vm.prank(creator);

        vm.expectRevert("PR: zero early bird price");

        registry.registerProject("Solar", "Desc", creator, 0, STD);
    }

    function test_RegisterZeroStandardPriceReverts() public {
        vm.prank(creator);

        vm.expectRevert("PR: zero standard price");

        registry.registerProject("Solar", "Desc", creator, EARLY, 0);
    }

    function test_GetProjectNotFoundReverts() public {
        vm.expectRevert("PR: not found");
        registry.getProject(999);
    }

    function test_CurrentPriceNotFoundReverts() public {
        vm.expectRevert("PR: not found");
        registry.currentPrice(999);
    }

    function test_SetStageNotFoundReverts() public {
        vm.expectRevert("PR: not found");
        registry.setStage(999, ProjectRegistry.Stage.ACTIVE);
    }
}
