package ir.h0p3.securebankapi.account.dto;

import ir.h0p3.securebankapi.account.AccountStatus;

import java.math.BigDecimal;

public record AccountResponse(
        Long id,
        String accountNumber,
        BigDecimal balance,
        AccountStatus status
) {}