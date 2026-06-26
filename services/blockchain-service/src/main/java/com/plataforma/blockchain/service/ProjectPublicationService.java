package com.plataforma.blockchain.service;

import com.plataforma.blockchain.config.ContractsProperties;
import com.plataforma.blockchain.config.PublicationProperties;
import com.plataforma.blockchain.config.Web3Properties;
import com.plataforma.blockchain.dto.ProjectPublicationCommand;
import com.plataforma.blockchain.dto.ProjectPublicationFailureRequest;
import com.plataforma.blockchain.dto.ProjectPublicationSuccessRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectPublicationService {

    private static final Pattern REGISTRY_PROJECT_PATTERN =
            Pattern.compile("REGISTRY_PROJECT_ID\\s*=\\s*(\\d+)");
    private static final Pattern OFFERING_ADDRESS_PATTERN =
            Pattern.compile("OFFERING_ADDRESS\\s*=\\s*(0x[a-fA-F0-9]{40})");

    private final PublicationProperties publicationProperties;
    private final ContractsProperties contractsProperties;
    private final Web3Properties web3Properties;
    private final UserLookupClient userLookupClient;
    private final ProjectServiceClient projectServiceClient;
    private final SecretManagerService secretManagerService;

    private String getSignerPrivateKey() {
        if (publicationProperties.getSignerPrivateKeySecretName() != null && !publicationProperties.getSignerPrivateKeySecretName().isBlank()) {
            String projectId = System.getenv("GCP_PROJECT_ID");
            if (projectId == null) {
                throw new IllegalStateException("GCP_PROJECT_ID no configurado para leer el secret");
            }
            return secretManagerService.getSecret(projectId,
                    publicationProperties.getSignerPrivateKeySecretName(),
                    publicationProperties.getSignerPrivateKeySecretVersion());
        }
        return publicationProperties.getSignerPrivateKey();
    }

    @Async
    public void publishAsync(ProjectPublicationCommand command) {
        try {
            PublicationResult result = deploy(command);
            projectServiceClient.markPublicationSucceeded(ProjectPublicationSuccessRequest.builder()
                    .projectId(command.getProjectId())
                    .registryProjectId(result.registryProjectId())
                    .offeringContractAddress(result.offeringContractAddress())
                    .deployTxHash(result.deployTxHash())
                    .deployBlockNumber(result.deployBlockNumber())
                    .build());
            log.info("Publicacion on-chain completada: projectId={} offering={}",
                    command.getProjectId(), result.offeringContractAddress());
        } catch (Exception ex) {
            log.error("Fallo la publicacion on-chain del proyecto {}: {}", command.getProjectId(), ex.getMessage(), ex);
            projectServiceClient.markPublicationFailed(ProjectPublicationFailureRequest.builder()
                    .projectId(command.getProjectId())
                    .errorMessage(ex.getMessage())
                    .build());
        }
    }

    private PublicationResult deploy(ProjectPublicationCommand command) throws Exception {
        UserLookupClient.UserContext owner = userLookupClient.userContext(command.getOwnerId());
        if (owner.walletAddress() == null || owner.walletAddress().isBlank()) {
            throw new IllegalStateException("El owner del proyecto no tiene wallet vinculada");
        }

        Path workspace = Path.of(publicationProperties.getContractsWorkspace());
        if (!Files.isDirectory(workspace)) {
            throw new IllegalStateException("No existe contractsWorkspace para deploy: " + workspace);
        }

        List<String> processCommand = new ArrayList<>();
        processCommand.add(publicationProperties.getForgeCommand());
        processCommand.add("script");
        processCommand.add(publicationProperties.getScriptEntry());
        processCommand.add("--rpc-url");
        processCommand.add(web3Properties.getRpcUrl());
        processCommand.add("--broadcast");

        ProcessBuilder builder = new ProcessBuilder(processCommand);
        builder.directory(workspace.toFile());
        builder.redirectErrorStream(true);

        Map<String, String> env = builder.environment();
        env.put("PRIVATE_KEY", getSignerPrivateKey());
        env.put("LKN_ADDRESS", contractsProperties.getLinkenToken());
        env.put("REGISTRY_ADDRESS", contractsProperties.getRegistry());
        env.put("USDC_ADDRESS", contractsProperties.getUsdc());
        env.put("PLATFORM_ADMIN", publicationProperties.getPlatformAdmin());
        env.put("EMISOR", publicationProperties.getEmisor());
        env.put("TREASURY", publicationProperties.getTreasury());
        env.put("PROJECT_OWNER", owner.walletAddress());
        env.put("PROJECT_NAME", command.getProjectName());
        env.put("PROJECT_DESCRIPTION", command.getProjectDescription());
        env.put("EARLY_BIRD_PRICE", toUsdc6(command.getEarlyBirdPrice()));
        env.put("STANDARD_PRICE", toUsdc6(command.getStandardPrice()));
        env.put("TOKEN_PRICE", toUsdc6(command.getEarlyBirdPrice()));
        env.put("SOFT_CAP", toUsdc6(command.getSoftCap()));
        env.put("HARD_CAP", toUsdc6(command.getHardCap()));
        env.put("DEADLINE_TIMESTAMP", toDeadlineEpoch(command));
        env.put("ESCROW_LKN_AMOUNT_WEI", toLkn18(command.getTotalTokens()));

        Process process = builder.start();
        List<String> outputLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                outputLines.add(line);
            }
        }

        int exitCode = process.waitFor();
        String output = String.join(System.lineSeparator(), outputLines);
        if (exitCode != 0) {
            throw new IllegalStateException("Forge termino con exitCode=" + exitCode + ". Salida: " + tail(output));
        }

        Long registryProjectId = extractLong(REGISTRY_PROJECT_PATTERN, output, "REGISTRY_PROJECT_ID");
        String offeringAddress = extractString(OFFERING_ADDRESS_PATTERN, output, "OFFERING_ADDRESS");
        return new PublicationResult(registryProjectId, offeringAddress, null, null);
    }

    private String toDeadlineEpoch(ProjectPublicationCommand command) {
        ZoneId zoneId = ZoneId.of(publicationProperties.getDeadlineZoneId());
        long epoch = command.getExpectedOpenDate()
                .atTime(LocalTime.of(23, 59, 59))
                .atZone(zoneId)
                .toEpochSecond();
        return Long.toString(epoch);
    }

    private static String toUsdc6(BigDecimal value) {
        return value.movePointRight(6).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static String toLkn18(BigDecimal value) {
        return value.movePointRight(18).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static Long extractLong(Pattern pattern, String output, String label) {
        Matcher matcher = pattern.matcher(output);
        if (!matcher.find()) {
            throw new IllegalStateException("No pude parsear " + label + " desde la salida de forge");
        }
        return Long.parseLong(matcher.group(1));
    }

    private static String extractString(Pattern pattern, String output, String label) {
        Matcher matcher = pattern.matcher(output);
        if (!matcher.find()) {
            throw new IllegalStateException("No pude parsear " + label + " desde la salida de forge");
        }
        return matcher.group(1);
    }

    private static String tail(String output) {
        if (output == null || output.isBlank()) return "(sin salida)";
        String normalized = output.strip();
        if (normalized.length() <= 600) return normalized;
        return normalized.substring(Math.max(0, normalized.length() - 600));
    }

    private record PublicationResult(
            Long registryProjectId,
            String offeringContractAddress,
            String deployTxHash,
            Long deployBlockNumber) {
    }
}
