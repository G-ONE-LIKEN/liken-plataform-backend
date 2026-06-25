// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {IERC20} from "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import {SafeERC20} from "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";
import {AccessControl} from "@openzeppelin/contracts/access/AccessControl.sol";

/**
 * @title LknMarketplace
 * @notice Contrato inteligente para la liquidación on-chain de transacciones P2P del marketplace.
 *         Permite el intercambio atómico de tokens LKN por USDC con cobro de comisión (fee).
 */
contract LknMarketplace is AccessControl {
    using SafeERC20 for IERC20;

    bytes32 public constant SETTLER_ROLE = keccak256("SETTLER_ROLE");
    address public treasury;

    event TradeSettled(
        uint256 indexed orderId,
        address indexed seller,
        address indexed buyer,
        address tokenAddress,
        uint256 tokenAmount,
        uint256 usdcAmount,
        uint256 feeAmount
    );

    constructor(address admin, address _treasury) {
        require(admin != address(0), "LknMarketplace: zero admin address");
        require(_treasury != address(0), "LknMarketplace: zero treasury address");
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(SETTLER_ROLE, admin);
        treasury = _treasury;
    }

    function setTreasury(address _treasury) external onlyRole(DEFAULT_ADMIN_ROLE) {
        require(_treasury != address(0), "LknMarketplace: zero treasury address");
        treasury = _treasury;
    }

    /**
     * @notice Liquida una transacción P2P intercambiando tokens LKN por USDC.
     * @param orderId El identificador de la orden en la base de datos de la plataforma.
     * @param seller La dirección del vendedor (propietario del token LKN).
     * @param buyer La dirección del comprador (propietario de los USDC).
     * @param tokenAddress La dirección del token LKN.
     * @param usdcAddress La dirección del token USDC.
     * @param tokenAmount Cantidad de tokens LKN a transferir (escala 18).
     * @param usdcAmount Cantidad de USDC a transferir (escala 6).
     * @param feePercent Porcentaje de fee cobrado al vendedor (ej. 100 = 1% con base 10000).
     */
    function settleTrade(
        uint256 orderId,
        address seller,
        address buyer,
        address tokenAddress,
        address usdcAddress,
        uint256 tokenAmount,
        uint256 usdcAmount,
        uint256 feePercent
    ) external onlyRole(SETTLER_ROLE) {
        require(seller != address(0), "LknMarketplace: invalid seller");
        require(buyer != address(0), "LknMarketplace: invalid buyer");
        require(tokenAmount > 0, "LknMarketplace: invalid token amount");
        require(usdcAmount > 0, "LknMarketplace: invalid usdc amount");

        // Calcular la comisión de la plataforma y el neto para el vendedor
        uint256 fee = (usdcAmount * feePercent) / 10000;
        uint256 netUsdc = usdcAmount - fee;

        // 1. Transferir LKN de seller a buyer
        IERC20(tokenAddress).safeTransferFrom(seller, buyer, tokenAmount);

        // 2. Transferir USDC neto de buyer a seller
        IERC20(usdcAddress).safeTransferFrom(buyer, seller, netUsdc);

        // 3. Transferir el fee de buyer a treasury
        if (fee > 0) {
            IERC20(usdcAddress).safeTransferFrom(buyer, treasury, fee);
        }

        emit TradeSettled(orderId, seller, buyer, tokenAddress, tokenAmount, usdcAmount, fee);
    }
}
