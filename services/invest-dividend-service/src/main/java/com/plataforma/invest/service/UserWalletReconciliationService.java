package com.plataforma.invest.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserWalletReconciliationService {

    private final UserContextClient userContextClient;
    private final InvestmentService investmentService;
    private final DividendService dividendService;

    @Transactional
    public void reconcileIfNeeded(Long userId) {
        UserContextClient.UserContext context = userContextClient.fetch(userId);
        String walletAddress = context != null ? context.walletAddress() : null;
        if (walletAddress == null || walletAddress.isBlank()) {
            return;
        }

        investmentService.reconcileWalletLinked(userId, walletAddress);
        dividendService.reconcileWalletLinked(userId, walletAddress);
    }
}
