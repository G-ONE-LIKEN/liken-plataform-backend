// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

// contracts/src/ProjectRegistry.sol

import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

/**
 * @title ProjectRegistry
 * @notice Registra proyectos energeticos con su etapa y precio de conversión LKN/USDC.
 *
 * Etapas:
 *   FUNDING → precio early bird (más LKN por USDC).
 *   ACTIVE  → precio estándar.
 *   PAUSED  → no acepta nuevas inversiones.
 */
contract ProjectRegistry is AccessControl, ReentrancyGuard {
    bytes32 public constant CREATOR_ROLE = keccak256("CREATOR_ROLE");
    bytes32 public constant OFFERING_ROLE = keccak256("OFFERING_ROLE");

    enum Stage {
        FUNDING,
        ACTIVE,
        PAUSED
    }

    struct Project {
        string name;
        string description;
        address owner;
        Stage stage;
        uint256 earlyBirdPrice; // USDC per LKN (6 decimals) en etapa FUNDING
        uint256 standardPrice; // USDC per LKN (6 decimals) en etapa ACTIVE
        bool exists;
    }

    mapping(uint256 => Project) public projects;
    uint256 public projectCount;

    event ProjectRegistered(
        uint256 indexed projectId, address indexed owner, string name, uint256 earlyBirdPrice, uint256 standardPrice
    );
    event StageChanged(uint256 indexed projectId, Stage newStage);

    constructor(address platformAdmin) {
        require(platformAdmin != address(0), "PR: zero admin");
        _grantRole(DEFAULT_ADMIN_ROLE, platformAdmin);
        _grantRole(CREATOR_ROLE, platformAdmin);
    }

    // ── Registrar proyecto ───────────────────────────────────

    function registerProject(
        string calldata name,
        string calldata description,
        address owner,
        uint256 earlyBirdPrice,
        uint256 standardPrice
    ) external onlyRole(CREATOR_ROLE) nonReentrant returns (uint256 projectId) {
        require(bytes(name).length > 0, "PR: empty name");
        require(owner != address(0), "PR: zero owner");
        require(earlyBirdPrice > 0, "PR: zero early bird price");
        require(standardPrice > 0, "PR: zero standard price");
        require(earlyBirdPrice < standardPrice, "PR: early bird must be cheaper");

        projectId = ++projectCount;

        projects[projectId] = Project({
            name: name,
            description: description,
            owner: owner,
            stage: Stage.FUNDING,
            earlyBirdPrice: earlyBirdPrice,
            standardPrice: standardPrice,
            exists: true
        });

        emit ProjectRegistered(projectId, owner, name, earlyBirdPrice, standardPrice);
    }

    // ── Cambiar etapa ────────────────────────────────────────

    function setStage(uint256 projectId, Stage newStage) external {
        Project storage p = projects[projectId];
        require(p.exists, "PR: not found");
        require(hasRole(DEFAULT_ADMIN_ROLE, msg.sender) || p.owner == msg.sender, "PR: not authorized");
        p.stage = newStage;
        emit StageChanged(projectId, newStage);
    }

    /**
     * @notice Activa el proyecto automáticamente al finalizar una ronda exitosa.
     * @dev Solo puede ser llamado por un OfferingContract autorizado (OFFERING_ROLE).
     */
    function activateProject(uint256 projectId) external onlyRole(OFFERING_ROLE) {
        Project storage p = projects[projectId];
        require(p.exists, "PR: not found");
        require(p.stage == Stage.FUNDING, "PR: not in funding");
        p.stage = Stage.ACTIVE;
        emit StageChanged(projectId, Stage.ACTIVE);
    }

    // ── Views ────────────────────────────────────────────────

    function getProject(uint256 projectId) external view returns (Project memory) {
        require(projects[projectId].exists, "PR: not found");
        return projects[projectId];
    }

    /**
     * @notice Devuelve el precio vigente de LKN en USDC para un proyecto.
     *         Precio = USDC que cuesta 1 LKN (6 decimales).
     */
    function currentPrice(uint256 projectId) external view returns (uint256) {
        Project storage p = projects[projectId];
        require(p.exists, "PR: not found");
        require(p.stage != Stage.PAUSED, "PR: project paused");
        return p.stage == Stage.FUNDING ? p.earlyBirdPrice : p.standardPrice;
    }
}
