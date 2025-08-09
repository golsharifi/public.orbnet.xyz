package com.orbvpn.api.config;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import graphql.scalars.ExtendedScalars;

@Configuration
public class GraphQLScalarConfig {

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder
                .scalar(jsonScalar())
                .scalar(localDateTimeScalar())
                .scalar(localDateScalar())
                .scalar(dateScalar())
                .scalar(dateTimeScalar())
                .scalar(bigDecimalScalar())
                .scalar(bigIntegerScalar())
                .scalar(uploadScalar());
    }

    @Bean
    public GraphQLScalarType uploadScalar() {
        return GraphQLScalarType.newScalar()
                .name("Upload")
                .description("A file upload in a multipart request")
                .coercing(new UploadCoercing())
                .build();
    }

    private GraphQLScalarType jsonScalar() {
        return ExtendedScalars.Json;
    }

    private GraphQLScalarType bigDecimalScalar() {
        return GraphQLScalarType.newScalar()
                .name("BigDecimal")
                .description("Java BigDecimal type")
                .coercing(new Coercing<BigDecimal, String>() {
                    @Override
                    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
                        if (dataFetcherResult instanceof BigDecimal) {
                            return dataFetcherResult.toString();
                        } else {
                            throw new CoercingSerializeException("Expected a BigDecimal object.");
                        }
                    }

                    @Override
                    public BigDecimal parseValue(Object input) throws CoercingParseValueException {
                        try {
                            if (input instanceof String) {
                                return new BigDecimal((String) input);
                            } else if (input instanceof BigDecimal) {
                                return (BigDecimal) input;
                            } else if (input instanceof Double) {
                                return BigDecimal.valueOf((Double) input);
                            } else if (input instanceof Integer) {
                                return BigDecimal.valueOf((Integer) input);
                            } else if (input instanceof Long) {
                                return BigDecimal.valueOf((Long) input);
                            } else {
                                throw new CoercingParseValueException("Expected a String or Number");
                            }
                        } catch (NumberFormatException e) {
                            throw new CoercingParseValueException("Not a valid BigDecimal", e);
                        }
                    }

                    @Override
                    public BigDecimal parseLiteral(Object input) throws CoercingParseLiteralException {
                        if (input instanceof StringValue) {
                            try {
                                return new BigDecimal(((StringValue) input).getValue());
                            } catch (NumberFormatException e) {
                                throw new CoercingParseLiteralException("Not a valid BigDecimal", e);
                            }
                        }
                        throw new CoercingParseLiteralException("Expected a StringValue.");
                    }
                }).build();
    }

    private GraphQLScalarType bigIntegerScalar() {
        return GraphQLScalarType.newScalar()
                .name("BigInteger")
                .description("Java BigInteger type")
                .coercing(new Coercing<BigInteger, String>() {
                    @Override
                    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
                        if (dataFetcherResult instanceof BigInteger) {
                            return dataFetcherResult.toString();
                        } else {
                            throw new CoercingSerializeException("Expected a BigInteger object.");
                        }
                    }

                    @Override
                    public BigInteger parseValue(Object input) throws CoercingParseValueException {
                        try {
                            if (input instanceof String) {
                                return new BigInteger((String) input);
                            } else if (input instanceof BigInteger) {
                                return (BigInteger) input;
                            } else if (input instanceof Integer) {
                                return BigInteger.valueOf((Integer) input);
                            } else if (input instanceof Long) {
                                return BigInteger.valueOf((Long) input);
                            } else {
                                throw new CoercingParseValueException("Expected a String or Number");
                            }
                        } catch (NumberFormatException e) {
                            throw new CoercingParseValueException("Not a valid BigInteger", e);
                        }
                    }

                    @Override
                    public BigInteger parseLiteral(Object input) throws CoercingParseLiteralException {
                        if (input instanceof StringValue) {
                            try {
                                return new BigInteger(((StringValue) input).getValue());
                            } catch (NumberFormatException e) {
                                throw new CoercingParseLiteralException("Not a valid BigInteger", e);
                            }
                        }
                        throw new CoercingParseLiteralException("Expected a StringValue.");
                    }
                }).build();
    }

    private GraphQLScalarType localDateTimeScalar() {
        return GraphQLScalarType.newScalar()
                .name("LocalDateTime")
                .description("Java LocalDateTime type")
                .coercing(new Coercing<LocalDateTime, String>() {
                    @Override
                    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
                        if (dataFetcherResult instanceof LocalDateTime) {
                            return ((LocalDateTime) dataFetcherResult).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        } else {
                            throw new CoercingSerializeException("Expected a LocalDateTime object.");
                        }
                    }

                    @Override
                    public LocalDateTime parseValue(Object input) throws CoercingParseValueException {
                        try {
                            if (input instanceof String) {
                                return LocalDateTime.parse((String) input, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                            }
                            throw new CoercingParseValueException("Expected a String");
                        } catch (Exception e) {
                            throw new CoercingParseValueException("Not a valid LocalDateTime", e);
                        }
                    }

                    @Override
                    public LocalDateTime parseLiteral(Object input) throws CoercingParseLiteralException {
                        if (input instanceof StringValue) {
                            try {
                                return LocalDateTime.parse(((StringValue) input).getValue(),
                                        DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                            } catch (Exception e) {
                                throw new CoercingParseLiteralException("Not a valid LocalDateTime", e);
                            }
                        }
                        throw new CoercingParseLiteralException("Expected a StringValue.");
                    }
                }).build();
    }

    private GraphQLScalarType localDateScalar() {
        return GraphQLScalarType.newScalar()
                .name("LocalDate")
                .description("Java LocalDate type")
                .coercing(new Coercing<LocalDate, String>() {
                    @Override
                    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
                        if (dataFetcherResult instanceof LocalDate) {
                            return dataFetcherResult.toString();
                        } else {
                            throw new CoercingSerializeException("Expected a LocalDate object.");
                        }
                    }

                    @Override
                    public LocalDate parseValue(Object input) throws CoercingParseValueException {
                        try {
                            if (input instanceof String) {
                                return LocalDate.parse((String) input);
                            }
                            throw new CoercingParseValueException("Expected a String");
                        } catch (Exception e) {
                            throw new CoercingParseValueException("Not a valid LocalDate", e);
                        }
                    }

                    @Override
                    public LocalDate parseLiteral(Object input) throws CoercingParseLiteralException {
                        if (input instanceof StringValue) {
                            try {
                                return LocalDate.parse(((StringValue) input).getValue());
                            } catch (Exception e) {
                                throw new CoercingParseLiteralException("Not a valid LocalDate", e);
                            }
                        }
                        throw new CoercingParseLiteralException("Expected a StringValue.");
                    }
                }).build();
    }

    private GraphQLScalarType dateScalar() {
        return GraphQLScalarType.newScalar()
                .name("Date")
                .description("Java Date type")
                .coercing(new Coercing<Date, String>() {
                    @Override
                    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
                        if (dataFetcherResult instanceof Date) {
                            return ((Date) dataFetcherResult).toInstant().toString();
                        } else {
                            throw new CoercingSerializeException("Expected a Date object.");
                        }
                    }

                    @Override
                    public Date parseValue(Object input) throws CoercingParseValueException {
                        try {
                            if (input instanceof String) {
                                return new Date(Long.parseLong((String) input));
                            } else if (input instanceof Integer) {
                                return new Date(((Integer) input).longValue());
                            } else if (input instanceof Long) {
                                return new Date((Long) input);
                            }
                            throw new CoercingParseValueException("Expected a String or Number");
                        } catch (Exception e) {
                            throw new CoercingParseValueException("Not a valid Date", e);
                        }
                    }

                    @Override
                    public Date parseLiteral(Object input) throws CoercingParseLiteralException {
                        if (input instanceof StringValue) {
                            try {
                                return new Date(Long.parseLong(((StringValue) input).getValue()));
                            } catch (Exception e) {
                                throw new CoercingParseLiteralException("Not a valid Date", e);
                            }
                        }
                        throw new CoercingParseLiteralException("Expected a StringValue.");
                    }
                }).build();
    }

    private GraphQLScalarType dateTimeScalar() {
        return GraphQLScalarType.newScalar()
                .name("DateTime")
                .description("ISO-8601 DateTime")
                .coercing(new Coercing<Object, String>() {
                    @Override
                    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
                        if (dataFetcherResult instanceof LocalDateTime) {
                            return ((LocalDateTime) dataFetcherResult).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        } else if (dataFetcherResult instanceof Date) {
                            return ((Date) dataFetcherResult).toInstant().toString();
                        } else {
                            throw new CoercingSerializeException("Expected a Date or LocalDateTime object.");
                        }
                    }

                    @Override
                    public Object parseValue(Object input) throws CoercingParseValueException {
                        try {
                            if (input instanceof String) {
                                try {
                                    return LocalDateTime.parse((String) input, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                                } catch (Exception e) {
                                    // If LocalDateTime parsing fails, try Date parsing
                                    return new Date(Long.parseLong((String) input));
                                }
                            } else if (input instanceof Long) {
                                return new Date((Long) input);
                            }
                            throw new CoercingParseValueException("Expected a String or Long value");
                        } catch (Exception e) {
                            throw new CoercingParseValueException("Not a valid DateTime", e);
                        }
                    }

                    @Override
                    public Object parseLiteral(Object input) throws CoercingParseLiteralException {
                        if (input instanceof StringValue) {
                            String value = ((StringValue) input).getValue();
                            try {
                                return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                            } catch (Exception e) {
                                try {
                                    return new Date(Long.parseLong(value));
                                } catch (Exception ex) {
                                    throw new CoercingParseLiteralException("Not a valid DateTime", ex);
                                }
                            }
                        }
                        throw new CoercingParseLiteralException("Expected a StringValue.");
                    }
                }).build();
    }

    public static class UploadCoercing implements Coercing<Object, Object> {
        @Override
        public Object serialize(Object dataFetcherResult) {
            throw new CoercingSerializeException("Upload scalar cannot be serialized");
        }

        @Override
        public Object parseValue(Object input) {
            return input;
        }

        @Override
        public Object parseLiteral(Object input) {
            throw new CoercingParseLiteralException(
                    "Upload scalar can only be used as an input type");
        }
    }
}