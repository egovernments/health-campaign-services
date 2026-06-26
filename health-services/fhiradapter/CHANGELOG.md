# Changelog
All notable changes to this module will be documented in this file.

## 1.0.1 - 2026-06-17
- Upgraded to Java 25
- Upgraded Spring Boot to 3.4.4
- Bumped Lombok to 1.18.46 and added explicit Lombok annotation processor path (required on JDK 23+)
- Removed redundant `@Autowired` on `@Bean` methods (rejected by Spring Boot 3.4)

