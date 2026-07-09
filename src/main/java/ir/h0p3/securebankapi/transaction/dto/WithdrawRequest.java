package ir.h0p3.securebankapi.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record WithdrawRequest(
        @NotBlank
        String accountNumber,

        @NotNull
        @DecimalMin(value = "0.01")
        BigDecimal amount,

        String description
) {
}