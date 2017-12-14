package com.db.awmd.challenge.service;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.domain.MoneyTransferResult;
import com.db.awmd.challenge.exception.AccountNotFoundException;
import com.db.awmd.challenge.exception.MoneyTransferException;
import com.db.awmd.challenge.exception.OperationException;
import com.db.awmd.challenge.repository.AccountsRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Optional;

@Service
@Slf4j
@Validated
public class AccountsService {

  @Getter
  private final AccountsRepository accountsRepository;

  private final NotificationService notificationService;

  @Autowired
  public AccountsService(AccountsRepository accountsRepository, NotificationService notificationService) {
    this.accountsRepository = accountsRepository;
    this.notificationService = notificationService;
  }

  public void createAccount(Account account) {
    this.accountsRepository.createAccount(account);
  }

  public Account getAccount(String accountId) {
    return this.accountsRepository.getAccount(accountId);
  }

  /**
   * Transfers {@code amount} from {@code sourceAccountId} to {@code destinationAccountId}.
   *
   * @param sourceAccountId
   *        id of the account that is the source of the transfer
   *
   * @param destinationAccountId
   *        id of the account that is the destination of the trasfer
   *
   * @param amount
   *        amount to be transferred
   *
   * @return summary of outcome of the transfer
   *
   * @throws MoneyTransferException if an error occurs due to wrong input
   */
  public MoneyTransferResult transferMoney(
          @NotBlank final String sourceAccountId,
          @NotBlank final String destinationAccountId,
          @NotNull @DecimalMin(value = "0", inclusive = false) final BigDecimal amount)
          throws MoneyTransferException {

    log.info("Transferring amount {} from account {} to account {}", amount, sourceAccountId, destinationAccountId);

    final MoneyTransferResult result;

    try {
      result = transferMoney0(sourceAccountId, destinationAccountId, amount);

    } catch (final Throwable t) {

      log.error("The following error occurred while transferring amount {} from account {} to account {}: {}", amount, sourceAccountId, destinationAccountId, t.getMessage());
      log.debug(t.getMessage(), t);
      throw t;
    }

    log.info("Transferred amount {} from account {} to account {}", amount, sourceAccountId, destinationAccountId);

    notify(sourceAccountId, destinationAccountId, amount);

    return result;
  }

  private MoneyTransferResult transferMoney0(
          final String sourceAccountId,
          final String destinationAccountId,
          final BigDecimal amount)
          throws MoneyTransferException {

    final BigDecimal srcBalanceNew;
    final BigDecimal destBalanceNew;


    try {

      final Account srcAccount = fetchAccount(sourceAccountId);
      final Account destAccount = fetchAccount(destinationAccountId);

      srcBalanceNew = srcAccount.debit(amount);

      try {
        destBalanceNew = creditToDestinationAccount(destAccount, amount);

      } catch (final Throwable t) {

        srcAccount.credit(amount);
        throw t;
      }

    } catch (final OperationException | AccountNotFoundException ex) {

      throw new MoneyTransferException(
              "Failed to transfer amount '%s' from account '%s' to account '%s'.",
              new Object[]{amount, sourceAccountId, destinationAccountId},
              ex
      );
    }

    return new MoneyTransferResult(sourceAccountId, destinationAccountId, amount, srcBalanceNew, destBalanceNew);
  }

  /**
   * This method is not private to allow mocking for tests.
   */
  BigDecimal creditToDestinationAccount(final Account destAccount, final BigDecimal amount) throws OperationException {
    return destAccount.credit(amount);
  }

  private Account fetchAccount(final String sourceAccountId) throws AccountNotFoundException {

    return
            Optional
                    .ofNullable(getAccount(sourceAccountId))
                    .orElseThrow(() -> new AccountNotFoundException(sourceAccountId));
  }

  private void notify(final String srcAccountId, final String destAccountId, final BigDecimal amount) {

    final Account srcAccount = getAccount(srcAccountId);
    final Account destAccount = getAccount(destAccountId);

    this.notificationService.notifyAboutTransfer(srcAccount, String.format("Amount %s was transferred from your account to account %s.", amount, destAccount.getAccountId()));
    this.notificationService.notifyAboutTransfer(destAccount, String.format("Amount %s was transferred from account %s to your account.", amount, srcAccount.getAccountId()));
  }
}
