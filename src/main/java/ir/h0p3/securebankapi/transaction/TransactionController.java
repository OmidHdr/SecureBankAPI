package ir.h0p3.securebankapi.transaction;

import ir.h0p3.securebankapi.transaction.dto.DepositRequest;
import ir.h0p3.securebankapi.transaction.dto.TransactionResponse;
import ir.h0p3.securebankapi.transaction.dto.TransferRequest;
import ir.h0p3.securebankapi.transaction.dto.WithdrawRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/deposit")
    public TransactionResponse deposit(
            @Valid @RequestBody DepositRequest request,
            Authentication authentication
    ) {
        return transactionService.deposit(request, authentication);
    }

    @PostMapping("/withdraw")
    public TransactionResponse withdraw(
            @Valid @RequestBody WithdrawRequest request,
            Authentication authentication
    ) {
        return transactionService.withdraw(request, authentication);
    }

    @PostMapping("/transfer")
    public TransactionResponse transfer(
            @Valid @RequestBody TransferRequest request,
            Authentication authentication
    ) {
        return transactionService.transfer(request, authentication);
    }

    @GetMapping("/account/{accountNumber}")
    public List<TransactionResponse> getAccountTransactions(
            @PathVariable String accountNumber,
            Authentication authentication
    ) {
        return transactionService.getAccountTransactions(
                accountNumber,
                authentication
        );
    }
}