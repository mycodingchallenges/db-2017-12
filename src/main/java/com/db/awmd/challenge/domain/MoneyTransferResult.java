package com.db.awmd.challenge.domain;

import com.db.awmd.challenge.utils.BigDecimalJsonSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Value;
import org.hibernate.validator.constraints.NotBlank;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * IMPORTANT:
 * By default, {@link BigDecimal} fields are serialized as {@code double} values.
 * Precision is lost when using {@code double} or {@code float}.
 * Therefore, {@link BigDecimal} fields must be serialized as strings.
 */
@Value
public class MoneyTransferResult {

    @NotBlank
    String sourceAccountId;

    @NotBlank
    String destinationAccountId;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    BigDecimal amount;

    @NotNull
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    BigDecimal sourceAccountBalanceNew;

    @NotNull
    @JsonSerialize(using = BigDecimalJsonSerializer.class)
    BigDecimal destinationAccountBalanceNew;
}
