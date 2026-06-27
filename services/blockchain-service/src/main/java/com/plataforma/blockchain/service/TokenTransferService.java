package com.plataforma.blockchain.service;

import com.plataforma.blockchain.config.ContractsProperties;
import com.plataforma.blockchain.config.PublicationProperties;
import com.plataforma.blockchain.config.Web3Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.response.PollingTransactionReceiptProcessor;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

        String txHash = ethSendTransaction.getTransactionHash();
        log.info("Enviada transacción settleTrade para orden {}: {}. Esperando recibo...", orderId, txHash);

        org.web3j.tx.response.TransactionReceiptProcessor receiptProcessor = 
                new org.web3j.tx.response.PollingTransactionReceiptProcessor(web3j, 2000, 30);
        org.web3j.protocol.core.methods.response.TransactionReceipt receipt = 
                receiptProcessor.waitForTransactionReceipt(txHash);

        if (!receipt.isStatusOK()) {
            throw new RuntimeException("Transacción revertida en la blockchain (receipt status 0x0)");
        }

        log.info("Liquidación on-chain exitosa para orden {}: {} (Gas usado: {})", orderId, txHash, receipt.getGasUsed());
    }

    /** Resultado de una deposito de dividendos on-chain. */
    public record DepositResult(String txHash, BigInteger blockNumber) {}

    /**
     * Aprueba (si hace falta) y deposita {@code amountUsdc} en el
     * {@code DividendDistributor}. El signer debe tener el rol DEPOSITOR_ROLE.
     *
     * <p>El allowance se chequea on-chain antes; si esta por debajo del monto a
     * depositar se manda una tx {@code approve(distributor, max-uint256)} y se
     * espera receipt. Esto ahorra firmar un approve cada deposit despues del
     * primero.
     */
    public DepositResult executeDepositDividends(BigDecimal amountUsdc) throws Exception {
        String privateKey = publicationProperties.getSignerPrivateKey();
        if (privateKey == null || privateKey.isBlank()) {
            throw new IllegalStateException("Missing platform admin signer private key");
        }

        Credentials credentials = Credentials.create(privateKey);
        String signer = credentials.getAddress();
        String usdcAddress = contractsProperties.getUsdc();
        String distributorAddress = contractsProperties.getDistributor();

        if (distributorAddress == null || distributorAddress.isBlank()
                || distributorAddress.equalsIgnoreCase("0x0000000000000000000000000000000000000000")) {
            throw new IllegalStateException("Distributor contract address is not configured");
        }
        if (usdcAddress == null || usdcAddress.isBlank()
                || usdcAddress.equalsIgnoreCase("0x0000000000000000000000000000000000000000")) {
            throw new IllegalStateException("USDC contract address is not configured");
        }

        BigInteger amountRaw = amountUsdc.movePointRight(6).setScale(0, RoundingMode.DOWN).toBigInteger();
        if (amountRaw.signum() <= 0) {
            throw new IllegalArgumentException("amountUsdc must be > 0, got " + amountUsdc);
        }

        // 1) Allowance check / approve si hace falta.
        BigInteger currentAllowance = readAllowance(signer, usdcAddress, distributorAddress);
        if (currentAllowance.compareTo(amountRaw) < 0) {
            log.info("Allowance USDC insuficiente ({}); aprobando max-uint256 a {}", currentAllowance, distributorAddress);
            sendApproveMax(credentials, usdcAddress, distributorAddress);
        }

        // 2) depositDividends(uint256 amount)
        Function deposit = new Function(
                "depositDividends",
                Arrays.asList(new Uint256(amountRaw)),
                Collections.emptyList()
        );
        String encoded = FunctionEncoder.encode(deposit);

        BigInteger nonce = web3j.ethGetTransactionCount(signer, DefaultBlockParameterName.LATEST)
                .send().getTransactionCount();
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice().multiply(BigInteger.valueOf(2));
        // depositDividends hace: AccessControl check + ReentrancyGuard + 2 SSTORE
        // (magnifiedDividendPerShare, totalDeposited) + safeTransferFrom externo a
        // USDC. Primera vez con cold storage consume ~115-180k gas. 120k era
        // apretado y la primera tx quedo out-of-gas. 250k da margen comodo.
        BigInteger gasLimit = BigInteger.valueOf(250_000);

        RawTransaction raw = RawTransaction.createTransaction(
                nonce, gasPrice, gasLimit, distributorAddress, encoded);
        byte[] signed = TransactionEncoder.signMessage(raw, web3Properties.getChainId(), credentials);
        EthSendTransaction sent = web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send();

        if (sent.hasError()) {
            throw new RuntimeException("Error executing depositDividends: " + sent.getError().getMessage());
        }
        String txHash = sent.getTransactionHash();
        log.info("Enviada tx depositDividends ({} USDC raw). Esperando receipt: {}", amountRaw, txHash);

        TransactionReceipt receipt = new PollingTransactionReceiptProcessor(web3j, 2000, 30)
                .waitForTransactionReceipt(txHash);
        if (!receipt.isStatusOK()) {
            throw new RuntimeException("depositDividends revertida (status 0x0) tx=" + txHash);
        }
        log.info("depositDividends OK: tx={} block={} gasUsed={}", txHash, receipt.getBlockNumber(), receipt.getGasUsed());
        return new DepositResult(txHash, receipt.getBlockNumber());
    }

    private BigInteger readAllowance(String owner, String tokenAddress, String spender) throws Exception {
        Function allowance = new Function(
                "allowance",
                Arrays.asList(new Address(owner), new Address(spender)),
                List.of(TypeReference.create(Uint256.class))
        );
        String result = web3j.ethCall(
                Transaction.createEthCallTransaction(owner, tokenAddress, FunctionEncoder.encode(allowance)),
                DefaultBlockParameterName.LATEST).send().getValue();
        List<Type> decoded = FunctionReturnDecoder.decode(result, allowance.getOutputParameters());
        return decoded.isEmpty() ? BigInteger.ZERO : ((Uint256) decoded.get(0)).getValue();
    }

    private void sendApproveMax(Credentials credentials, String tokenAddress, String spender) throws Exception {
        BigInteger maxUint = BigInteger.TWO.pow(256).subtract(BigInteger.ONE);
        Function approve = new Function(
                "approve",
                Arrays.asList(new Address(spender), new Uint256(maxUint)),
                Collections.emptyList()
        );
        String encoded = FunctionEncoder.encode(approve);
        BigInteger nonce = web3j.ethGetTransactionCount(credentials.getAddress(), DefaultBlockParameterName.LATEST)
                .send().getTransactionCount();
        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice().multiply(BigInteger.valueOf(2));
        RawTransaction raw = RawTransaction.createTransaction(
                nonce, gasPrice, BigInteger.valueOf(80_000), tokenAddress, encoded);
        byte[] signed = TransactionEncoder.signMessage(raw, web3Properties.getChainId(), credentials);
        EthSendTransaction sent = web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send();
        if (sent.hasError()) {
            throw new RuntimeException("Error executing approve: " + sent.getError().getMessage());
        }
        TransactionReceipt receipt = new PollingTransactionReceiptProcessor(web3j, 2000, 30)
                .waitForTransactionReceipt(sent.getTransactionHash());
        if (!receipt.isStatusOK()) {
            throw new RuntimeException("approve revertida (status 0x0) tx=" + sent.getTransactionHash());
        }
        log.info("approve max-uint a {} OK: tx={}", spender, sent.getTransactionHash());
    }
}
