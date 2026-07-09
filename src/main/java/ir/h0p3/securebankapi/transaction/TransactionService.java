package ir.h0p3.securebankapi.transaction;

import ir.h0p3.securebankapi.account.Account;
import ir.h0p3.securebankapi.account.AccountRepository;
import ir.h0p3.securebankapi.account.AccountStatus;
import ir.h0p3.securebankapi.transaction.dto.DepositRequest;
import ir.h0p3.securebankapi.transaction.dto.TransactionResponse;
import ir.h0p3.securebankapi.transaction.dto.WithdrawRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public TransactionResponse deposit(DepositRequest request, Authentication authentication) {
        Account account = accountRepository.findByAccountNumber(request.accountNumber())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        if (!account.getUser().getEmail().equals(authentication.getName())) {
            throw new IllegalArgumentException("You are not allowed to access this account");
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Account is not active");
        }

        account.setBalance(account.getBalance().add(request.amount()));
        accountRepository.save(account);

        Transaction transaction = Transaction.builder()
                .fromAccount(null)
                .toAccount(account)
                .amount(request.amount())
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.SUCCESS)
                .description(request.description())
                .createdAt(LocalDateTime.now())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);

        return toResponse(savedTransaction);
    }

    //Todo add mapstruct later
    private TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getFromAccount() == null ? null : transaction.getFromAccount().getAccountNumber(),
                transaction.getToAccount() == null ? null : transaction.getToAccount().getAccountNumber(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getDescription(),
                transaction.getCreatedAt()
        );
    }


    @Transactional
    public TransactionResponse withdraw(WithdrawRequest request, Authentication authentication) {
        Account account = accountRepository.findByAccountNumber(request.accountNumber())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        if (!account.getUser().getEmail().equals(authentication.getName())) {
            throw new IllegalArgumentException("You are not allowed to access this account");
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Account is not active");
        }

        if (account.getBalance().compareTo(request.amount()) < 0) {
            throw new IllegalArgumentException("Insufficient balance");
        }

        account.setBalance(account.getBalance().subtract(request.amount()));
        accountRepository.save(account);

        Transaction transaction = Transaction.builder()
                .fromAccount(account)
                .toAccount(null)
                .amount(request.amount())
                .type(TransactionType.WITHDRAW)
                .status(TransactionStatus.SUCCESS)
                .description(request.description())
                .createdAt(LocalDateTime.now())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);

        return toResponse(savedTransaction);
    }
}