package com.db.awmd.challenge.service;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.domain.MoneyTransferResult;
import com.db.awmd.challenge.exception.AccountNotFoundException;
import com.db.awmd.challenge.exception.DuplicateAccountIdException;
import com.db.awmd.challenge.exception.MoneyTransferException;
import com.db.awmd.challenge.exception.OperationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import javax.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.fail;
import static org.mockito.BDDMockito.any;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("tests")
public class AccountsServiceTest {

  private static <T> void executeTasksConcurrently(int nThreads, List<Callable<T>> tasks, int timeout, TimeUnit timeUnit) throws InterruptedException {

    final ExecutorService executor = Executors.newFixedThreadPool(nThreads);
    try {
      executor.invokeAll(tasks);
    } finally {
      executor.shutdown();
      executor.awaitTermination(timeout, timeUnit);
    }
  }

  @SpyBean
  private AccountsService accountsService;

  @MockBean
  private NotificationService notificationService;

  @Before
  public void beforeEveryTest() {
    this.accountsService.getAccountsRepository().clearAccounts();
  }

  @After
  public void afterEveryTest() {
    this.accountsService.getAccountsRepository().clearAccounts();
  }

  @Test
  public void addAccount() {
    Account account = new Account("Id-123", new BigDecimal(1000));
    this.accountsService.createAccount(account);

    assertThat(this.accountsService.getAccount("Id-123")).isEqualTo(account);
  }

  @Test
  public void addAccount_failsOnDuplicateId() {
    String uniqueId = "Id-" + System.currentTimeMillis();
    Account account = new Account(uniqueId);
    this.accountsService.createAccount(account);

    try {
      this.accountsService.createAccount(account);
      fail("Should have failed when adding duplicate account");
    } catch (DuplicateAccountIdException ex) {
      assertThat(ex.getMessage()).isEqualTo("Account id " + uniqueId + " already exists!");
    }
  }

  @Test
  public void transferMoney_failsIfSourceAccountIdIsNull() {
    assertConstraintViolationException(null, "2", BigDecimal.ONE);
  }

  @Test
  public void transferMoney_failsIfSourceAccountIdIsEmpty() {
    assertConstraintViolationException("", "2", BigDecimal.ONE);
  }

  @Test
  public void transferMoney_failsIfSourceAccountIdIsBlank() {
    assertConstraintViolationException(" \t  \r  ", "2", BigDecimal.ONE);
  }

  @Test
  public void transferMoney_failsIfDestinationAccountIdIsNull() {
    assertConstraintViolationException("1", null, BigDecimal.ONE);
  }

  @Test
  public void transferMoney_failsIfDestinationAccountIdIsEmpty() {
    assertConstraintViolationException("1", "", BigDecimal.ONE);
  }

  @Test
  public void transferMoney_failsIfDestinationAccountIdIsBlank() {
    assertConstraintViolationException("1", " \t  \r  ", BigDecimal.ONE);
  }

  @Test
  public void transferMoney_failsIfAmountIsNull() {
    assertConstraintViolationException("1", "2", null);
  }

  @Test
  public void transferMoney_failsIfAmountIsZero() {
    assertConstraintViolationException("1", "2", BigDecimal.ZERO);
  }

  @Test
  public void transferMoney_failsIfAmountIsNegative() {
    assertConstraintViolationException("1", "2", new BigDecimal("-0.00000000000000000000000001"));
  }

  @Test
  public void transferMoney_failsIfSourceAccountDoesNotExist() {

    final String destinationAccountId = "destination";

    createAccount(destinationAccountId, BigDecimal.ONE);

    assertThatThrownBy(() -> this.accountsService.transferMoney("source", destinationAccountId, BigDecimal.ONE))
            .isInstanceOf(MoneyTransferException.class)
            .hasMessageMatching("Failed to transfer amount '.*' from account '.*' to account '.*'.")
            .hasRootCauseInstanceOf(AccountNotFoundException.class);
  }

  @Test
  public void transferMoney_failsIfDestinationAccountDoesNotExist() {

    final String sourceAccountId = "source";

    createAccount(sourceAccountId, BigDecimal.ONE);

    assertThatThrownBy(() -> this.accountsService.transferMoney(sourceAccountId, "destination", BigDecimal.ONE))
            .isInstanceOf(MoneyTransferException.class)
            .hasMessageMatching("Failed to transfer amount '.*' from account '.*' to account '.*'.")
            .hasRootCauseInstanceOf(AccountNotFoundException.class);
  }

  @Test
  public void transferMoney_succeedsIfInputsAreValid() throws MoneyTransferException {
    assertSuccess("4.536", "2.6677", "2.4214");
  }

  @Test
  public void transferMoney_succeedsIfAmountIsEqualToSourceAccountsCurrentBalance() throws MoneyTransferException {
    final String initialSrcBalance = "4.536";
    assertSuccess(initialSrcBalance, "0", /*amount*/ initialSrcBalance);
  }

  @Test
  public void transferMoney_failsIfAmountIsGreaterThanSourceAccountsCurrentBalance() {

    assertThatThrownBy(() -> transferMoney("source", "4.536", "destination", "0", "4.5361"))
            .isInstanceOf(MoneyTransferException.class)
            .hasMessageMatching("Failed to transfer amount '\\d+(\\.\\d+)?' from account 'source' to account 'destination'.")
            .hasRootCauseInstanceOf(OperationException.class)
            .hasStackTraceContaining("Current balance is less than the amount to be debited.");
  }

  @Test
  public void transferMoney_sourceAccountIsRefundedIfDestinationAccountsBalanceCouldNotBeUpdated() throws OperationException {

    final String srcAccountId = "source";
    final String initialSrcBalance = "4.536";
    final String destAccountId = "destination";
    final String initialDestBalance = "0";
    final String syntheticFailureMsg = "Synthetic Failure";

    Mockito.doThrow(new OperationException(syntheticFailureMsg)).when(this.accountsService).creditToDestinationAccount(any(Account.class), any(BigDecimal.class));

    assertThatThrownBy(() -> transferMoney(srcAccountId, initialSrcBalance, destAccountId, initialDestBalance, /*amount*/ initialSrcBalance))
            .isInstanceOf(MoneyTransferException.class)
            .hasMessageMatching("Failed to transfer amount '\\d+(\\.\\d+)?' from account 'source' to account 'destination'.")
            .hasRootCauseInstanceOf(OperationException.class)
            .hasStackTraceContaining(syntheticFailureMsg);

    assertThat(getAccountBalance(srcAccountId)).isEqualTo(new BigDecimal(initialSrcBalance));
    assertThat(getAccountBalance(destAccountId)).isEqualTo(new BigDecimal(initialDestBalance));
  }

  @Test
  public void transferMoney_correctnessUnderConcurrentTransactions() throws InterruptedException {

    final String accountId1 = "account-1";
    final BigDecimal initBalanceAccount1 =  new BigDecimal("100000");
    final String accountId2 = "account-2";
    final BigDecimal initBalanceAccount2 = new BigDecimal("200000");
    final int nTransfers = 10000;
    final int nThreads = 10;
    final int timeout = 30;
    final TimeUnit timeoutUnit = TimeUnit.SECONDS;


    {
      createAccount(accountId1, initBalanceAccount1);
      createAccount(accountId2, initBalanceAccount2);
    }

    final BigDecimal expectedCreditTo1, expectedCreditTo2;
    final List<Callable<MoneyTransferResult>> tasks;
    {
      final List<BigDecimal> transfers1to2 = generateRandomAmounts(nTransfers);
      final List<BigDecimal> transfers2to1 = generateRandomAmounts(nTransfers);


      tasks = new ArrayList<>(nTransfers * 2);

      addTransferTasks(accountId1, accountId2, transfers1to2, tasks);
      addTransferTasks(accountId2, accountId1, transfers2to1, tasks);

      Collections.shuffle(tasks);

      expectedCreditTo2 = sum(transfers1to2);
      expectedCreditTo1 = sum(transfers2to1);
    }

    executeTasksConcurrently(nThreads, tasks, timeout, timeoutUnit);

    assertThat(getAccountBalance(accountId1)).isEqualTo(initBalanceAccount1.add(expectedCreditTo1).subtract(expectedCreditTo2));
    assertThat(getAccountBalance(accountId2)).isEqualTo(initBalanceAccount2.add(expectedCreditTo2).subtract(expectedCreditTo1));
  }


  private void addTransferTasks(String sourceAccountId, String destinationAccountId, List<BigDecimal> transferAmounts, List<Callable<MoneyTransferResult>> tasks) {

    transferAmounts
            .stream()
            .map(amount -> (Callable<MoneyTransferResult>) () -> this.accountsService.transferMoney(sourceAccountId, destinationAccountId, amount))
            .forEach(tasks::add);
  }

  private BigDecimal sum(final List<BigDecimal> numbers) {

    return numbers.parallelStream().reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal getAccountBalance(String accountId) {

    return this.accountsService.getAccount(accountId).getBalance();
  }

  private void createAccount(final String accountId, final BigDecimal initialBalance) {

    this.accountsService.createAccount(new Account(accountId, initialBalance));
  }

  private List<BigDecimal> generateRandomAmounts(int nAmounts) {

    return new Random()
            .doubles(0.1, 1)
            .limit(nAmounts)
            .mapToObj(BigDecimal::valueOf)
            .collect(Collectors.toList());
  }

  private void assertSuccess(final String initialSrcBalance, final String initialDestBalance, final String amount) throws MoneyTransferException {

    final String srcAccountId = "source";
    final String destAccountId = "destination";

    final BigDecimal amountBd = new BigDecimal(amount);

    final BigDecimal srcBalanceExpected = new BigDecimal(initialSrcBalance).subtract(amountBd);
    final BigDecimal destBalanceExpected = new BigDecimal(initialDestBalance).add(amountBd);

    final MoneyTransferResult result = transferMoney(srcAccountId, initialSrcBalance, destAccountId, initialDestBalance, amount);

    assertThat(result).isNotNull();
    assertThat(result.getSourceAccountId()).isEqualTo(srcAccountId);
    assertThat(result.getDestinationAccountId()).isEqualTo(destAccountId);
    assertThat(result.getAmount()).isEqualTo(amountBd);
    assertThat(result.getSourceAccountBalanceNew()).isEqualTo(srcBalanceExpected);
    assertThat(result.getDestinationAccountBalanceNew()).isEqualTo(destBalanceExpected);
  }

  private MoneyTransferResult transferMoney(String srcAccountId, String initialSrcBalance, String destAccountId, String initialDestBalance, String amount) throws MoneyTransferException {

    final BigDecimal initialSrcBalanceBd = new BigDecimal(initialSrcBalance);
    final BigDecimal initialDestBalanceBd = new BigDecimal(initialDestBalance);
    final BigDecimal amountBd = new BigDecimal(amount);

    this.accountsService.createAccount(new Account(srcAccountId, initialSrcBalanceBd));
    this.accountsService.createAccount(new Account(destAccountId, initialDestBalanceBd));

    return this.accountsService.transferMoney(srcAccountId, destAccountId, amountBd);
  }


  private void assertConstraintViolationException(String sourceAccountId, String destinationAccountId, BigDecimal amount) {
    assertThatThrownBy(() -> this.accountsService.transferMoney(sourceAccountId, destinationAccountId, amount))
            .isInstanceOf(ConstraintViolationException.class);
  }
}
