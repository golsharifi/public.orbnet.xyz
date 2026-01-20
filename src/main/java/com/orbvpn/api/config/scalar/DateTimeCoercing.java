package com.orbvpn.api.config.scalar;

import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateTimeCoercing implements Coercing<LocalDateTime, String> {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

    @Override
    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if (dataFetcherResult instanceof LocalDateTime) {
            return ((LocalDateTime) dataFetcherResult).format(formatter);
        }
        throw new CoercingSerializeException("Expected LocalDateTime object.");
    }

    @Override
    public LocalDateTime parseValue(Object input) throws CoercingParseValueException {
        try {
            if (input instanceof String) {
                return LocalDateTime.parse((String) input, formatter);
            }
            throw new CoercingParseValueException("Expected a String");
        } catch (Exception e) {
            throw new CoercingParseValueException(
                    String.format("Not a valid date: '%s'.", input), e);
        }
    }

    @Override
    public LocalDateTime parseLiteral(Object input) throws CoercingParseLiteralException {
        if (!(input instanceof String)) {
            throw new CoercingParseLiteralException("Expected AST type 'StringValue'.");
        }
        try {
            return LocalDateTime.parse((String) input, formatter);
        } catch (Exception e) {
            throw new CoercingParseLiteralException(e);
        }
    }
}