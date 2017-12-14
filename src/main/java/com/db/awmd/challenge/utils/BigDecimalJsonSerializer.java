package com.db.awmd.challenge.utils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.BigDecimal;

public final class BigDecimalJsonSerializer extends JsonSerializer<BigDecimal> {

    @Override
    public void serialize(final BigDecimal value, final JsonGenerator gen, final SerializerProvider serializers) throws IOException {

        if (value == null) {
            gen.writeNull();
        } else {
            gen.writeString(value.toPlainString());
        }
    }
}
