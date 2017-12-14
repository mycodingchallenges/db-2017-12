package com.db.awmd.challenge.domain;

import com.db.awmd.challenge.exception.OperationException;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
@Slf4j
public class Account {

  private enum Operation {
    CREDIT {
      @Override
      BigDecimal execute(final BigDecimal a, final BigDecimal b) {
        return a.add(b);
      }
    },

    DEBIT {
      @Override
      BigDecimal execute(final BigDecimal currentBalance, final BigDecimal amount) throws OperationException {

        if (currentBalance.compareTo(amount) < 0) {
          throw new OperationException("Current balance is less than the amount to be debited. Current balance is %s. Amount to be debited is %s.", currentBalance, amount);
        }

        return currentBalance.subtract(amount);
      }
    };

    abstract BigDecimal execute(final BigDecimal a, final BigDecimal b) throws OperationException;
  }


  @NotNull
  @NotEmpty
  private final String accountId;

  @NotNull
  @Min(value = 0, message = "Initial balance must be positive.")
  @Setter(AccessLevel.PRIVATE)
  private volatile BigDecimal balance;

  public Account(String accountId) {
    this.accountId = accountId;
    this.balance = BigDecimal.ZERO;
  }

  @JsonCreator
  public Account(@JsonProperty("accountId") String accountId,
    @JsonProperty("balance") BigDecimal balance) {
    this.accountId = accountId;
    this.balance = balance;
  }

  public BigDecimal credit(final BigDecimal amount) throws OperationException {
    return updateBalance(Operation.CREDIT, amount);
  }

  public BigDecimal debit(final BigDecimal amount) throws OperationException {
    return updateBalance(Operation.DEBIT, amount);
  }

  private BigDecimal updateBalance(final Operation operator, final BigDecimal amount) throws OperationException {

    //If benchmarks reveal high contention on the lock, try Compare-and-Set (CAS) instead of locking.

    final BigDecimal newBalance;

    synchronized (this) {

      final BigDecimal currentBalance = getBalance();
      newBalance = operator.execute(currentBalance, amount);
      setBalance(newBalance);
    }

    log.debug(
            "{} amount {} {} account {}. New balance is {}.",
            operator == Operation.CREDIT ? "Credited" : "Debited",
            amount,
            operator == Operation.CREDIT ? "to" : "from",
            this.accountId,
            newBalance
    );

    return newBalance;
  }
}
