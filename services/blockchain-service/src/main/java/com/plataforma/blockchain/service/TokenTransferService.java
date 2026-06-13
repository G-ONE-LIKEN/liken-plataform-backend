package com.plataforma.blockchain.service;

import com.plataforma.blockchain.config.ContractsProperties;
import com.plataforma.blockchain.config.PublicationProperties;
import com.plataforma.blockchain.config.Web3Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenTransferService {

    private final Web3j web3j;
    private final ContractsProperties contractsProperties;
    private final PublicationProperties publicationProperties;
    private final Web3Properties web3Properties;

    public void executeTransferFrom(String fromAddress, String toAddress, BigDecimal tokenAmount) throws Exception {
        String privateKey = publicationProperties.getSignerPrivateKey();
        if (privateKey == null || privateKey.isBlank()) {
            throw new IllegalStateException("Missing signer private key for platform admin");
        }
        
        Credentials credentials = Credentials.create(privateKey);
        String tokenAddress = contractsProperties.getLinkenToken();

        // Convert tokenAmount to Wei (18 decimals)
        BigInteger amountWei = tokenAmount.movePointRight(18).setScale(0, RoundingMode.HALF_UP).toBigInteger();

        // transferFrom(address from, address to, uint256 amount)
        Function function = new Function(
                "transferFrom",
                Arrays.asList(new Address(fromAddress), new Address(toAddress), new Uint256(amountWei)),
                Collections.emptyList()
        );

        String encodedFunction = FunctionEncoder.encode(function);

        EthGetTransactionCount ethGetTransactionCount = web3j.ethGetTransactionCount(
                credentials.getAddress(), DefaultBlockParameterName.LATEST).send();
        BigInteger nonce = ethGetTransactionCount.getTransactionCount();

        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice().multiply(BigInteger.valueOf(2));
        BigInteger gasLimit = BigInteger.valueOf(100_000); // estimation

        RawTransaction rawTransaction = RawTransaction.createTransaction(
                nonce, gasPrice, gasLimit, tokenAddress, encodedFunction);

        byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, web3Properties.getChainId(), credentials);
        String hexValue = Numeric.toHexString(signedMessage);

        EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send();

        if (ethSendTransaction.hasError()) {
            throw new RuntimeException("Error executing transferFrom: " + ethSendTransaction.getError().getMessage());
        }

        log.info("Successfully sent transferFrom transaction: {}", ethSendTransaction.getTransactionHash());
    }

    public void executeSettleTrade(
            BigInteger orderId,
            String sellerWallet,
            String buyerWallet,
            BigDecimal tokenAmount,
            BigDecimal usdcAmount,
            BigInteger feePercent
    ) throws Exception {
        String privateKey = publicationProperties.getSignerPrivateKey();
        if (privateKey == null || privateKey.isBlank()) {
            throw new IllegalStateException("Missing platform admin signer private key");
        }

        Credentials credentials = Credentials.create(privateKey);
        String marketplaceAddress = contractsProperties.getMarketplace();
        String tokenAddress = contractsProperties.getLinkenToken();
        String usdcAddress = contractsProperties.getUsdc();

        if (marketplaceAddress == null || marketplaceAddress.isBlank() ||
            marketplaceAddress.equalsIgnoreCase("0x0000000000000000000000000000000000000000")) {
            throw new IllegalStateException("Marketplace contract address is not configured");
        }

        // Convert amounts to on-chain decimals
        BigInteger tokenAmountWei = tokenAmount.movePointRight(18).setScale(0, RoundingMode.HALF_UP).toBigInteger();
        BigInteger usdcAmountRaw = usdcAmount.movePointRight(6).setScale(0, RoundingMode.HALF_UP).toBigInteger();

        // settleTrade(uint256, address, address, address, address, uint256, uint256, uint256)
        Function function = new Function(
                "settleTrade",
                Arrays.asList(
                        new Uint256(orderId),
                        new Address(sellerWallet),
                        new Address(buyerWallet),
                        new Address(tokenAddress),
                        new Address(usdcAddress),
                        new Uint256(tokenAmountWei),
                        new Uint256(usdcAmountRaw),
                        new Uint256(feePercent)
                ),
                Collections.emptyList()
        );

        String encodedFunction = FunctionEncoder.encode(function);

        EthGetTransactionCount ethGetTransactionCount = web3j.ethGetTransactionCount(
                credentials.getAddress(), DefaultBlockParameterName.LATEST).send();
        BigInteger nonce = ethGetTransactionCount.getTransactionCount();

        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice().multiply(BigInteger.valueOf(2));
        BigInteger gasLimit = BigInteger.valueOf(250_000); // Settle takes more gas than simple transfer

        RawTransaction rawTransaction = RawTransaction.createTransaction(
                nonce, gasPrice, gasLimit, marketplaceAddress, encodedFunction);

        byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, web3Properties.getChainId(), credentials);
        String hexValue = Numeric.toHexString(signedMessage);

        EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send();

        if (ethSendTransaction.hasError()) {
            throw new RuntimeException("Error executing settleTrade: " + ethSendTransaction.getError().getMessage());
        }

        log.info("Successfully sent settleTrade transaction for order {}: {}", orderId, ethSendTransaction.getTransactionHash());
    }
}
