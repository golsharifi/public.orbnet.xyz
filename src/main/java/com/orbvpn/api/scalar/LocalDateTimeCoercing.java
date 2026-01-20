package com.orbvpn.api.scalar;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;

public class LocalDateTimeCoercing implements Coercing<LocalDateTime, String> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    @Override
    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if (dataFetcherResult instanceof LocalDateTime) {
            return ((LocalDateTime) dataFetcherResult).format(FORMATTER);
        }
        throw new CoercingSerializeException("Expected a LocalDateTime object.");
    }

    @Override
    public LocalDateTime parseValue(Object input) throws CoercingParseValueException {
        try {
            if (input instanceof String) {
                String dateStr = (String) input;
                if (dateStr.endsWith("Z")) {
                    // Handle UTC/Zulu time
                    return ZonedDateTime.parse(dateStr)
                            .withZoneSameInstant(ZoneOffset.UTC)
                            .toLocalDateTime();
                }
                return LocalDateTime.parse(dateStr, FORMATTER);
            }
            throw new CoercingParseValueException("Expected a String");
        } catch (Exception e) {
            throw new CoercingParseValueException(
                    String.format("Not a valid date time: '%s'.", input), e);
        }
    }

    @Override
    public LocalDateTime parseLiteral(Object input) throws CoercingParseLiteralException {
        if (input instanceof StringValue) {
            return parseValue(((StringValue) input).getValue());
        }
        throw new CoercingParseLiteralException("Expected AST type 'StringValue'.");
    }
}