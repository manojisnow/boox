package com.example.chatapp.persistence;

import org.hibernate.community.dialect.SQLiteDialect;
import org.hibernate.community.dialect.identity.SQLiteIdentityColumnSupport;
import org.hibernate.dialect.identity.IdentityColumnSupport;

/** Preserves SQLite INTEGER PRIMARY KEY generation with Hibernate 7's schema exporter. */
public class UllavoxSqliteDialect extends SQLiteDialect {
  @Override
  public IdentityColumnSupport getIdentityColumnSupport() {
    return new SQLiteIdentityColumnSupport() {
      @Override
      public boolean hasDataTypeInIdentityColumn() {
        return true;
      }

      @Override
      public String getIdentityColumnString(final int type) {
        // The entity's explicit integer columnDefinition supplies SQLite's required type.
        // Hibernate 7 checks the entire table DDL for the identity clause; using "integer"
        // here can omit it when a preceding, unrelated integer column already exists.
        return "";
      }
    };
  }
}
