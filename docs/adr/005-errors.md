# ADR-005: Унификация формата ошибок и correlation ID

## Контекст

Поведение Spring Boot по умолчанию приводит к нескольким несовместимым форматам ответов при ошибках:

- Ответы 4xx из MVC возвращаются в формате `{"timestamp","status","error","path"}` — без машиночитаемого кода и без сообщения об ошибке.
- Ответы 401 от Spring Security возвращаются с пустым телом (`Content-Length: 0`).
- Необработанные исключения приводят к ответу 500 в формате, зависящем от конфигурации.

Для клиента и для интеграционных тестов требуется единый формат ответа, содержащий машиночитаемый код ошибки и correlation ID для сопоставления с логами.

## Решение

### Формат

Все ответы 4xx и 5xx возвращаются в единой структуре `ErrorDTO`:

```json
{
  "timestamp": "2026-10-30T12:00:00Z",
  "status": 409,
  "error": "CONFLICT",
  "code": "CONFLICT",
  "message": "Room is already booked for these dates",
  "path": "/bookings",
  "traceId": "abc123def456"
}
```

Поля:
- `status` — HTTP-статус.
- `error` — стандартное имя статуса (`NOT_FOUND`, `CONFLICT`, ...).
- `code` — машиночитаемый код ошибки (`ErrorCode` enum), позволяющий клиенту различать семантически разные ошибки с одинаковым HTTP-статусом.
- `message` — человекочитаемое описание.
- `traceId` — correlation ID для сопоставления с логами.

### Реализация

Компоненты сосредоточены в модуле `common`:

| Компонент | Роль |
|---|---|
| `ErrorDTO` | DTO с фиксированным набором полей |
| `ErrorCode` | Enum машиночитаемых кодов |
| `GlobalExceptionHandler` | `@RestControllerAdvice` для servlet-стека |
| `JsonSecurityErrorHandler` | `AuthenticationEntryPoint` + `AccessDeniedHandler` для 401/403 |

`GlobalExceptionHandler` обрабатывает:
- `ResponseStatusException` — базовый механизм возврата специфичных ошибок из сервисов.
- `MethodArgumentNotValidException` — ошибки валидации JSR-380 → `400` с перечнем полей.
- `IllegalArgumentException` → `400`.
- `Exception` (fallback) → `500` с логированием stack trace на сервере.

`JsonSecurityErrorHandler` реализует `AuthenticationEntryPoint` (для 401) и `AccessDeniedHandler` (для 403). Он регистрируется в `SecurityConfig` в двух точках:
- `.exceptionHandling(...)` — для ошибок на уровне фильтров;
- `.oauth2ResourceServer(...).authenticationEntryPoint(...)` — для ошибок отсутствия/валидности JWT.

Дублирование обязательно: `ExceptionTranslationFilter` вызывает `entryPoint` из `oauth2ResourceServer` до того, как запрос дойдёт до `DispatcherServlet`, поэтому `@RestControllerAdvice` не может его перехватить.

### Gateway (WebFlux)

В реактивном стеке `@RestControllerAdvice` не применяется. Реализованы собственные компоненты:
- `GatewaySecurityErrorHandler` — `ServerAuthenticationEntryPoint` + `ServerAccessDeniedHandler`.
- `GatewayExceptionHandler` — `ErrorWebExceptionHandler`.

Оба возвращают `ErrorDTO`-совместимую структуру.

### Замечание о конфигурации Spring Security

По умолчанию Spring MVC при возникновении исключения выполняет **внутренний forward на `/error`**. Если путь `/error` не разрешён в `SecurityFilterChain`, `AuthorizationFilter` перехватывает forward и возвращает `403` вместо исходного статуса (400/409/500). Это приводит к маскировке реальных ошибок.

Во всех `SecurityConfig` явно указано:
```java
.requestMatchers("/error", "/error/**", ...).permitAll()
```

Без этой настройки все ошибки приложения выглядели бы как `403`.

## Рассмотренные альтернативы

### `ProblemDetail` (RFC 7807)

Стандартный формат описания ошибок в HTTP API. Spring Boot 3 предоставляет встроенную поддержку.

Отклонено:
- Поле `code` (машиночитаемый идентификатор) не предусмотрено стандартом — потребовалось бы введение расширения.
- Требует отдельной настройки сериализации для каждого типа исключений.
- Для данного проекта собственный DTO с явным набором полей проще и предоставляет больше контроля.

Для публичного API, ориентированного на внешних потребителей, `ProblemDetail` был бы предпочтителен — это стандарт, который снижает порог интеграции.

### Локальная реализация в каждом сервисе

Каждый сервис содержит собственный `@RestControllerAdvice`.

Отклонено:
- Дублирование кода (DRY).
- Риск расхождения форматов при эволюции.
- Централизованное решение в `common` гарантирует единообразие.

### Использование только `ResponseStatusException`

Возврат ошибок через `ResponseStatusException` без `@RestControllerAdvice`.

Отклонено:
- Spring по умолчанию возвращает стандартный формат `DefaultErrorAttributes` без поля `code`.
- Требует настройки `ErrorAttributes` — по сути, тот же объём работы, но с меньшей гибкостью.

## Последствия

### Положительные

- Клиент получает единый формат ответов при любых ошибках.
- Машиночитаемые коды позволяют реализовать логику обработки на клиенте (например, при `code == "ROOM_NOT_AVAILABLE"` показать альтернативные даты).
- `traceId` в теле ответа позволяет пользователю или разработчику сопоставить ошибку с записью в логах.
- Ошибки 401/403 из Security больше не возвращаются с пустым телом.

### Отрицательные

- Дублирование обработчиков для servlet- и WebFlux-стека (неизбежно: интерфейсы `@RestControllerAdvice` и `ErrorWebExceptionHandler` несовместимы).
- Требуется явная настройка `/error` в `permitAll` в каждом сервисе. Пропуск этой настройки приводит к маскировке всех ошибок под 403 — это неочевидное поведение, требующее документирования.
- Поле `traceId` на `gateway` заполняется только для ошибок бизнес-логики. Для ошибок на уровне Security (`401` до вызова `TraceIdFilter`) поле остаётся `null` — техническое ограничение текущей реализации. Для устранения потребовалось бы генерировать `traceId` внутри `GatewaySecurityErrorHandler`.

## Ссылки

- [RFC 7807: Problem Details for HTTP APIs](https://datatracker.ietf.org/doc/html/rfc7807)
- [Spring Boot: Error Handling](https://docs.spring.io/spring-boot/docs/current/reference/html/web.html#web.servlet.spring-mvc.error-handling)
