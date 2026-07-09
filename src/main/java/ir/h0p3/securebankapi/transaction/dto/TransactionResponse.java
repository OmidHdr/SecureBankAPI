package ir.h0p3.securebankapi.transaction.dto;

import ir.h0p3.securebankapi.transaction.TransactionStatus;
import ir.h0p3.securebankapi.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        String fromAccountNumber,
        String toAccountNumber,
        BigDecimal amount,
        TransactionType type,
        TransactionStatus status,
        String description,
        LocalDateTime createdAt
) {
}