// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

// contracts/src/DividendDistributor.sol

import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import {SafeERC20} from "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";
import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import {LinkenToken} from "./LinkenToken.sol";
import "./interfaces/IDividendDistributor.sol";

/**
 * @title DividendDistributor
 * @notice Recibe ingresos en USDC y los distribuye proporcionalmente entre los holders de un LinkenToken.
 *
 * Algoritmo "dividends per share":
 *   magnifiedDPShare += (amount * MAGNITUDE) / totalSupply
 *   claimable(user)   = (balance(user) * magnifiedDPShare - correction(user)) / MAGNITUDE
 *
 * Patron pull: cada holder retira sus dividendos cuando quiere.
 * La plataforma nunca itera sobre holders (no hay loops).
 */
contract DividendDistributor is AccessControl, ReentrancyGuard, IDividendDistributor {
    using SafeERC20 for IERC20;

    bytes32 public constant DEPOSITOR_ROLE = keccak256("DEPOSITOR_ROLE");

    // Precision para evitar perdida por division entera
    uint256 private constant MAGNITUDE = 2 ** 128;

    LinkenToken public immutable token;
    IERC20 public immutable usdc;

    // Dividendos acumulados por token (escalados por MAGNITUDE)
    uint256 public magnifiedDividendPerShare;

    // Total de USDC depositado historicamente
    uint256 public totalDeposited;

    // Total de USDC retirado historicamente
    uint256 public totalWithdrawn;

    // Correccion por usuario para manejar cambios de balance
    // correction[user] = magnifiedDPShare al momento del ultimo cambio de balance * balance
    mapping(address => int256) private magnifiedDividendCorrections;

    // USDC ya retirado por usuario
    mapping(address => uint256) private withdrawnDividends;

    // ── Eventos ──────────────────────────────────────────────
    event DividendsDeposited(address indexed depositor, uint256 amount);
    event DividendsWithdrawn(address indexed holder, uint256 amount);

    // ── Constructor ──────────────────────────────────────────
    constructor(address _linkenToken, address _usdc, address platformAdmin) {
        require(_linkenToken != address(0), "DD: zero token");
        require(_usdc != address(0), "DD: zero usdc");
        require(platformAdmin != address(0), "DD: zero admin");

        token = LinkenToken(_linkenToken);
        usdc = IERC20(_usdc);

        _grantRole(DEFAULT_ADMIN_ROLE, platformAdmin);
        _grantRole(DEPOSITOR_ROLE, platformAdmin);
    }

    // ── Depositar dividendos (plataforma) ────────────────────

    /**
     * @notice Deposita `amount` USDC como dividendos para este proyecto.
     * @dev CEI: checks → effects → interaction (safeTransferFrom al final).
     *      Requiere approve previo del depositor hacia este contrato.
     */
    function depositDividends(uint256 amount) external onlyRole(DEPOSITOR_ROLE) nonReentrant {
        // Checks
        require(amount > 0, "DD: amount = 0");
        uint256 supply = token.totalSupply();
        require(supply > 0, "DD: no token supply");

        // Effects
        magnifiedDividendPerShare += (amount * MAGNITUDE) / supply;
        totalDeposited += amount;

        // Interaction
        usdc.safeTransferFrom(msg.sender, address(this), amount);
        emit DividendsDeposited(msg.sender, amount);
    }

    // ── Retirar dividendos (holder) ──────────────────────────

    /**
     * @notice El holder retira todos sus dividendos pendientes.
     * @dev CEI: calcula pendiente → actualiza estado → transfiere.
     */
    function claimDividends() external nonReentrant {
        uint256 pending = _pendingDividends(msg.sender);
        require(pending > 0, "DD: nothing to claim");

        // Effects
        withdrawnDividends[msg.sender] += pending;
        totalWithdrawn += pending;

        // Interaction
        usdc.safeTransfer(msg.sender, pending);
        emit DividendsWithdrawn(msg.sender, pending);
    }

    // ── Hooks — llamar desde LinkenToken al transferir ──────

    /**
     * @notice Ajusta las correcciones cuando un holder transfiere tokens.
     * @dev Debe ser llamado por LinkenToken en _update (from != 0 && to != 0).
     *      Solo puede ser llamado por el LinkenToken asociado.
     */
    function onTokenTransfer(address from, address to, uint256 amount) external {
        require(msg.sender == address(token), "DD: not linken token");

        int256 delta = int256(magnifiedDividendPerShare * amount);
        magnifiedDividendCorrections[from] += delta;
        magnifiedDividendCorrections[to] -= delta;
    }

    // ── Views ────────────────────────────────────────────────

    function pendingDividends(address holder) external view returns (uint256) {
        return _pendingDividends(holder);
    }

    function totalDividendsEarned(address holder) external view returns (uint256) {
        return _cumulativeDividends(holder);
    }

    // ── Internals ────────────────────────────────────────────

    function _cumulativeDividends(address holder) private view returns (uint256) {
        uint256 balance = token.balanceOf(holder);
        int256 raw = int256(magnifiedDividendPerShare * balance) + magnifiedDividendCorrections[holder];
        if (raw <= 0) return 0;
        return uint256(raw) / MAGNITUDE;
    }

    function _pendingDividends(address holder) private view returns (uint256) {
        uint256 cumulative = _cumulativeDividends(holder);
        uint256 withdrawn = withdrawnDividends[holder];
        if (cumulative <= withdrawn) return 0;
        return cumulative - withdrawn;
    }
}
