// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/**
 * @title OfferingContract
 * @notice Venta primaria de tokens LKN a precio fijo con soft cap / hard cap.
 *
 * Flujo:
 *   1. Emisor aprueba LKN a este contrato y llama deposit().
 *   2. Inversores llaman buy(usdcAmount) durante la ronda.
 *   3a. Si totalRaised >= hardCap → ronda cierra automáticamente.
 *   3b. Si deadline pasó y totalRaised < softCap → inversores llaman refund().
 *   4. Si totalRaised >= softCap → emisor llama finalize().
 *
 * Ver ADR-0012 para el flujo completo y decisiones de diseño.
 */

import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import {SafeERC20} from "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";
import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import {ProjectRegistry} from "./ProjectRegistry.sol";

contract OfferingContract is AccessControl, ReentrancyGuard {
    using SafeERC20 for IERC20;

    // ── Roles ─────────────────────────────────────────────────
    bytes32 public constant EMISOR_ROLE = keccak256("EMISOR_ROLE");

    // ── Estado de la ronda ────────────────────────────────────
    enum RoundState {
        PENDING,
        OPEN,
        FINALIZED,
        FAILED
    }

    // ── Parámetros inmutables de la ronda ─────────────────────
    IERC20 public immutable lkn;
    IERC20 public immutable usdc;
    address public immutable treasury;
    ProjectRegistry public immutable registry;
    uint256 public immutable projectId;

    /// @notice USDC por LKN (6 decimales). Ej: 10_000_000 = USD 10/LKN
    uint256 public immutable tokenPrice;

    /// @notice Mínimo USDC a recaudar para que la ronda sea exitosa.
    uint256 public immutable softCap;

    /// @notice Máximo USDC a recaudar — cierra la ronda automáticamente.
    uint256 public immutable hardCap;

    /// @notice Timestamp límite para alcanzar el soft cap.
    uint256 public immutable deadline;

    // ── Estado mutable ────────────────────────────────────────
    RoundState public state;

    /// @notice LKN depositados por el emisor como escrow.
    uint256 public lknDeposited;

    /// @notice LKN ya vendidos.
    uint256 public lknSold;

    /// @notice USDC total recaudado.
    uint256 public totalRaised;

    /// @notice Contribución de cada inversor en USDC (para refunds).
    mapping(address => uint256) public contributions;

    // ── Eventos ───────────────────────────────────────────────
    event LKNDeposited(address indexed emisor, uint256 amount);
    event TokensPurchased(address indexed buyer, uint256 usdcAmount, uint256 lknAmount);
    event RoundFinalized(uint256 totalRaised, uint256 lknSold);
    event RoundFailed(uint256 totalRaised, uint256 softCap);
    event Refunded(address indexed investor, uint256 usdcAmount);
    event UnsoldLKNReturned(address indexed emisor, uint256 amount);

    // ── Constructor ───────────────────────────────────────────

    /**
     * @param _lkn        Address del LinkenToken.
     * @param _usdc       Address del USDC.
     * @param _treasury   Address que recibe el USDC recaudado.
     * @param _tokenPrice USDC por LKN con 6 decimales (ej: 10_000_000 = $10).
     * @param _softCap    Mínimo USDC a recaudar (6 decimales).
     * @param _hardCap    Máximo USDC a recaudar (6 decimales).
     * @param _deadline   Timestamp límite (Unix).
     * @param platformAdmin DEFAULT_ADMIN_ROLE.
     * @param emisor      Dirección del SPE/emisor que deposita los LKN.
     * @param _registry   Address del ProjectRegistry asociado.
     * @param _projectId  Identificador del proyecto registrado.
     */
    constructor(
        address _lkn,
        address _usdc,
        address _treasury,
        uint256 _tokenPrice,
        uint256 _softCap,
        uint256 _hardCap,
        uint256 _deadline,
        address platformAdmin,
        address emisor,
        address _registry,
        uint256 _projectId
    ) {
        require(_lkn != address(0), "OC: zero lkn");
        require(_usdc != address(0), "OC: zero usdc");
        require(_treasury != address(0), "OC: zero treasury");
        require(platformAdmin != address(0), "OC: zero admin");
        require(emisor != address(0), "OC: zero emisor");
        require(_tokenPrice > 0, "OC: zero price");
        require(_softCap > 0, "OC: zero soft cap");
        require(_hardCap > _softCap, "OC: hard cap <= soft cap");
        require(_deadline > block.timestamp, "OC: deadline in past");
        require(_registry != address(0), "OC: zero registry");

        lkn = IERC20(_lkn);
        usdc = IERC20(_usdc);
        treasury = _treasury;
        registry = ProjectRegistry(_registry);
        projectId = _projectId;
        tokenPrice = _tokenPrice;
        softCap = _softCap;
        hardCap = _hardCap;
        deadline = _deadline;

        _grantRole(DEFAULT_ADMIN_ROLE, platformAdmin);
        _grantRole(EMISOR_ROLE, emisor);

        state = RoundState.PENDING;
    }

    // ── Emisor: depositar LKN como escrow ─────────────────────

    /**
     * @notice El emisor deposita LKN antes de abrir la ronda.
     * @dev Requiere approve previo de LKN a este contrato.
     *      Puede llamarse múltiples veces hasta abrir la ronda.
     */
    function deposit(uint256 amount) external onlyRole(EMISOR_ROLE) nonReentrant {
        require(state == RoundState.PENDING, "OC: round not pending");
        require(amount > 0, "OC: zero amount");

        lknDeposited += amount;
        lkn.safeTransferFrom(msg.sender, address(this), amount);

        emit LKNDeposited(msg.sender, amount);
    }

    /**
     * @notice El emisor abre la ronda oficialmente.
     */
    function openRound() external onlyRole(EMISOR_ROLE) {
        require(state == RoundState.PENDING, "OC: not pending");
        require(lknDeposited > 0, "OC: no LKN deposited");
        require(block.timestamp < deadline, "OC: deadline passed");

        state = RoundState.OPEN;
    }

    // ── Inversores: comprar LKN ───────────────────────────────

    /**
     * @notice Compra LKN pagando USDC.
     * @dev CEI: checks → effects → interactions.
     *      Requiere approve previo de USDC a este contrato.
     * @param usdcAmount USDC a pagar (6 decimales).
     */
    function buy(uint256 usdcAmount) external nonReentrant {
        // Checks
        require(state == RoundState.OPEN, "OC: round not open");
        require(block.timestamp <= deadline, "OC: deadline passed");
        require(usdcAmount > 0, "OC: zero amount");
        require(totalRaised + usdcAmount <= hardCap, "OC: exceeds hard cap");

        uint256 lknAmount = (usdcAmount * 1e18) / tokenPrice;
        require(lknAmount > 0, "OC: lkn amount too small");
        require(lknAmount <= lknAvailable(), "OC: not enough LKN");

        // Effects
        contributions[msg.sender] += usdcAmount;
        totalRaised += usdcAmount;
        lknSold += lknAmount;

        // Cerrar automáticamente si se alcanzó el hard cap
        if (totalRaised >= hardCap) {
            state = RoundState.FINALIZED;
            registry.activateProject(projectId); // activar el proyecto.
            emit RoundFinalized(totalRaised, lknSold);
        }

        // Interactions
        usdc.safeTransferFrom(msg.sender, treasury, usdcAmount);
        lkn.safeTransfer(msg.sender, lknAmount);

        emit TokensPurchased(msg.sender, usdcAmount, lknAmount);
    }

    // ── Emisor: finalizar ronda exitosa ───────────────────────

    /**
     * @notice Finaliza la ronda si se alcanzó el soft cap.
     *         Devuelve los LKN no vendidos al emisor.
     */
    function finalize() external onlyRole(EMISOR_ROLE) nonReentrant {
        require(state == RoundState.OPEN, "OC: round not open");
        require(totalRaised >= softCap, "OC: soft cap not reached");

        state = RoundState.FINALIZED;

        uint256 unsold = lknAvailable();
        if (unsold > 0) {
            lkn.safeTransfer(msg.sender, unsold);
            emit UnsoldLKNReturned(msg.sender, unsold);
        }

        // Activar el proyecto automáticamente en el Registry
        registry.activateProject(projectId);

        emit RoundFinalized(totalRaised, lknSold);
    }

    // ── Inversores: refund si la ronda falló ──────────────────

    /**
     * @notice Devuelve el USDC al inversor si la ronda falló.
     * @dev Patrón pull — cada inversor retira individualmente.
     *      El treasury debe aprobar USDC a este contrato para honrar refunds.
     */
    function refund() external nonReentrant {
        require(_roundFailed(), "OC: round not failed");
        uint256 amount = contributions[msg.sender];
        require(amount > 0, "OC: nothing to refund");

        // Effects
        contributions[msg.sender] = 0;

        // Marcar como fallida la primera vez
        if (state != RoundState.FAILED) {
            state = RoundState.FAILED;
            emit RoundFailed(totalRaised, softCap);
        }

        // Interactions — el treasury devuelve el USDC
        usdc.safeTransferFrom(treasury, msg.sender, amount);

        emit Refunded(msg.sender, amount);
    }

    // ── Views ─────────────────────────────────────────────────

    /// @notice LKN disponibles para vender (depositados menos vendidos).
    function lknAvailable() public view returns (uint256) {
        return lknDeposited - lknSold;
    }

    /// @notice Verdadero si la ronda falló (deadline pasó sin alcanzar soft cap).
    function _roundFailed() internal view returns (bool) {
        return
            (state == RoundState.OPEN || state == RoundState.FAILED) && block.timestamp > deadline
                && totalRaised < softCap;
    }

    /// @notice Verdadero si la ronda está activa y dentro del deadline.
    function isActive() external view returns (bool) {
        return state == RoundState.OPEN && block.timestamp <= deadline;
    }
}
