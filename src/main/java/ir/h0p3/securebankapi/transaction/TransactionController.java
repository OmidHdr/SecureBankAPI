package ir.h0p3.securebankapi.transaction;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ir.h0p3.securebankapi.common.response.PagedResponse;
import ir.h0p3.securebankapi.transaction.dto.DepositRequest;
import ir.h0p3.securebankapi.transaction.dto.TransactionResponse;
import ir.h0p3.securebankapi.transaction.dto.TransferRequest;
import ir.h0p3.securebankapi.transaction.dto.WithdrawRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(
        name = "Transactions",
        description = "Deposit, withdrawal, transfer and history endpoints"
)
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(
            summary = "Deposit money",
            description = "Deposits money into an active account owned by the authenticated user"
    )
    @PostMapping("/deposit")
    public TransactionResponse deposit(
            @Valid @RequestBody DepositRequest request,
            Authentication authentication
    ) {
        return transactionService.deposit(request, authentication);
    }

    @Operation(
            summary = "Withdraw money",
            description = "Withdraw money into an active account owned by the authenticated user"
    )
    @PostMapping("/withdraw")
    public TransactionResponse withdraw(
            @Valid @RequestBody WithdrawRequest request,
            Authentication authentication
    ) {
        return transactionService.withdraw(request, authentication);
    }

    @Operation(
            summary = "Transfer money",
            description = """
                Transfers money from an account owned by the authenticated user
                to another active bank account.
                """
    )
    @PostMapping("/transfer")
    public TransactionResponse transfer(
            @Valid @RequestBody TransferRequest request,
            Authentication authentication
    ) {
        return transactionService.transfer(request, authentication);
    }

    @Operation(
            summary = "Get account transaction history",
            description = """
                Returns paginated transaction history for an account owned by
                the authenticated user, with optional filtering and sorting.
                """
    )
    @GetMapping("/account/{accountNumber}")
    public PagedResponse<TransactionResponse> getAccountTransactions(
            @PathVariable String accountNumber,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size,

            @RequestParam(required = false)
            TransactionType type,

            @RequestParam(defaultValue = "DESC")
            String sortDirection,

            Authentication authentication
    ) {
        return transactionService.getAccountTransactions(
                accountNumber,
                page,
                size,
                type,
                sortDirection,
                authentication
        );
    }
}