// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/**
 * @title Linken (LKN)
 * @notice ERC-20 de participación fraccionada en infraestructura de generación
 *         de energía renovable (parque solar / eólico).
 *
 * Modelo TGE (Token Generation Event):
 * - El supply se define en el constructor y se emite UNA SOLA VEZ al emisor.
 * - No existe función mint() — cero inflación post-TGE.
 * - Ejemplo: parque 5MW → 200.000 LKN emitidos al SPE dueño del parque.
 *
 * Decisiones de diseño:
 * - OpenZeppelin v5: última versión estable, compatibilidad nativa con 0.8.24.
 * - ReentrancyGuard: protege burn contra ataques de reentrada.
 * - Sin Pausable: pausar transferencias destruye la confianza del inversor (ver ADR-0015).
 * - AccessControl: roles separados para administración.
 * - Patrón CEI: validaciones → efectos → interacciones externas.
 * - Sin loops ni envío de ETH.
 */

import {ERC20} from "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import {ERC20Burnable} from "@openzeppelin/contracts/token/ERC20/extensions/ERC20Burnable.sol";
import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";
import {ReentrancyGuard} from "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import {IDividendDistributor} from "./interfaces/IDividendDistributor.sol";

contract LinkenToken is ERC20, ERC20Burnable, AccessControl, ReentrancyGuard {
    bytes32 public constant PAUSER_ROLE = keccak256("PAUSER_ROLE");

    /// @notice Supply total fijo — emitido en el TGE, nunca aumenta.
    uint256 public immutable initialSupply;

    IDividendDistributor public dividendDistributor;

    // ── Eventos ───────────────────────────────────────────────
    event Burned(address indexed from, uint256 amount);
    event DistributorSet(address indexed distributor);

    // ── Constructor — TGE ─────────────────────────────────────

    /**
     * @param platformAdmin  Recibe DEFAULT_ADMIN_ROLE y PAUSER_ROLE (plataforma).
     * @param tgeRecipient   Recibe el supply completo en el TGE (SPE / emisor).
     * @param tgeSupply      Cantidad de tokens a emitir. Fijo para siempre.
     */
    constructor(address platformAdmin, address tgeRecipient, uint256 tgeSupply) ERC20("Linken", "LKN") {
        require(platformAdmin != address(0), "LKN: zero admin");
        require(tgeRecipient != address(0), "LKN: zero recipient");
        require(tgeSupply > 0, "LKN: zero supply");

        initialSupply = tgeSupply;

        _grantRole(DEFAULT_ADMIN_ROLE, platformAdmin);

        // TGE — emisión única al SPE dueño del parque
        _mint(tgeRecipient, tgeSupply);
    }

    // ── Burn público (cualquier holder puede quemar sus tokens) ──

    /**
     * @notice Quema `amount` tokens propios.
     * @dev Sink de la economía del token — reduce supply circulante.
     */
    function burn(uint256 amount) public override nonReentrant {
        require(amount > 0, "LKN: amount must be > 0");
        super.burn(amount);
        emit Burned(msg.sender, amount);
    }

    /**
     * @notice Quema `amount` tokens de `account` con allowance.
     */
    function burnFrom(address account, uint256 amount) public override nonReentrant {
        require(amount > 0, "LKN: amount must be > 0");
        super.burnFrom(account, amount);
        emit Burned(account, amount);
    }

    // ── Distributor ───────────────────────────────────────────

    function setDistributor(address newDistributor) external onlyRole(DEFAULT_ADMIN_ROLE) {
        require(newDistributor != address(0), "LKN: zero distributor");
        dividendDistributor = IDividendDistributor(newDistributor);
        emit DistributorSet(newDistributor);
    }

    // ── Override requerido por herencia múltiple ──────────────

    function _update(address from, address to, uint256 value) internal virtual override(ERC20) {
        super._update(from, to, value);

        if (from != address(0) && to != address(0)) {
            if (address(dividendDistributor) != address(0)) {
                dividendDistributor.onTokenTransfer(from, to, value);
            }
        }
    }
}
