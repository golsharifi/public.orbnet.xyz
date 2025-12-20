package com.orbvpn.api.scalar;

import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

import org.springframework.web.multipart.MultipartFile;

public class UploadCoercing implements Coercing<MultipartFile, Void> {

    @Override
    public Void serialize(Object dataFetcherResult) throws CoercingSerializeException {
        throw new CoercingSerializeException("Upload serialization is not supported.");
    }

    @Override
    public MultipartFile parseValue(Object input) throws CoercingParseValueException {
        if (input instanceof MultipartFile) {
            return (MultipartFile) input;
        }
        throw new CoercingParseValueException("Expected a MultipartFile object.");
    }

    @Override
    public MultipartFile parseLiteral(Object input) throws CoercingParseLiteralException {
        throw new CoercingParseLiteralException("Parsing literals is not supported for Upload scalar.");
    }
}