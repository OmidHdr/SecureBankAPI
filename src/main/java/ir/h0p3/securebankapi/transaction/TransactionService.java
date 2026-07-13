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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public TransactionResponse deposit(
            DepositRequest request,
            Authentication authentication
    ) {
        log.debug(
                "Deposit requested: account={}, amount={}, user={}",
                maskAccountNumber(request.accountNumber()),
                request.amount(),
                authentication.getName()
        );

        Account account = accountRepository
                .findByAccountNumber(request.accountNumber())
                .orElseThrow(() -> {
                    log.warn(
                            "Deposit rejected because account was not found: account={}",
                            maskAccountNumber(request.accountNumber())
                    );

                    return new ResourceNotFoundException(
                            "Account not found"
                    );
                });

        if (!isAccountOwner(account, authentication)) {
            log.warn(
                    "Unauthorized deposit attempt: account={}, authenticatedUser={}",
                    maskAccountNumber(account.getAccountNumber()),
                    authentication.getName()
            );

            throw new ForbiddenException(
                    "You are not allowed to access this account"
            );
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            log.warn(
                    "Deposit rejected because account is not active: account={}, status={}",
                    maskAccountNumber(account.getAccountNumber()),
                    account.getStatus()
            );

            throw new BadRequestException(
                    "Account is not active"
            );
        }

        account.setBalance(
                account.getBalance().add(request.amount())
        );

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

        Transaction savedTransaction =
                transactionRepository.save(transaction);

        log.info(
                "Deposit completed: transactionId={}, account={}, amount={}",
                savedTransaction.getId(),
                maskAccountNumber(account.getAccountNumber()),
                request.amount()
        );

        return toResponse(savedTransaction);
    }

    @Transactional
    public TransactionResponse withdraw(
            WithdrawRequest request,
            Authentication authentication
    ) {
        log.debug(
                "Withdrawal requested: account={}, amount={}, user={}",
                maskAccountNumber(request.accountNumber()),
                request.amount(),
                authentication.getName()
        );

        Account account = accountRepository
                .findByAccountNumber(request.accountNumber())
                .orElseThrow(() -> {
                    log.warn(
                            "Withdrawal rejected because account was not found: account={}",
                            maskAccountNumber(request.accountNumber())
                    );

                    return new ResourceNotFoundException(
                            "Account not found"
                    );
                });

        if (!isAccountOwner(account, authentication)) {
            log.warn(
                    "Unauthorized withdrawal attempt: account={}, authenticatedUser={}",
                    maskAccountNumber(account.getAccountNumber()),
                    authentication.getName()
            );

            throw new ForbiddenException(
                    "You are not allowed to access this account"
            );
        }

        if (account.getStatus() != AccountStatus.ACTIVE) {
            log.warn(
                    "Withdrawal rejected because account is not active: account={}, status={}",
                    maskAccountNumber(account.getAccountNumber()),
                    account.getStatus()
            );

            throw new BadRequestException(
                    "Account is not active"
            );
        }

        if (account.getBalance().compareTo(request.amount()) < 0) {
            log.warn(
                    "Withdrawal rejected due to insufficient balance: account={}, requestedAmount={}",
                    maskAccountNumber(account.getAccountNumber()),
                    request.amount()
            );

            throw new BadRequestException(
                    "Insufficient balance"
            );
        }

        account.setBalance(
                account.getBalance().subtract(request.amount())
        );

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

        Transaction savedTransaction =
                transactionRepository.save(transaction);

        log.info(
                "Withdrawal completed: transactionId={}, account={}, amount={}",
                savedTransaction.getId(),
                maskAccountNumber(account.getAccountNumber()),
                request.amount()
        );

        return toResponse(savedTransaction);
    }

    @Transactional
    public TransactionResponse transfer(
            TransferRequest request,
            Authentication authentication
    ) {
        log.debug(
                "Transfer requested: fromAccount={}, toAccount={}, amount={}, user={}",
                maskAccountNumber(request.fromAccountNumber()),
                maskAccountNumber(request.toAccountNumber()),
                request.amount(),
                authentication.getName()
        );

        if (request.fromAccountNumber()
                .equals(request.toAccountNumber())) {

            log.warn(
                    "Transfer rejected because source and destination accounts are identical: account={}",
                    maskAccountNumber(request.fromAccountNumber())
            );

            throw new BadRequestException(
                    "Source and destination accounts must be different"
            );
        }

        Account fromAccount = accountRepository
                .findByAccountNumber(request.fromAccountNumber())
                .orElseThrow(() -> {
                    log.warn(
                            "Transfer rejected because source account was not found: account={}",
                            maskAccountNumber(request.fromAccountNumber())
                    );

                    return new ResourceNotFoundException(
                            "Source account not found"
                    );
                });

        Account toAccount = accountRepository
                .findByAccountNumber(request.toAccountNumber())
                .orElseThrow(() -> {
                    log.warn(
                            "Transfer rejected because destination account was not found: account={}",
                            maskAccountNumber(request.toAccountNumber())
                    );

                    return new ResourceNotFoundException(
                            "Destination account not found"
                    );
                });

        if (!isAccountOwner(fromAccount, authentication)) {
            log.warn(
                    "Unauthorized transfer attempt: fromAccount={}, authenticatedUser={}",
                    maskAccountNumber(fromAccount.getAccountNumber()),
                    authentication.getName()
            );

            throw new ForbiddenException(
                    "You are not allowed to transfer from this account"
            );
        }

        if (fromAccount.getStatus() != AccountStatus.ACTIVE) {
            log.warn(
                    "Transfer rejected because source account is not active: account={}, status={}",
                    maskAccountNumber(fromAccount.getAccountNumber()),
                    fromAccount.getStatus()
            );

            throw new BadRequestException(
                    "Source account is not active"
            );
        }

        if (toAccount.getStatus() != AccountStatus.ACTIVE) {
            log.warn(
                    "Transfer rejected because destination account is not active: account={}, status={}",
                    maskAccountNumber(toAccount.getAccountNumber()),
                    toAccount.getStatus()
            );

            throw new BadRequestException(
                    "Destination account is not active"
            );
        }

        if (fromAccount.getBalance()
                .compareTo(request.amount()) < 0) {

            log.warn(
                    "Transfer rejected due to insufficient balance: account={}, requestedAmount={}",
                    maskAccountNumber(fromAccount.getAccountNumber()),
                    request.amount()
            );

            throw new BadRequestException(
                    "Insufficient balance"
            );
        }

        fromAccount.setBalance(
                fromAccount.getBalance()
                        .subtract(request.amount())
        );

        toAccount.setBalance(
                toAccount.getBalance()
                        .add(request.amount())
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

        Transaction savedTransaction =
                transactionRepository.save(transaction);

        log.info(
                "Transfer completed: transactionId={}, fromAccount={}, toAccount={}, amount={}",
                savedTransaction.getId(),
                maskAccountNumber(fromAccount.getAccountNumber()),
                maskAccountNumber(toAccount.getAccountNumber()),
                request.amount()
        );

        return toResponse(savedTransaction);
    }

    public PagedResponse<TransactionResponse> getAccountTransactions(
            String accountNumber,
            int page,
            int size,
            TransactionType type,
            String sortDirection,
            Authentication authentication
    ) {
        log.debug(
                "Transaction history requested: account={}, page={}, size={}, type={}, direction={}, user={}",
                maskAccountNumber(accountNumber),
                page,
                size,
                type,
                sortDirection,
                authentication.getName()
        );

        validatePagination(page, size);

        Account account = accountRepository
                .findByAccountNumber(accountNumber)
                .orElseThrow(() -> {
                    log.warn(
                            "Transaction history rejected because account was not found: account={}",
                            maskAccountNumber(accountNumber)
                    );

                    return new ResourceNotFoundException(
                            "Account not found"
                    );
                });

        if (!isAccountOwner(account, authentication)) {
            log.warn(
                    "Unauthorized transaction history access attempt: account={}, authenticatedUser={}",
                    maskAccountNumber(account.getAccountNumber()),
                    authentication.getName()
            );

            throw new ForbiddenException(
                    "You are not allowed to access this account"
            );
        }

        Sort.Direction direction =
                parseSortDirection(sortDirection);

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(direction, "createdAt")
        );

        Page<Transaction> transactionPage;

        if (type == null) {
            transactionPage = transactionRepository
                    .findByFromAccountIdOrToAccountId(
                            account.getId(),
                            account.getId(),
                            pageable
                    );
        } else {
            transactionPage = transactionRepository
                    .findByTypeAndFromAccountIdOrTypeAndToAccountId(
                            type,
                            account.getId(),
                            type,
                            account.getId(),
                            pageable
                    );
        }

        Page<TransactionResponse> responsePage =
                transactionPage.map(this::toResponse);

        return PagedResponse.from(responsePage);
    }

    private boolean isAccountOwner(
            Account account,
            Authentication authentication
    ) {
        return account.getUser()
                .getEmail()
                .equals(authentication.getName());
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
                    "Page size must not be greater than "
                            + MAX_PAGE_SIZE
            );
        }
    }

    private Sort.Direction parseSortDirection(
            String sortDirection
    ) {
        if (sortDirection == null || sortDirection.isBlank()) {
            return Sort.Direction.DESC;
        }

        try {
            return Sort.Direction.fromString(sortDirection);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException(
                    "Sort direction must be ASC or DESC"
            );
        }
    }

    // TODO: Replace manual mapping with MapStruct later.
    private TransactionResponse toResponse(
            Transaction transaction
    ) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getFromAccount() == null
                        ? null
                        : transaction.getFromAccount()
                        .getAccountNumber(),
                transaction.getToAccount() == null
                        ? null
                        : transaction.getToAccount()
                        .getAccountNumber(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getStatus(),
                transaction.getDescription(),
                transaction.getCreatedAt()
        );
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }

        return "*".repeat(accountNumber.length() - 4)
                + accountNumber.substring(
                accountNumber.length() - 4
        );
    }
}