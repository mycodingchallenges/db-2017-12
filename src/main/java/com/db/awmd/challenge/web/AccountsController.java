package com.db.awmd.challenge.web;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.domain.MoneyTransferResult;
import com.db.awmd.challenge.exception.DuplicateAccountIdException;
import com.db.awmd.challenge.exception.MoneyTransferException;
import com.db.awmd.challenge.service.AccountsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
import java.math.BigDecimal;

@RestController
@RequestMapping("/v1/accounts")
@Slf4j
public class AccountsController {

  private final AccountsService accountsService;

  @Autowired
  public AccountsController(AccountsService accountsService) {
    this.accountsService = accountsService;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Object> createAccount(@RequestBody @Valid Account account) {
    log.info("Creating account {}", account);

    try {
    this.accountsService.createAccount(account);
    } catch (DuplicateAccountIdException daie) {
      return new ResponseEntity<>(daie.getMessage(), HttpStatus.BAD_REQUEST);
    }

    return new ResponseEntity<>(HttpStatus.CREATED);
  }

  @GetMapping(path = "/{accountId}")
  public Account getAccount(@PathVariable String accountId) {
    log.info("Retrieving account for id {}", accountId);
    return this.accountsService.getAccount(accountId);
  }

  @PatchMapping(path = "/transferMoney", produces = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<Object> transferMoney(

          @RequestParam(name = "sourceAccountId") final String sourceAccountId,
          @RequestParam(name = "destinationAccountId") final String destinationAccountId,
          @RequestParam(name = "amount") final BigDecimal amount) {

    MoneyTransferResult result = null;
    Throwable error = null;
    try {
      result = this.accountsService.transferMoney(sourceAccountId, destinationAccountId, amount);

    } catch (final ConstraintViolationException e) {

      error =
              new MoneyTransferException(
                      "Failed to transfer amount '%s' from account '%s' to account '%s'.",
                      new Object[]{amount, sourceAccountId, destinationAccountId},
                      e
              );

    } catch (final Throwable t) {
      error = t;
    }

    final ResponseEntity<Object> response;

    if (error == null) {

      response = new ResponseEntity<>(result, HttpStatus.OK);

    } else {

      response =
              new ResponseEntity<>(
                      error.getMessage(),
                      error instanceof MoneyTransferException ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR
              );
    }

    return response;
  }
}
