package com.plataforma.notification.model;

public enum NotificationType {
    // Admin
    ADMIN_DEVELOPER_PENDING,
    ADMIN_PROJECT_PENDING,

    // Developer (dueño del proyecto / cuenta)
    DEVELOPER_STATUS_CHANGED,
    PROJECT_APPROVED,
    PROJECT_REJECTED,

    // Inversor
    INVESTMENT_CONFIRMED,
    DIVIDEND_RECEIVED,

    // Billetera
    WALLET_FUNDED,
    WALLET_DEBITED,

    // Broadcast manual
    BROADCAST
}
