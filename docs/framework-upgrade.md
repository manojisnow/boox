# Framework upgrade

The upgrade uses current stable framework versions and supported LTS runtimes.
Java 25 LTS is the deployment baseline; Java 27 is newer but is outside Spring
Boot 4.1.1's documented Java 17–26 compatibility range. Node 24 LTS replaces
Node 20, which is no longer supported. Use `nvm use` in `frontend/`.

| Component | Previous | Upgraded |
| --- | --- | --- |
| Java | 21 | 25 LTS |
| Spring Boot | 3.5.3 | 4.1.1 |
| Spring Framework | 6.2.8 | 7.0.9 (Boot managed) |
| Hibernate | 6.6 | 7.4.5 (Boot managed) |
| Embedded Tomcat | 10.1.42 | 11.0.26 |
| Jackson | 2.19.1 | 3.2.3 |
| SQLite JDBC | 3.49.1.0 | 3.53.4.0 |
| Node build runtime | 20 | 24 LTS (Docker 24.21.0) |
| React | 19.2 | 19.3.0 |
| Axios | 1.18.1 | 1.20.0 |
| Vite | 8.1 | 8.3.4 |
| Vitest | 4.1 | 5.0.3 |
| Docker Maven | 3.9.10 | 3.10.0 |
| Container OS | Ubuntu 22.04 | Ubuntu 24.04 |

## Compatibility changes

- Spring Boot 4's web MVC and JPA test starters replace the former monolithic
  test auto-configuration imports. Testcontainers 2 uses renamed artifacts.
- JSON code uses `tools.jackson` packages and catches `JacksonException`.
  Jackson 3 exceptions are unchecked. Spring 7 HTTP headers use `HttpHeaders`.
- Tomcat and Jackson are explicitly managed as aligned families in the parent
  POM because their security fixes are newer than Boot's managed versions.
  Review these overrides when upgrading Boot again.
- SQLite requires `INTEGER PRIMARY KEY` for identity columns. Hibernate 7's
  schema exporter can omit the identity type if another integer column appears
  earlier in the table. `UllavoxSqliteDialect` and explicit integer ID column
  definitions preserve identity generation for both messages and agent steps.
  Keep this workaround until an upstream fix is verified against fresh and
  existing databases. Existing tables do not need to be recreated.
- Google Java Format 1.36 is the latest tested compatible version. Version 1.37
  changed its Style API and fails with Spotless 3.10.4. Formatting, coverage and
  bug detection remain enforced. Checkstyle's JavadocMethod property is updated
  from `scope` to `accessModifiers` with the same public-method scope.
- SQLite JDBC's native access is explicitly enabled for Java 25 application
  startup. Contract tests use a separate test database, never the user's DB.

## Validation and rollback

Run `mvn -f backend/pom.xml clean verify` with Java 25 and
`npm ci && npm test -- --coverage && npm run build` in `frontend/` with Node 24.
Build the release image with `docker build --pull -t ullavox .`.

Before using an existing database, take a SQLite backup (including committed WAL
contents, using SQLite's backup API or `.backup`). Test the upgrade against a
copy first. Validate existing history, ordinary streaming chat, file-based agent
follow-ups, attachment clearing and web research in the running app before PR.
Keep the previous image and database backup until the upgrade is accepted. To
roll back, stop the new app, restore the backup, and start the previous image.

The upgrade does not change Ullavox's authentication or network exposure. The
unauthenticated API and Compose ports bound to all interfaces remain a separate
security concern before a release intended for untrusted networks.

## References

- [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Node release schedule](https://github.com/nodejs/Release)
