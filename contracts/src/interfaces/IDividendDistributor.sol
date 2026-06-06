// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

interface IDividendDistributor {
    function onTokenTransfer(address from, address to, uint256 amount) external;
}
