package com.orbvpn.api.config;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

import java.math.BigDecimal;

public class BigDecimalCoercing implements Coercing<BigDecimal, BigDecimal> {

    @Override
    public BigDecimal serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if (dataFetcherResult instanceof BigDecimal) {
            return (BigDecimal) dataFetcherResult;
        } else {
            throw new CoercingSerializeException("Expected a BigDecimal object.");
        }
    }

    @Override
    public BigDecimal parseValue(Object input) throws CoercingParseValueException {
        try {
            if (input instanceof BigDecimal) {
                return (BigDecimal) input;
            } else if (input instanceof String) {
                return new BigDecimal((String) input);
            } else if (input instanceof Integer) {
                return new BigDecimal((Integer) input);
            } else if (input instanceof Long) {
                return new BigDecimal((Long) input);
            } else if (input instanceof Float) {
                return new BigDecimal((Float) input);
            } else if (input instanceof Double) {
                return BigDecimal.valueOf((Double) input);
            } else {
                throw new CoercingParseValueException("Unknown input type for BigDecimal");
            }
        } catch (NumberFormatException e) {
            throw new CoercingParseValueException("Invalid BigDecimal value: " + input, e);
        }
    }

    @Override
    public BigDecimal parseLiteral(Object input) throws CoercingParseLiteralException {
        if (input instanceof StringValue) {
            try {
                return new BigDecimal(((StringValue) input).getValue());
            } catch (NumberFormatException e) {
                throw new CoercingParseLiteralException("Invalid BigDecimal value: " + input, e);
            }
        }
        throw new CoercingParseLiteralException("Expected a StringValue.");
    }
}
