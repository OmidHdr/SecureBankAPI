package ir.h0p3.securebankapi.account;

import ir.h0p3.securebankapi.account.dto.AccountResponse;
import ir.h0p3.securebankapi.common.exception.ResourceNotFoundException;
import ir.h0p3.securebankapi.user.User;
import ir.h0p3.securebankapi.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final String ACCOUNT_PREFIX = "603799";
    private static final int RANDOM_DIGITS = 10;

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();


    // Section create account
    public AccountResponse createAccount(Authentication authentication) {
        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Account account = Account.builder()
                .accountNumber(generateUniqueAccountNumber())
                .balance(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .user(user)
                .createdAt(LocalDateTime.now())
                .build();

        Account savedAccount = accountRepository.save(account);

        log.info(
                "Bank account created: accountId={}, accountNumber={}, userId={}",
                savedAccount.getId(),
                maskAccountNumber(savedAccount.getAccountNumber()),
                user.getId()
        );

        return toResponse(savedAccount);
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() < 4) {
            return "****";
        }

        return "*".repeat(accountNumber.length() - 4)
                + accountNumber.substring(accountNumber.length() - 4);
    }

    private String generateUniqueAccountNumber() {
        String accountNumber;

        do {
            accountNumber = generateAccountNumber();
        } while (accountRepository.existsByAccountNumber(accountNumber));

        return accountNumber;
    }

    private String generateAccountNumber() {
        StringBuilder builder = new StringBuilder(ACCOUNT_PREFIX);

        for (int i = 0; i < RANDOM_DIGITS; i++) {
            builder.append(secureRandom.nextInt(10));
        }

        return builder.toString();
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getBalance(),
                account.getStatus()
        );
    }

    // Section Get user accounts
    public List<AccountResponse> getMyAccounts(Authentication authentication) {
        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return accountRepository.findByUserId(user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }


}