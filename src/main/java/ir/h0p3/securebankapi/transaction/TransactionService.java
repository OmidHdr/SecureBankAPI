package ir.h0p3.securebankapi.transaction;

import ir.h0p3.securebankapi.account.Account;
import ir.h0p3.securebankapi.account.AccountRepository;
import ir.h0p3.securebankapi.account.AccountStatus;
import ir.h0p3.securebankapi.common.exception.BadRequestException;
import ir.h0p3.securebankapi.common.exception.ForbiddenException;
import ir.h0p3.securebankapi.common.exception.ResourceNotFoundException;
import ir.h0p3.securebankapi.common.response.PagedResponse;
import ir.h0p3.securebankapi.transaction.dto.DepositRequest;
import ir.h0p3.securebankapi.transaction.dto.TransactionResponse;
import ir.h0p3.securebankapi.transaction.dto.TransferRequest;
import ir.h0p3.securebankapi.transaction.dto.WithdrawRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static org.hibernate.dialect.SybaseASEDialect.MAX_PAGE_SIZE;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private static final int MAX_PAGE_SIZE = 100;

    @Transactional
    public TransactionResponse deposit(DepositRequest request, Authentication authentication) {
        Account account = accountRepository.findByAccountNumber(request.accountNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (!account.getUser().getEmail().equals(authentication.getName())) {
            throw new ForbiddenException("You are not allowed to access this account");
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BadRequestException("Account is not active");
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
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));

        if (!account.getUser().getEmail().equals(authentication.getName())) {
            throw new ForbiddenException("You are not allowed to access this account");
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BadRequestException("Account is not active");
        }

        if (account.getBalance().compareTo(request.amount()) < 0) {
            throw new BadRequestException("Insufficient balance");
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


    @Transactional
    public TransactionResponse transfer(
            TransferRequest request,
            Authentication authentication
    ) {
        if (request.fromAccountNumber().equals(request.toAccountNumber())) {
            throw new BadRequestException(
                    "Source and destination accounts must be different"
            );
        }

        Account fromAccount = accountRepository
                .findByAccountNumber(request.fromAccountNumber())
                .orElseThrow(() ->
                        new BadCredentialsException("Source account not found")
                );

        Account toAccount = accountRepository
                .findByAccountNumber(request.toAccountNumber())
                .orElseThrow(() ->
                        new BadRequestException("Destination account not found")
                );

        if (!fromAccount.getUser().getEmail().equals(authentication.getName())) {
            throw new ForbiddenException(
                    "You are not allowed to transfer from this account"
            );
        }

        if (fromAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new BadRequestException("Source account is not active");
        }

        if (toAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new BadRequestException("Destination account is not active");
        }

        if (fromAccount.getBalance().compareTo(request.amount()) < 0) {
            throw new BadRequestException("Insufficient balance");
        }

        fromAccount.setBalance(
                fromAccount.getBalance().subtract(request.amount())
        );

        toAccount.setBalance(
                toAccount.getBalance().add(request.amount())
        );

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        Transaction transaction = Transaction.builder()
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(request.amount())
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .description(request.description())
                .createdAt(LocalDateTime.now())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);

        return toResponse(savedTransaction);
    }


    public PagedResponse<TransactionResponse> getAccountTransactions(
            String accountNumber,
            int page,
            int size,
            Authentication authentication
    ) {
        validatePagination(page, size);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Account not found")
                );

        if (!account.getUser().getEmail().equals(authentication.getName())) {
            throw new ForbiddenException(
                    "You are not allowed to access this account"
            );
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<TransactionResponse> transactions = transactionRepository
                .findByFromAccountIdOrToAccountId(
                        account.getId(),
                        account.getId(),
                        pageable
                )
                .map(this::toResponse);

        return PagedResponse.from(transactions);
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new BadRequestException(
                    "Page number must be greater than or equal to zero"
            );
        }

        if (size < 1) {
            throw new BadRequestException(
                    "Page size must be greater than zero"
            );
        }

        if (size > MAX_PAGE_SIZE) {
            throw new BadRequestException(
                    "Page size must not be greater than " + MAX_PAGE_SIZE
            );
        }
    }
}