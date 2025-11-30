# FinTrack — Документация проекта

## Общее описание

**FinTrack** — веб-приложение для персонального учёта финансов. Состоит из:
- **Backend**: Spring Boot 3.2.4 (Java 17)
- **Frontend**: Vue 3 + TypeScript + Vite
- **БД**: SQLite (файл `./data/fintrack.db`)

---

# BACKEND (Spring Boot)

## Структура пакетов

```
com.acs.finance/
├── FinanceApplication.java     # Точка входа
├── config/                     # Конфигурация
├── controller/                 # REST контроллеры
├── entity/                     # JPA сущности
├── exception/                  # Обработка ошибок
├── model/                      # DTO/модели
├── repository/                 # JPA репозитории
├── scheduler/                  # Планировщики
└── service/                    # Бизнес-логика
```

---

## Файлы Backend

### `FinanceApplication.java`
**Назначение**: Точка входа Spring Boot приложения.  
- Аннотация `@EnableScheduling` включает планировщики (для напоминаний).

---

## Config

### `WebConfig.java`
**Назначение**: Настройка веб-конфигурации.

| Метод | Описание |
|-------|----------|
| `addResourceHandlers()` | Настраивает раздачу статики из `web/public/` |
| `addCorsMappings()` | CORS для `/api/**` — разрешает все origins, credentials |

---

## Controllers

### `BaseController.java`
**Назначение**: Абстрактный базовый контроллер.

| Метод | Описание |
|-------|----------|
| `getSessionId(request)` | Извлекает SID из cookie |
| `getUser(request)` | Возвращает пользователя по сессии |
| `requireAuth(request)` | Требует авторизацию, бросает `UnauthorizedException` |
| `escapeJson(s)` | Экранирование для JSON |

---

### `AuthController.java`
**Назначение**: Аутентификация пользователей.

| Endpoint | Метод | Описание |
|----------|-------|----------|
| `/api/register` | POST | Регистрация (username, password) |
| `/api/login` | POST | Вход, создаёт сессию, ставит cookie SID |
| `/api/logout` | POST | Выход, удаляет сессию |
| `/api/me` | GET | Текущий пользователь по сессии |

**Взаимодействие**: Использует `AuthService` для работы с пользователями и сессиями.

---

### `TransactionController.java`
**Назначение**: CRUD транзакций (доходы/расходы).

| Endpoint | Метод | Описание |
|----------|-------|----------|
| `/api/transaction/add` | POST | Добавить транзакцию (date, amount, category, description) |
| `/api/transactions` | GET | Список транзакций с фильтрами (from, to, category) |
| `/api/transaction/delete` | POST | Удалить транзакцию по id |

**Особенности**:
- Автокатегоризация через `AutoCategorizerService` если категория не указана
- Отправка SSE-событий (`tx-added`, `budget-update`, `alert` при превышении бюджета)
- Обновление поля `spent` в бюджетах при расходах

---

### `BudgetController.java`
**Назначение**: Управление лимитами бюджета по категориям.

| Endpoint | Метод | Описание |
|----------|-------|----------|
| `/api/budget/set` | POST | Установить лимит для категории |
| `/api/budget` | GET | Список бюджетов пользователя (category, limit, spent) |
| `/api/budget/delete` | POST | Удалить бюджет категории |

**Взаимодействие**: `FinanceService`, `SseService` для уведомлений.

---

### `ReminderController.java`
**Назначение**: Напоминания о платежах.

| Endpoint | Метод | Описание |
|----------|-------|----------|
| `/api/reminder/add` | POST | Добавить напоминание (dueDate, message, amount) |
| `/api/reminders` | GET | Список напоминаний пользователя |

---

### `GroupController.java`
**Назначение**: Групповой бюджет — несколько пользователей в одной группе.

| Endpoint | Метод | Описание |
|----------|-------|----------|
| `/api/group/create` | POST | Создать группу |
| `/api/group/join` | POST | Присоединиться к группе по groupId или invite-ссылке |
| `/api/group/leave` | POST | Покинуть группу |
| `/api/group/me` | GET | Информация о группе текущего пользователя |
| `/api/group/budget` | GET | Агрегированный бюджет группы (сумма лимитов × share) |
| `/api/group/peers` | GET | Участники группы с их доходами/расходами |

**Особенности**:
- `normalizeGroupId()` — парсит ссылки-приглашения `?join=<id>`
- Каждый участник имеет `share` (долю) от 0 до 1

---

### `CurrencyController.java`
**Назначение**: Курсы валют и конвертация.

| Endpoint | Метод | Описание |
|----------|-------|----------|
| `/api/currency` | GET | Курсы (base=MDL, symbols=USD,EUR,RUB) |
| `/api/currency/convert` | GET | Конвертация (from, to, amount, date) |

**Взаимодействие**: `CurrencyService` — получает курсы с exchangerate.host API.

---

### `CategorizerController.java`
**Назначение**: Автоматическое определение категории.

| Endpoint | Метод | Описание |
|----------|-------|----------|
| `/api/categorizer/suggest` | GET | Предлагает категорию по описанию |

---

### `SseController.java`
**Назначение**: Server-Sent Events для real-time уведомлений.

| Endpoint | Метод | Описание |
|----------|-------|----------|
| `/api/events` | GET | SSE endpoint, возвращает `SseEmitter` |

**События**:
- `hello` — при подключении
- `tx-added` — добавлена транзакция
- `budget-update` — изменён бюджет
- `reminder` — напоминание
- `alert` — превышение бюджета

---

### `HealthController.java`
**Назначение**: Health-check эндпоинты.

| Endpoint | Описание |
|----------|----------|
| `/health` | Возвращает "OK" |
| `/health/db` | Проверяет соединение с БД |

---

## Services

### `AuthService.java`
**Назначение**: Аутентификация, сессии, кэширование пользователей.

| Метод | Описание |
|-------|----------|
| `register()` | Регистрация, хеширование пароля (SHA-256 + Base64) |
| `login()` | Проверка credentials |
| `createSession()` | Создаёт сессию, сохраняет в БД и in-memory кэш |
| `destroySession()` | Удаляет сессию |
| `getUserBySession()` | Получает пользователя по SID (сначала кэш, потом БД) |
| `getUserById()` | Получает пользователя по ID |

**Кэширование**:
- `userCache: ConcurrentHashMap<username, User>` — кэш пользователей
- `sessionCache: ConcurrentHashMap<sid, username>` — кэш сессий

---

### `FinanceService.java`
**Назначение**: Основная бизнес-логика финансов.

| Метод | Описание |
|-------|----------|
| `addTransaction()` | Добавляет транзакцию, обновляет `spent` в бюджете |
| `listTransactions()` | Список транзакций с фильтрами |
| `deleteTransaction()` | Удаление, корректировка бюджета |
| `setBudget()` | Создать/обновить лимит категории |
| `getBudgets()` | Список бюджетов пользователя |
| `deleteBudget()` | Удалить бюджет |
| `addReminder()` | Добавить напоминание |
| `getReminders()` | Список напоминаний |
| `consumeDueReminders()` | Получить и пометить как отправленные просроченные напоминания |
| `round2()` | Округление до 2 знаков |

---

### `GroupService.java`
**Назначение**: Логика групповых бюджетов.

| Метод | Описание |
|-------|----------|
| `create()` | Создать группу |
| `join()` | Присоединиться (автоматически покидает старую группу) |
| `leave()` | Покинуть группу (удаляет пустую группу) |
| `userGroupId()` | ID группы пользователя |
| `members()` | Map<userId, share> участников |
| `myShare()` | Доля текущего пользователя |
| `getName()` | Название группы |

---

### `CurrencyService.java`
**Назначение**: Курсы валют с внешнего API.

| Метод | Описание |
|-------|----------|
| `getRates()` | Текущие курсы (кэш 1 час) |
| `convert()` | Конвертация с поддержкой исторических дат |
| `historyOnDate()` | Исторические курсы (кэш 6 часов) |

**Кэширование**:
- `cache` — volatile CurrencyRates, TTL 1 час
- `histCache` — ConcurrentHashMap для исторических курсов, TTL 6 часов
- Fallback stub-данные при недоступности API

---

### `AutoCategorizerService.java`
**Назначение**: Автокатегоризация по ключевым словам.

**Правила** (ru/ro/en):
- еда, ресторан, кафе, market, supermarket, alimentara → Питание
- такси, transport, bus → Транспорт
- аренда, квартира, rent → Жильё
- зарплата, salary → Доход
- аптека, pharmacy → Здоровье

| Метод | Описание |
|-------|----------|
| `addRule()` | Добавить правило (keyword → category) |
| `categorize()` | Определить категорию по описанию |

---

### `SseService.java`
**Назначение**: Управление SSE-соединениями.

| Метод | Описание |
|-------|----------|
| `register()` | Создаёт SseEmitter, отправляет hello |
| `unregister()` | Закрывает соединение |
| `send()` | Отправка события конкретному клиенту |
| `broadcast()` | Массовая рассылка |
| `sessionIds()` | Список активных сессий |

**Хранение**: `Map<sessionId, SseEmitter>` — ConcurrentHashMap.

---

## Scheduler

### `ReminderScheduler.java`
**Назначение**: Периодическая проверка напоминаний.

- `@Scheduled(fixedRate = 10000)` — каждые 10 секунд
- Для каждой активной SSE-сессии:
  - Получает due reminders
  - Отправляет SSE-событие `reminder`
  - Помечает как отправленные

---

## Entities

### `User.java`
| Поле | Тип | Описание |
|------|-----|----------|
| id | String (UUID) | Первичный ключ |
| username | String | Уникальный логин |
| passwordHash | String | SHA-256 хеш пароля |
| groupId | String | ID группы (опционально) |
| share | Double | Доля в группе (default 1.0) |

---

### `Transaction.java`
| Поле | Тип | Описание |
|------|-----|----------|
| id | String (UUID) | PK |
| userId | String | Владелец |
| dateEpochDay | Long | Дата как epoch day |
| category | String | Категория |
| description | String | Описание |
| amount | BigDecimal | Сумма (- расход, + доход) |

---

### `Budget.java`
**Составной ключ**: `BudgetId(userId, category)`

| Поле | Тип | Описание |
|------|-----|----------|
| limitAmount | BigDecimal | Лимит на категорию |
| spent | BigDecimal | Потрачено |

---

### `Reminder.java`
| Поле | Тип | Описание |
|------|-----|----------|
| id | String (UUID) | PK |
| userId | String | Владелец |
| dueEpochDay | Long | Дата срабатывания |
| message | String | Текст напоминания |
| amount | BigDecimal | Сумма (опционально) |
| sent | Boolean | Отправлено? |

---

### `Session.java`
| Поле | Тип | Описание |
|------|-----|----------|
| sid | String | Session ID (PK) |
| username | String | Имя пользователя |
| createdAt | Instant | Время создания |

---

### `Group.java`
| Поле | Тип | Описание |
|------|-----|----------|
| id | String (UUID) | PK |
| name | String | Название группы |

---

### `GroupMember.java`
**Составной ключ**: `GroupMemberId(groupId, userId)`

| Поле | Тип | Описание |
|------|-----|----------|
| share | BigDecimal | Доля участника (0–1) |

---

## Repositories

| Repository | Entity | Особые методы |
|------------|--------|---------------|
| `UserRepository` | User | `findByUsername()`, `existsByUsername()` |
| `TransactionRepository` | Transaction | `findFiltered()` — JPQL с динамическими фильтрами |
| `BudgetRepository` | Budget | `addSpent()` — атомарное обновление spent |
| `ReminderRepository` | Reminder | `findDueReminders()`, `markAsSent()` |
| `SessionRepository` | Session | `findBySid()`, `deleteBySid()` |
| `GroupRepository` | Group | Стандартный CRUD |
| `GroupMemberRepository` | GroupMember | `findGroupIdByUserId()`, `countByGroupId()` |

---

## Exceptions

### `UnauthorizedException.java`
RuntimeException с HTTP 401. Бросается при отсутствии авторизации.

### `GlobalExceptionHandler.java`
`@ControllerAdvice` — глобальный обработчик:
- `UnauthorizedException` → 401
- `IllegalArgumentException` → 400
- `Exception` → 500

---

## Model

### `CurrencyRates.java`
DTO для курсов валют:
- `base` — базовая валюта
- `rates` — Map<currency, rate>
- `timestamp` — время получения

---

## application.properties
```properties
server.port=8080
spring.datasource.url=jdbc:sqlite:./data/fintrack.db
spring.jpa.hibernate.ddl-auto=update
logging.level.com.acs.finance=INFO
```

---

# FRONTEND (Vue 3 + TypeScript)

## Структура

```
web/src/
├── App.vue                 # Главный компонент
├── main.ts                 # Точка входа (createApp, Pinia)
├── api/
│   └── client.ts           # API клиент + кэш
├── components/             # UI компоненты
├── composables/            # Composition API логика
├── store/
│   └── index.ts            # Pinia store
└── utils/
    └── format.ts           # Утилиты форматирования
```

---

## API Client (`api/client.ts`)

### Функции

| Функция | Описание |
|---------|----------|
| `api(path, opts)` | Универсальный HTTP клиент |
| `sseConnect(onEvent)` | Подключение к SSE |
| `suggestCategory(desc)` | Запрос автокатегоризации |
| `exportTransactionsCsv()` | Генерация CSV |

### Кэширование (IndexedDB)
- **Database**: `fintrack-cache`
- **TTL**: 5 минут
- GET-запросы кэшируются
- POST/PUT/DELETE инвалидируют связанные GET
- При offline возвращает кэшированные данные

---

## Store (`store/index.ts`)

Pinia store с состоянием:

| State | Описание |
|-------|----------|
| `user` | Текущий пользователь |
| `favoritesSet` | Избранные категории (localStorage) |
| `theme` | Тема (dark/light) |
| `baseCurrency` | Базовая валюта |

| Action | Описание |
|--------|----------|
| `setUser()` | Установить пользователя |
| `toggleFavorite()` | Переключить избранную категорию |
| `setTheme()` | Сменить тему |
| `setBaseCurrency()` | Сменить базовую валюту |

---

## Composables

### `useAuth.ts`
| Функция | Описание |
|---------|----------|
| `login()` | Вход, запуск SSE, refreshAll |
| `register()` | Регистрация |
| `logout()` | Выход |
| `afterAuth()` | Инициализация после входа (SSE, загрузка данных) |

SSE-обработка:
- `budget-update` → refreshBudget
- `reminder`, `alert` → toast
- `tx-added` → refreshTx

---

### `useTransactions.ts`
Управление транзакциями.

| Экспорт | Описание |
|---------|----------|
| `transactions` | Список транзакций |
| `filteredTx` | Отфильтрованные (computed) |
| `stats` | Статистика (total, expenses, income) |
| `txForm` | Форма добавления |
| `templates` | Шаблоны транзакций (localStorage) |
| `quickAmounts` | Быстрые суммы [-100, -500, ...] |
| `refreshTx()` | Загрузка с сервера |
| `addTransaction()` | Добавление |
| `deleteTx()` | Удаление |
| `maybeSuggestCategory()` | Автокатегоризация (debounce 300ms) |
| `setQuick()` | Быстрые фильтры (today/week/month) |
| `exportCsv()` | Экспорт в CSV |

---

### `useBudgets.ts`
| Функция | Описание |
|---------|----------|
| `budgets` | Список бюджетов |
| `refreshBudget()` | Загрузка |
| `setBudget()` | Установить лимит |
| `deleteBudget()` | Удалить лимит |

---

### `useChart.ts`
Графики (Chart.js).

| Экспорт | Описание |
|---------|----------|
| `viewMode` | categories / group |
| `chartMode` | expenses / income / both |
| `refreshChart()` | Перерисовка Doughnut chart |

**Режимы**:
- **categories**: группировка по категориям транзакций
- **group**: доходы/расходы участников группы

---

### `useCurrency.ts`
| Функция | Описание |
|---------|----------|
| `currency` | {base, symbol, val} |
| `convert` | {amount, result} |
| `refreshRate()` | Загрузка курса |
| `convertCurrency()` | Конвертация |

Автообновление при смене base/symbol (watch).

---

### `useCrypto.ts`
Криптовалюты (CoinGecko API).

| Функция | Описание |
|---------|----------|
| `cryptoList` | Список монет |
| `loadCrypto()` | Загрузка (кэш в localStorage) |

Монеты: BTC, ETH, BNB, SOL, TON, DOGE, ADA, XRP, DOT.

---

### `useGroup.ts`
Групповой бюджет.

| Функция | Описание |
|---------|----------|
| `myGroup` | {groupId, groupName, share} |
| `groupPeers` | Участники с доходами/расходами |
| `groupCreate()` | Создать группу |
| `groupJoin()` | Присоединиться |
| `groupLeave()` | Покинуть |
| `copyInvite()` | Скопировать ссылку ?join=... |
| `refreshGroupMeta()` | Обновить данные (с защитой от race condition) |

---

### `useRecommendations.ts`
Рекомендации по оптимизации расходов.

**Настройки** (localStorage `rec.options`):
- `periodDays` — период анализа (30)
- `subWindowDays` — окно поиска подписок (60)
- `subMinCount` — мин. повторов для подписки (3)
- `subMinSum` — мин. сумма подписки (100)
- `topSharePct` — порог доминирования категории (35%)
- `top1CutPct` / `top2CutPct` — % сокращения топ-категорий

**Генерируемые рекомендации**:
1. Расходы > доходов
2. Оптимизировать топ-1 категорию
3. Снизить траты в топ-2 категории
4. Обнаруженные подписки
5. Превышение бюджета
6. Доминирующая категория
7. Категории без бюджета

---

### `useReminders.ts`
| Функция | Описание |
|---------|----------|
| `reminders` | Список напоминаний |
| `refreshReminders()` | Загрузка |
| `addReminder()` | Добавление |

---

### `useToast.ts`
Уведомления (toast).
- `toast(msg)` — показать на 2.5 сек

---

## Components

### `Header.vue`
Шапка с логотипом, меню пользователя (тема, выход).

### `AuthForm.vue`
Форма входа/регистрации.

### `StatsCards.vue`
Карточки статистики: всего транзакций, расходы, доходы.

### `TransactionForm.vue`
Форма добавления транзакции:
- Дата, тип, сумма, категория, описание
- Быстрые суммы
- Шаблоны
- Избранные категории (★)

### `TransactionList.vue`
Таблица транзакций:
- Быстрые фильтры (сегодня/неделя/месяц)
- Поиск по описанию
- Фильтр избранных категорий
- Фильтры по дате и категории
- Экспорт CSV
- Удаление

### `ChartView.vue`
Doughnut-график:
- Режим: категории / группа
- Показать: расходы / доходы / вместе

### `BudgetManager.vue`
Управление бюджетами:
- Установка лимита
- Отображение spent / limit
- Удаление

### `CurrencyConverter.vue`
Курсы валют:
- Выбор base/symbol
- Конвертация суммы

### `Recommendations.vue`
Рекомендации:
- Настраиваемые параметры
- Список советов с потенциальной экономией

### `Reminders.vue`
Напоминания:
- Форма добавления
- Список с датой, сообщением, суммой

### `GroupBudget.vue`
Групповой бюджет:
- Создание группы
- Присоединение по ID
- Таблица участников
- Копирование приглашения
- Выход из группы

### `CryptoView.vue`
Криптовалюты:
- Таблица с ценой и % изменения 24ч
- Выбор валюты отображения (USD/EUR)

---

## Utils

### `format.ts`
```typescript
fmt(n) // форматирование числа (ru-RU, 2 знака)
```

---

# Взаимодействие Frontend ↔ Backend

```
┌─────────────┐     HTTP/SSE      ┌─────────────┐
│   Vue App   │ ◄───────────────► │ Spring Boot │
│             │                   │             │
│ composables │                   │ controllers │
│     ↓       │                   │     ↓       │
│   api/      │    /api/*         │  services   │
│  client.ts  │ ──────────────►   │     ↓       │
│             │                   │ repositories│
│ IndexedDB   │                   │     ↓       │
│   cache     │                   │   SQLite    │
└─────────────┘                   └─────────────┘
```

### Аутентификация
1. `POST /api/login` → создаётся сессия, ставится cookie `SID`
2. Все запросы включают `credentials: 'include'`
3. Backend проверяет SID через `AuthService`

### Real-time обновления (SSE)
1. После входа фронт подключается к `GET /api/events`
2. Backend через `SseService` отправляет события
3. Фронт обрабатывает в `useAuth.afterAuth()`

### Автокатегоризация
1. При вводе описания транзакции (debounce 300ms)
2. Запрос `GET /api/categorizer/suggest?desc=...`
3. Backend сопоставляет с правилами `AutoCategorizerService`

---

# Ключевые фичи

## 1. Кэширование

### Backend
| Данные | Механизм | TTL |
|--------|----------|-----|
| Пользователи | ConcurrentHashMap | ∞ (до рестарта) |
| Сессии | ConcurrentHashMap + DB | ∞ |
| Курсы валют | volatile + lock | 1 час |
| Исторические курсы | ConcurrentHashMap | 6 часов |

### Frontend
| Данные | Механизм | TTL |
|--------|----------|-----|
| API ответы | IndexedDB | 5 минут |
| Криптовалюты | localStorage | ∞ (fallback) |
| Шаблоны | localStorage | ∞ |
| Настройки рекомендаций | localStorage | ∞ |
| Избранные категории | localStorage | ∞ |
| Тема | localStorage | ∞ |

---

## 2. Логирование

Backend использует **SLF4J + Logback** (через `@Slf4j` Lombok):

```java
log.info("User logged in: {}", username);
log.warn("Budget exceeded: category={}", cat);
log.error("Failed to fetch rates", e);
```

Уровни в `application.properties`:
- `com.acs.finance=INFO`
- `org.hibernate.SQL=WARN`

---

## 3. Обработка ошибок

### Backend
- `GlobalExceptionHandler` — централизованная обработка
- Возврат JSON `{error: "..."}` или `{ok: false, error: "..."}`

### Frontend
- `api()` возвращает `{ok: false, error: 'offline'}` при сетевых ошибках
- `safeJson()` — безопасный парсинг JSON
- Toast-уведомления об ошибках

---

## 4. Безопасность

- Пароли хешируются SHA-256 + Base64
- Сессии — криптографически случайные токены (24 байта)
- HttpOnly cookies для SID
- CORS настроен для `/api/**`

---

## 5. Offline-режим

Frontend:
- IndexedDB кэш возвращает данные при недоступности сервера
- localStorage кэш для криптовалют
- Graceful degradation — приложение остаётся функциональным

---

# Зависимости

## Backend (Maven)
- Spring Boot 3.2.4 (Web, Data JPA, Validation, WebFlux)
- SQLite JDBC 3.45.1
- Hibernate SQLite Dialect 6.4.4
- Lombok

## Frontend (npm)
- Vue 3.5.12
- Pinia 2.2.6 (state management)
- Chart.js 4.4.4 (графики)
- Vite 5.4.10 (сборка)
- TypeScript 5.6.3

---

# Запуск

```bash
# Backend
mvn spring-boot:run

# Frontend (dev)
cd web
npm install
npm run dev
```

Backend: http://localhost:8080  
Frontend dev: http://localhost:5173 (проксирует API на 8080)

---

# Диаграммы потоков данных

## Вход пользователя

```
┌──────────┐    POST /api/login     ┌──────────────┐
│  Браузер │ ──────────────────────►│ AuthController│
│          │   username, password   │              │
└──────────┘                        └──────┬───────┘
     ▲                                     │
     │                                     ▼
     │                              ┌──────────────┐
     │                              │  AuthService │
     │                              │              │
     │                              │ 1. Ищет user │
     │                              │    в кэше    │
     │                              │ 2. Или в БД  │
     │                              │ 3. Проверяет │
     │                              │    пароль    │
     │                              │ 4. Создаёт   │
     │                              │    сессию    │
     │                              └──────┬───────┘
     │                                     │
     │  Set-Cookie: SID=abc123...          │
     │  {ok: true, user: {...}}            │
     └─────────────────────────────────────┘
```

## Добавление транзакции

```
┌──────────┐  POST /api/transaction/add  ┌─────────────────────┐
│  Браузер │ ───────────────────────────►│TransactionController│
│          │  date, amount, category...  │                     │
└──────────┘                             └──────────┬──────────┘
     ▲                                              │
     │                                              ▼
     │                                   ┌─────────────────────┐
     │                                   │ AutoCategorizerSvc  │
     │                                   │ (если нет категории)│
     │                                   └──────────┬──────────┘
     │                                              │
     │                                              ▼
     │                                   ┌─────────────────────┐
     │                                   │   FinanceService    │
     │                                   │                     │
     │                                   │ 1. Сохраняет в БД   │
     │                                   │ 2. Обновляет budget │
     │                                   │    (spent += amount)│
     │                                   └──────────┬──────────┘
     │                                              │
     │                                              ▼
     │  SSE: {type: "tx-added"}          ┌─────────────────────┐
     │  SSE: {type: "budget-update"}     │    SseService       │
     │◄──────────────────────────────────│                     │
     │                                   │ Если budget exceeded│
     │  SSE: {type: "alert", message:..} │ → отправляет alert  │
     │◄──────────────────────────────────┴─────────────────────┘
```

## Real-time уведомления (SSE)

```
┌──────────────┐                              ┌─────────────────┐
│   Браузер    │                              │   Spring Boot   │
│              │                              │                 │
│  После входа │   GET /api/events            │                 │
│  ───────────►│─────────────────────────────►│  SseController  │
│              │                              │        │        │
│              │   Connection: keep-alive     │        ▼        │
│              │◄─────────────────────────────│   SseService    │
│              │                              │        │        │
│              │                              │  Регистрирует   │
│              │   data: {"type":"hello"}     │  SseEmitter     │
│              │◄─────────────────────────────│        │        │
│              │                              │        │        │
│   (ждёт...)  │         ...время...          │   (хранит в     │
│              │                              │    Map<sid,     │
│              │                              │    emitter>)    │
│              │                              │        │        │
│              │                              │        │        │
│  ┌───────────┴──┐   Кто-то добавил TX       │        │        │
│  │ Другой       │ ─────────────────────────►│        │        │
│  │ запрос       │                           │        ▼        │
│  └───────────┬──┘                           │  send(sid,      │
│              │                              │   {type:...})   │
│              │   data: {"type":"tx-added"}  │        │        │
│              │◄─────────────────────────────┼────────┘        │
│              │                              │                 │
│  useAuth:    │                              │                 │
│  refreshTx() │                              │                 │
└──────────────┘                              └─────────────────┘
```

---

# Схема базы данных

```
┌─────────────────────────────────────────────────────────────────┐
│                           USERS                                  │
├─────────────────────────────────────────────────────────────────┤
│ id            VARCHAR(36)  PK     UUID пользователя             │
│ username      VARCHAR      UNIQUE  Логин                         │
│ password_hash VARCHAR             SHA-256 хеш пароля            │
│ group_id      VARCHAR(36)         FK → groups.id (опционально)  │
│ share         DOUBLE              Доля в группе (0-1)           │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ 1:N
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       TRANSACTIONS                               │
├─────────────────────────────────────────────────────────────────┤
│ id             VARCHAR(36)  PK    UUID транзакции               │
│ user_id        VARCHAR(36)  FK    → users.id                    │
│ date_epoch_day BIGINT             Дата (дни с 1970-01-01)       │
│ category       VARCHAR            Категория (Питание, и т.д.)   │
│ description    VARCHAR            Описание                      │
│ amount         DECIMAL(14,2)      Сумма (- расход, + доход)     │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                         BUDGETS                                  │
├─────────────────────────────────────────────────────────────────┤
│ user_id      VARCHAR(36)  PK, FK  → users.id                    │
│ category     VARCHAR(100) PK      Категория                     │
│ limit_amount DECIMAL(14,2)        Лимит на месяц                │
│ spent        DECIMAL(14,2)        Потрачено (автообновляется)   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                        REMINDERS                                 │
├─────────────────────────────────────────────────────────────────┤
│ id            VARCHAR(36)  PK     UUID напоминания              │
│ user_id       VARCHAR(36)  FK     → users.id                    │
│ due_epoch_day BIGINT              Дата срабатывания             │
│ message       VARCHAR             Текст                         │
│ amount        DECIMAL(14,2)       Сумма (опционально)           │
│ sent          BOOLEAN             Уже отправлено?               │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                         SESSIONS                                 │
├─────────────────────────────────────────────────────────────────┤
│ sid        VARCHAR(64)  PK        Session ID (токен)            │
│ username   VARCHAR                Имя пользователя              │
│ created_at TIMESTAMP              Время создания                │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                          GROUPS                                  │
├─────────────────────────────────────────────────────────────────┤
│ id    VARCHAR(36)  PK             UUID группы                   │
│ name  VARCHAR                     Название группы               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ 1:N
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      GROUP_MEMBERS                               │
├─────────────────────────────────────────────────────────────────┤
│ group_id  VARCHAR(36)  PK, FK     → groups.id                   │
│ user_id   VARCHAR(36)  PK, FK     → users.id                    │
│ share     DECIMAL(5,4)            Доля участника (0.0-1.0)      │
└─────────────────────────────────────────────────────────────────┘
```

---

# Примеры API запросов/ответов

## Регистрация

**Запрос:**
```http
POST /api/register
Content-Type: application/x-www-form-urlencoded

username=ivan&password=secret123
```

**Ответ (успех):**
```json
{"ok": true}
```

**Ответ (ошибка):**
```json
{"ok": false, "error": "user_exists_or_bad"}
```

---

## Вход

**Запрос:**
```http
POST /api/login
Content-Type: application/x-www-form-urlencoded

username=ivan&password=secret123
```

**Ответ (успех):**
```json
{
  "ok": true,
  "user": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "username": "ivan"
  }
}
```
+ Cookie: `SID=dGhpcyBpcyBhIHNlc3Npb24gdG9rZW4...`

---

## Добавление транзакции

**Запрос:**
```http
POST /api/transaction/add
Cookie: SID=...
Content-Type: application/x-www-form-urlencoded

date=2024-03-15&amount=-150.50&category=Питание&description=Обед в кафе
```

**Ответ:**
```json
{
  "ok": true,
  "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7"
}
```

---

## Получение транзакций с фильтрами

**Запрос:**
```http
GET /api/transactions?from=2024-03-01&to=2024-03-31&category=Питание
Cookie: SID=...
```

**Ответ:**
```json
{
  "items": [
    {
      "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
      "date": "2024-03-15",
      "category": "Питание",
      "description": "Обед в кафе",
      "amount": -150.50
    },
    {
      "id": "8d0e7780-8536-51ef-055c-f18gd2g01bf8",
      "date": "2024-03-10",
      "category": "Питание",
      "description": "Продукты",
      "amount": -320.00
    }
  ]
}
```

---

## Установка бюджета

**Запрос:**
```http
POST /api/budget/set
Cookie: SID=...
Content-Type: application/x-www-form-urlencoded

category=Питание&limit=5000
```

**Ответ:**
```json
{
  "ok": true,
  "category": "Питание",
  "limit": 5000.0
}
```

---

## Получение бюджетов

**Запрос:**
```http
GET /api/budget
Cookie: SID=...
```

**Ответ:**
```json
{
  "items": [
    {"category": "Питание", "limit": 5000.0, "spent": 470.50},
    {"category": "Транспорт", "limit": 2000.0, "spent": 850.00}
  ]
}
```

---

## Конвертация валют

**Запрос:**
```http
GET /api/currency/convert?from=USD&to=MDL&amount=100
```

**Ответ:**
```json
{
  "ok": true,
  "from": "USD",
  "to": "MDL",
  "amount": 100.0,
  "result": 1785.50
}
```

---

## Автокатегоризация

**Запрос:**
```http
GET /api/categorizer/suggest?desc=такси+до+работы
Cookie: SID=...
```

**Ответ:**
```json
{"category": "Транспорт"}
```

---

# Пользовательские сценарии

## Сценарий 1: Первый запуск

```
1. Пользователь открывает приложение
   └─► Видит форму входа/регистрации

2. Нажимает "Зарегистрироваться"
   └─► POST /api/register
   └─► Toast: "Регистрация успешна, войдите"

3. Вводит логин/пароль, нажимает "Войти"
   └─► POST /api/login
   └─► Получает cookie SID
   └─► Подключается к SSE (/api/events)
   └─► Загружает все данные (refreshAll):
       ├─► GET /api/budget
       ├─► GET /api/transactions
       ├─► GET /api/reminders
       ├─► GET /api/group/me
       └─► GET /api/currency

4. Видит пустой дашборд
   └─► Готов добавлять транзакции
```

## Сценарий 2: Добавление расхода

```
1. Заполняет форму транзакции:
   - Дата: сегодня
   - Тип: Расход
   - Сумма: 500
   - Описание: "продукты в магните"

2. При вводе описания (debounce 300ms):
   └─► GET /api/categorizer/suggest?desc=продукты+в+магните
   └─► Автоматически заполняется категория: "Питание"

3. Нажимает "Сохранить"
   └─► POST /api/transaction/add
       {date, amount: -500, category: "Питание", description}

4. Backend:
   ├─► Сохраняет транзакцию
   ├─► Обновляет budget.spent для "Питание" (+500)
   ├─► Проверяет: spent > limit?
   │   └─► Если да: SSE alert "Превышен бюджет..."
   └─► SSE: tx-added, budget-update

5. Frontend получает SSE:
   ├─► refreshTx() — обновляет список
   ├─► refreshBudget() — обновляет бюджеты
   └─► Показывает toast если alert
```

## Сценарий 3: Семейный бюджет

```
1. Пользователь А создаёт группу:
   └─► POST /api/group/create {name: "Семья"}
   └─► Получает groupId

2. Копирует ссылку-приглашение:
   └─► https://app.com/?join=abc-123-def
   └─► Отправляет жене

3. Пользователь Б переходит по ссылке:
   └─► При загрузке App.vue парсит ?join=
   └─► POST /api/group/join {groupId: "abc-123-def"}
   └─► Теперь оба в одной группе

4. Оба видят:
   ├─► Общий бюджет (сумма лимитов × доли)
   ├─► Статистику друг друга (доходы/расходы)
   └─► График группы
```

## Сценарий 4: Напоминание о платеже

```
1. Добавляет напоминание:
   - Дата: 25 марта
   - Сообщение: "Оплатить интернет"
   - Сумма: 500
   └─► POST /api/reminder/add

2. 25 марта (или позже):
   └─► ReminderScheduler каждые 10 сек проверяет
   └─► Находит due reminder (dueEpochDay <= today)
   └─► Отправляет SSE: {type: "reminder", message: "Оплатить интернет (500)"}
   └─► Помечает sent = true

3. Пользователь видит toast с напоминанием
```

---

# Глоссарий

| Термин | Описание |
|--------|----------|
| **SID** | Session ID — уникальный токен сессии в cookie |
| **SSE** | Server-Sent Events — односторонний real-time канал от сервера |
| **Epoch Day** | Число дней с 1 января 1970 года (для хранения дат) |
| **Composable** | Функция Vue 3 Composition API для переиспользования логики |
| **Toast** | Всплывающее уведомление на 2.5 секунды |
| **Share** | Доля участника в группе (0.0-1.0), влияет на расчёт бюджета |
| **Budget.spent** | Сумма расходов по категории (автоматически обновляется) |
| **TTL** | Time To Live — время жизни кэша |
| **Debounce** | Задержка перед выполнением (300ms для автокатегоризации) |
| **Fallback** | Резервные данные при недоступности API |

---

# Частые вопросы (FAQ)

## Как работает автокатегоризация?

`AutoCategorizerService` хранит Map ключевых слов:
```
"такси" → "Транспорт"
"ресторан" → "Питание"
"зарплата" → "Доход"
```

При вводе описания ищет вхождение ключевых слов (регистронезависимо).

---

## Почему spent обновляется автоматически?

При добавлении транзакции с категорией и отрицательной суммой:
```java
// FinanceService.addTransaction()
if (category != null && amount < 0) {
    budgetRepository.addSpent(userId, category, -amount);
}
```

SQL обновляет атомарно: `SET spent = spent + :amount`

---

## Что если сервер недоступен?

Frontend использует IndexedDB кэш:
1. При успешном GET сохраняет ответ с timestamp
2. При ошибке сети возвращает кэшированные данные
3. TTL 5 минут — старые данные игнорируются

---

## Как работает групповой бюджет?

1. Каждый участник имеет `share` (0-1)
2. При запросе `/api/group/budget`:
   ```
   Для каждого участника:
     Для каждого его бюджета:
       total_limit += limit × share
       total_spent += spent × share
   ```
3. Получаем агрегированный бюджет группы

---

## Зачем epoch day вместо даты?

- Занимает 8 байт вместо ~10+ для строки даты
- Легко сравнивать и фильтровать (>, <, BETWEEN)
- Нет проблем с timezone
- Конвертация: `LocalDate.ofEpochDay(epochDay)`

---
