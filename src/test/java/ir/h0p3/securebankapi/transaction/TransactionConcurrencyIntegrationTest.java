package ir.h0p3.securebankapi.transaction;

import ir.h0p3.securebankapi.account.Account;
import ir.h0p3.securebankapi.account.AccountRepository;
import ir.h0p3.securebankapi.account.AccountStatus;
import ir.h0p3.securebankapi.user.User;
import ir.h0p3.securebankapi.user.UserRepository;
import ir.h0p3.securebankapi.user.UserRole;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.TestConstructor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
        "jwt.secret=test-secret-key-test-secret-key-test-secret-key",
        "jwt.expiration=86400000"
})
@TestConstructor(
        autowireMode = TestConstructor.AutowireMode.ALL
)
class TransactionConcurrencyIntegrationTest {

    private static final String ACCOUNT_NUMBER = "6037991234567890";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(
                    DockerImageName.parse("postgres:17-alpine")
            );

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionTemplate transactionTemplate;

    private ExecutorService executorService;
    private Account account;

    TransactionConcurrencyIntegrationTest(
            UserRepository userRepository,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.saveAndFlush(
                User.builder()
                        .fullName("Concurrency Test User")
                        .email("concurrency@example.com")
                        .passwordHash("encoded-password")
                        .role(UserRole.CUSTOMER)
                        .enabled(true)
                        .createdAt(LocalDateTime.now())
                        .build()
        );

        account = accountRepository.saveAndFlush(
                Account.builder()
                        .accountNumber(ACCOUNT_NUMBER)
                        .balance(new BigDecimal("100.00"))
                        .status(AccountStatus.ACTIVE)
                        .user(user)
                        .createdAt(LocalDateTime.now())
                        .build()
        );

        executorService = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executorService.shutdownNow();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void shouldAllowOnlyOneUpdateWhenTwoTransactionsModifySameAccount()
            throws Exception {

        CyclicBarrier readBarrier = new CyclicBarrier(2);

        Future<UpdateResult> firstUpdate = executorService.submit(
                () -> withdrawInSeparateTransaction(
                        account.getId(),
                        new BigDecimal("80.00"),
                        readBarrier
                )
        );

        Future<UpdateResult> secondUpdate = executorService.submit(
                () -> withdrawInSeparateTransaction(
                        account.getId(),
                        new BigDecimal("80.00"),
                        readBarrier
                )
        );

        UpdateResult firstResult =
                firstUpdate.get(10, TimeUnit.SECONDS);

        UpdateResult secondResult =
                secondUpdate.get(10, TimeUnit.SECONDS);

        long successfulUpdates = Stream
                .of(firstResult, secondResult)
                .filter(UpdateResult::successful)
                .count();

        long failedUpdates = Stream
                .of(firstResult, secondResult)
                .filter(result -> !result.successful())
                .count();

        Account updatedAccount = accountRepository
                .findById(account.getId())
                .orElseThrow();

        assertThat(successfulUpdates)
                .isEqualTo(1);

        assertThat(failedUpdates)
                .isEqualTo(1);

        assertThat(updatedAccount.getBalance())
                .isEqualByComparingTo("20.00");

        assertThat(updatedAccount.getVersion())
                .isEqualTo(account.getVersion() + 1);

        UpdateResult failedResult = firstResult.successful()
                ? secondResult
                : firstResult;

        assertThat(failedResult.exception())
                .isNotNull();

        assertThat(
                isOptimisticLockingException(
                        failedResult.exception()
                )
        ).isTrue();
    }

    private UpdateResult withdrawInSeparateTransaction(
            Long accountId,
            BigDecimal amount,
            CyclicBarrier readBarrier
    ) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                Account currentAccount = accountRepository
                        .findById(accountId)
                        .orElseThrow();

                awaitBarrier(readBarrier);

                currentAccount.setBalance(
                        currentAccount
                                .getBalance()
                                .subtract(amount)
                );

                accountRepository.saveAndFlush(currentAccount);
            });

            return UpdateResult.success();

        } catch (RuntimeException exception) {
            return UpdateResult.failure(exception);
        }
    }

    private void awaitBarrier(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Concurrent test thread was interrupted",
                    exception
            );

        } catch (
                BrokenBarrierException |
                TimeoutException exception
        ) {
            throw new IllegalStateException(
                    "Concurrent test synchronization failed",
                    exception
            );
        }
    }

    private boolean isOptimisticLockingException(
            Throwable throwable
    ) {
        Throwable current = throwable;

        while (current != null) {
            if (current
                    instanceof ObjectOptimisticLockingFailureException
                    || current
                    instanceof OptimisticLockingFailureException
                    || current
                    instanceof OptimisticLockException) {

                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private record UpdateResult(
            boolean successful,
            Throwable exception
    ) {

        static UpdateResult success() {
            return new UpdateResult(true, null);
        }

        static UpdateResult failure(Throwable exception) {
            return new UpdateResult(false, exception);
        }
    }
}