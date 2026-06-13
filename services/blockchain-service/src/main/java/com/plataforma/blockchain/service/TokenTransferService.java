package com.plataforma.blockchain.service;

import com.plataforma.blockchain.config.ContractsProperties;
import com.plataforma.blockchain.config.PublicationProperties;
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

        BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();
        BigInteger gasLimit = BigInteger.valueOf(100_000); // estimation

        RawTransaction rawTransaction = RawTransaction.createTransaction(
                nonce, gasPrice, gasLimit, tokenAddress, encodedFunction);

        byte[] signedMessage = TransactionEncoder.signMessage(rawTransaction, credentials);
        String hexValue = Numeric.toHexString(signedMessage);

        EthSendTransaction ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send();

        if (ethSendTransaction.hasError()) {
            throw new RuntimeException("Error executing transferFrom: " + ethSendTransaction.getError().getMessage());
        }

        log.info("Successfully sent transferFrom transaction: {}", ethSendTransaction.getTransactionHash());
    }
}
