package com.db.awmd.challenge.web;

import com.db.awmd.challenge.domain.Account;
import com.db.awmd.challenge.service.AccountsService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@RunWith(SpringRunner.class)
@SpringBootTest
@WebAppConfiguration
public class AccountsControllerTest {

  private MockMvc mockMvc;

  @Autowired
  private AccountsService accountsService;

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Before
  public void prepareMockMvc() {
    this.mockMvc = webAppContextSetup(this.webApplicationContext).build();

    // Reset the existing accounts before each test.
    accountsService.getAccountsRepository().clearAccounts();
  }

  @Test
  public void createAccount() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isCreated());

    Account account = accountsService.getAccount("Id-123");
    assertThat(account.getAccountId()).isEqualTo("Id-123");
    assertThat(account.getBalance()).isEqualByComparingTo("1000");
  }

  @Test
  public void createDuplicateAccount() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isCreated());

    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\",\"balance\":1000}")).andExpect(status().isBadRequest());
  }

  @Test
  public void createAccountNoAccountId() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"balance\":1000}")).andExpect(status().isBadRequest());
  }

  @Test
  public void createAccountNoBalance() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\"}")).andExpect(status().isBadRequest());
  }

  @Test
  public void createAccountNoBody() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().isBadRequest());
  }

  @Test
  public void createAccountNegativeBalance() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"Id-123\",\"balance\":-1000}")).andExpect(status().isBadRequest());
  }

  @Test
  public void createAccountEmptyAccountId() throws Exception {
    this.mockMvc.perform(post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
      .content("{\"accountId\":\"\",\"balance\":1000}")).andExpect(status().isBadRequest());
  }

  @Test
  public void getAccount() throws Exception {
    String uniqueAccountId = "Id-" + System.currentTimeMillis();
    Account account = new Account(uniqueAccountId, new BigDecimal("123.45"));
    this.accountsService.createAccount(account);
    this.mockMvc.perform(get("/v1/accounts/" + uniqueAccountId))
      .andExpect(status().isOk())
      .andExpect(
        content().string("{\"accountId\":\"" + uniqueAccountId + "\",\"balance\":123.45}"));
  }

  @Test
  public void transferMoney() throws Exception {

    final String srcAccountId = "source";
    final BigDecimal initSrcBalance = new BigDecimal("10.37823");
    final String destAccountId = "destination";
    final BigDecimal initDestBalance = new BigDecimal("1.76299");
    final BigDecimal amount = new BigDecimal("3.4578");

    final Account srcAccount = new Account(srcAccountId, initSrcBalance);
    this.accountsService.createAccount(srcAccount);

    final Account destAccount = new Account(destAccountId, initDestBalance);
    this.accountsService.createAccount(destAccount);

    final MvcResult mvcResult =
            this.mockMvc
                    .perform(
                            patch("/v1/accounts/transferMoney")
                                    .param("sourceAccountId", srcAccountId)
                                    .param("destinationAccountId", destAccountId)
                                    .param("amount", amount.toPlainString())
                                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    )
                    .andExpect(status().isOk())
                    .andReturn();

    final String content = mvcResult.getResponse().getContentAsString();

    final Map<String, Object> contentJson = JsonParserFactory.getJsonParser().parseMap(content);

    assertThat(contentJson.get("sourceAccountId")).isEqualTo(srcAccountId);
    assertThat(contentJson.get("destinationAccountId")).isEqualTo(destAccountId);
    assertThat(new BigDecimal((String) contentJson.get("amount"))).isEqualTo(amount);
    assertThat(new BigDecimal((String) contentJson.get("sourceAccountBalanceNew"))).isEqualTo(initSrcBalance.subtract(amount));
    assertThat(new BigDecimal((String) contentJson.get("destinationAccountBalanceNew"))).isEqualTo(initDestBalance.add(amount));
  }

  @Test
  public void transferMoney_noParameters() throws Exception {

    this.mockMvc
            .perform(
                    patch("/v1/accounts/transferMoney")
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
            )
            .andExpect(status().isBadRequest());
  }

  @Test
  public void transferMoney_missingSourceAccountId() throws Exception {

    this.mockMvc
            .perform(
                    patch("/v1/accounts/transferMoney")
                            .param("destinationAccountId", "destination")
                            .param("amount", "1")
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
            )
            .andExpect(status().isBadRequest());
  }

  @Test
  public void transferMoney_missingDestinationAccountId() throws Exception {

    this.mockMvc
            .perform(
                    patch("/v1/accounts/transferMoney")
                            .param("sourceAccountId", "source")
                            .param("amount", "1")
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
            )
            .andExpect(status().isBadRequest());
  }

  @Test
  public void transferMoney_missingAmount() throws Exception {

    this.mockMvc
            .perform(
                    patch("/v1/accounts/transferMoney")
                            .param("sourceAccountId", "source")
                            .param("destinationAccountId", "destination")
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
            )
            .andExpect(status().isBadRequest());
  }

  @Test
  public void transferMoney_wrongContentType() throws Exception {

    this.mockMvc
            .perform(
                    patch("/v1/accounts/transferMoney")
                            .param("sourceAccountId", "source")
                            .param("destinationAccountId", "destination")
                            .param("amount", "1")
                            .contentType(MediaType.APPLICATION_XML_VALUE)
            )
            .andExpect(status().isBadRequest());
  }

  @Test
  public void transferMoney_blankSourceAccountId() throws Exception {

    assertOutcome(
            "  \t  ",
            "destination",
            "1",
            HttpStatus.BAD_REQUEST,
            "Failed to transfer amount '.*' from account '.*' to account '.*'."
    );
  }

  @Test
  public void transferMoney_blankDestinationAccountId() throws Exception {

    assertOutcome(
            "source",
            "  \t  ",
            "1",
            HttpStatus.BAD_REQUEST,
            "Failed to transfer amount '.*' from account '.*' to account '.*'."
    );
  }

  @Test
  public void transferMoney_amountIsZero() throws Exception {

    assertOutcome(
            "source",
            "destination",
            "0",
            HttpStatus.BAD_REQUEST,
            "Failed to transfer amount '.*' from account '.*' to account '.*'."
    );
  }

  @Test
  public void transferMoney_amountIsNegative() throws Exception {

    assertOutcome(
            "source",
            "destination",
            "-1",
            HttpStatus.BAD_REQUEST,
            "Failed to transfer amount '.*' from account '.*' to account '.*'."
    );
  }

  @Test
  public void transferMoney_sourceAccountDoesNotExist() throws Exception {

    final String destinationAccountId = "destination";

    this.mockMvc
            .perform(
                    post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"accountId\":\"" + destinationAccountId + "\",\"balance\":1000}")
            )
            .andExpect(status().isCreated());

    assertOutcome(
            "source",
            destinationAccountId,
            "1",
            HttpStatus.BAD_REQUEST,
            "Failed to transfer amount '.*' from account '.*' to account '.*'."
    );
  }

  @Test
  public void transferMoney_destinationAccountDoesNotExist() throws Exception {

    final String sourceAccountId = "source";

    this.mockMvc
            .perform(
                    post("/v1/accounts").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"accountId\":\"" + sourceAccountId + "\",\"balance\":1000}")
            )
            .andExpect(status().isCreated());

    assertOutcome(
            sourceAccountId,
            "destination",
            "1",
            HttpStatus.BAD_REQUEST,
            "Failed to transfer amount '.*' from account '.*' to account '.*'."
    );
  }

  private void assertOutcome(
          final String sourceAccountId,
          final String destinationAccountId,
          final String amount,
          final HttpStatus expectedStatus,
          final String expectedContentPattern)
          throws Exception {

    final String content =
            this.mockMvc
                    .perform(
                            patch("/v1/accounts/transferMoney")
                                    .param("sourceAccountId", sourceAccountId)
                                    .param("destinationAccountId", destinationAccountId)
                                    .param("amount", amount)
                                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    )
                    .andExpect(status().is(expectedStatus.value()))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

    assertThat(content).matches(expectedContentPattern);
  }
}
