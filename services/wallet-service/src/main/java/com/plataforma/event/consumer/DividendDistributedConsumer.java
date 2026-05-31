package com.plataforma.event.consumer;

import com.plataforma.event.dto.DividendDistributedEvent;
import com.plataforma.wallet.model.MovementType;
import com.plataforma.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DividendDistributedConsumer {

    private final WalletService walletService;

    @KafkaListener(topics = "dividends.distributed", groupId = "wallet-service")
    public void consume(DividendDistributedEvent event) {
        try {
            log.info("Procesando dividendo: usuario={}, monto={}, dividendId={}",
                    event.getUserId(), event.getAmount(), event.getDividendId());

            walletService.recordMovement(
                    event.getUserId(),
                    MovementType.DIVIDEND,
                    event.getAmount(),
                    "Dividendo del proyecto " + event.getProjectId(),
                    event.getDividendId(),
                    event.getEventId()
            );
        } catch (Exception e) {
            log.error("Error procesando evento dividends.distributed para usuario {}: {}",
                    event.getUserId(), e.getMessage(), e);
        }
    }
}
