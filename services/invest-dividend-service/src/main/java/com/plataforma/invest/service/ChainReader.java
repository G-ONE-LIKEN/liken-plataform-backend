package com.plataforma.invest.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.http.HttpService;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;

/**
 * Cliente Web3j read-only.
 *
 * <p>Hoy solo expone {@link #pendingDividends(String)} que llama a
 * {@code DividendDistributor.pendingDividends(address)} on-chain con
 * {@code eth_call} (sin firmar, no gasta gas).
 */
@Slf4j
@Service
public class ChainReader {

    private static final BigInteger USDC_DECIMALS_FACTOR = BigInteger.TEN.pow(6);

    private final Web3j web3j;
    private final String distributorAddress;

    public ChainReader(@Value("${web3.rpc-url}") String rpcUrl,
                       @Value("${contracts.distributor}") String distributorAddress) {
        this.web3j = Web3j.build(new HttpService(rpcUrl));
        this.distributorAddress = distributorAddress;
        log.info("ChainReader inicializado: rpc={} distributor={}", rpcUrl, distributorAddress);
    }

    /**
     * @return dividendos pendientes de retirar para esa wallet, en USDC (escala 6).
     *         Si la direccion del distributor no esta configurada o el RPC falla,
     *         devuelve cero (no se trata como error fatal: el front muestra $0).
     */
    public BigDecimal pendingDividends(String walletAddress) {
        if (!isConfigured()) {
            return BigDecimal.ZERO;
        }
        try {
            Function fn = new Function(
                    "pendingDividends",
                    List.of(new Address(walletAddress)),
                    List.of(TypeReference.create(Uint256.class)));
            String encoded = FunctionEncoder.encode(fn);

            String result = web3j.ethCall(
                    Transaction.createEthCallTransaction(null, distributorAddress, encoded),
                    DefaultBlockParameterName.LATEST).send().getValue();

            List<Type> decoded = FunctionReturnDecoder.decode(result, fn.getOutputParameters());
            if (decoded.isEmpty()) return BigDecimal.ZERO;
            BigInteger raw = ((Uint256) decoded.get(0)).getValue();
            return new BigDecimal(raw).divide(new BigDecimal(USDC_DECIMALS_FACTOR), 6, RoundingMode.HALF_UP);
        } catch (Exception ex) {
            log.warn("ethCall pendingDividends({}) fallo: {}", walletAddress, ex.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private boolean isConfigured() {
        return distributorAddress != null
                && !distributorAddress.isBlank()
                && !distributorAddress.equalsIgnoreCase("0x0000000000000000000000000000000000000000");
    }
}
