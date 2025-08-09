package com.orbvpn.api.service.subscription.transaction;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class TransactionConfig {

    @Bean
    @Qualifier("readOnlyTransactionTemplate")
    public TransactionTemplate readOnlyTransactionTemplate(
            PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(true);
        return template;
    }

    @Bean
    @Qualifier("writeTransactionTemplate")
    public TransactionTemplate writeTransactionTemplate(
            PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }
}