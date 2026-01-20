package com.orbvpn.api.scalar;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class LocalDateCoercing implements Coercing<LocalDate, String> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if (dataFetcherResult instanceof LocalDate) {
            return ((LocalDate) dataFetcherResult).format(FORMATTER);
        }
        throw new CoercingSerializeException("Expected LocalDate object.");
    }

    @Override
    public LocalDate parseValue(Object input) throws CoercingParseValueException {
        try {
            if (input instanceof String) {
                return LocalDate.parse((String) input, FORMATTER);
            }
            throw new CoercingParseValueException("Expected a String");
        } catch (Exception e) {
            throw new CoercingParseValueException(
                    String.format("Not a valid date: '%s'.", input), e);
        }
    }

    @Override
    public LocalDate parseLiteral(Object input) throws CoercingParseLiteralException {
        if (input instanceof StringValue) {
            try {
                return LocalDate.parse(((StringValue) input).getValue(), FORMATTER);
            } catch (Exception e) {
                throw new CoercingParseLiteralException(e);
            }
        }
        throw new CoercingParseLiteralException("Expected AST type 'StringValue'.");
    }
}