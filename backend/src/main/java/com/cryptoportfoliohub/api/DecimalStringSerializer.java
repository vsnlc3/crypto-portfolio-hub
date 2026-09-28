package com.cryptoportfoliohub.api;

import java.math.BigDecimal;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/** Serializes financial decimals as plain base-10 JSON strings. */
public final class DecimalStringSerializer extends ValueSerializer<BigDecimal> {

    @Override
    public void serialize(BigDecimal value, JsonGenerator generator, SerializationContext context)
            throws JacksonException {
        generator.writeString(value.toPlainString());
    }
}
