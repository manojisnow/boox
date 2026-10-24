package com.example.chatapp.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Makes migration a prerequisite of ORM validation, including imported JPA test slices. */
@Configuration(proxyBeanMethods = false)
public class SchemaMigrationConfiguration {
  @Bean
  public Boolean ullavoxSchemaMigration(DataSource dataSource) {
    SchemaMigrator.migrate(dataSource);
    return Boolean.TRUE;
  }

  @Bean
  public static BeanFactoryPostProcessor migrationBeforeJpa() {
    return factory -> {
      if (factory.containsBeanDefinition("entityManagerFactory")) {
        var definition = factory.getBeanDefinition("entityManagerFactory");
        var dependencies = new java.util.ArrayList<String>();
        String[] previous = definition.getDependsOn();
        if (previous != null) java.util.Collections.addAll(dependencies, previous);
        dependencies.add("ullavoxSchemaMigration");
        definition.setDependsOn(dependencies.toArray(String[]::new));
      }
    };
  }
}
