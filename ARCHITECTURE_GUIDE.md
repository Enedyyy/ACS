# FinTrack - Архитектурное Руководство

> Этот документ объясняет как устроен проект, как взаимодействуют фронтенд и бэкенд, и какие фичи реализованы.

---

## Общая Структура Проекта

```
FinTrack/
├── src/main/java/          # Backend (Spring Boot)
│   └── com/acs/finance/
│       ├── controller/     # REST-контроллеры (обработка HTTP-запросов)
│       ├── service/        # Бизнес-логика
│       ├── repository/     # Работа с БД (JPA)
│       ├── entity/         # JPA-сущности (таблицы БД)
│       ├── config/         # Конфигурация приложения
│       ├── scheduler/      # Фоновые задачи
│       └── FinanceApplication.java  # Точка входа
│
├── web/                    # Frontend (Vue 3 + TypeScript)
│   └── src/
│       ├── api/            # HTTP-клиент + кэширование
│       ├── components/     # Vue-компоненты UI
│       ├── composables/    # Бизнес-логика фронтенда (хуки)
│       ├── store/          # Глобальное состояние (Pinia)
│       └── App.vue         # Главный компонент
│
├── data/                   # SQLite база данных
└── pom.xml                 # Maven зависимости
```

---

## Backend (Spring Boot)

### Слои Архитектуры

```
HTTP-запрос → Controller → Service → Repository → SQLite DB
```

### 1. Controller (Контроллеры)
**Путь:** `src/main/java/com/acs/finance/controller/`

Принимают HTTP-запросы и возвращают JSON-ответы.

| Файл | Что делает |
|------|------------|
| `AuthController.java` | Регистрация, логин, логаут, получение текущего пользователя |
| `TransactionController.java` | CRUD для транзакций (доход/расход) |
| `BudgetController.java` | Установка/удаление лимитов по категориям |
| `GroupController.java` | Групповой бюджет (создание группы, присоединение, статистика) |
| `CurrencyController.java` | Курсы валют и конвертация |
| `ReminderController.java` | Напоминания о платежах |
| `CategorizerController.java` | Авто-определение категории по описанию |
| `SseController.java` | Server-Sent Events (push-уведомления) |
| `HealthController.java` | Проверка состояния сервера |

**Пример запроса:**
```
POST /api/transaction/add
  → TransactionController.add()
  → FinanceService.addTransaction()
  → TransactionRepository.save()
  → SQLite
```

### 2. Service (Сервисы)
**Путь:** `src/main/java/com/acs/finance/service/`

Содержат бизнес-логику приложения.

| Файл | Что делает |
|------|------------|
| `AuthService.java` | Хеширование паролей, сессии, кэш пользователей в памяти |
| `FinanceService.java` | Работа с транзакциями, бюджетами, напоминаниями |
| `GroupService.java` | Логика групп (создание, присоединение, расчет долей) |
| `CurrencyService.java` | Получение курсов от внешнего API + кэширование |
| `AutoCategorizerService.java` | Простое определение категории по ключевым словам |
| `SseService.java` | Управление SSE-соединениями, отправка событий клиентам |

### 3. Repository (Репозитории)
**Путь:** `src/main/java/com/acs/finance/repository/`

Интерфейсы для работы с БД через Spring Data JPA.

```java
// Пример - поиск транзакций с фильтрами
@Query("SELECT t FROM Transaction t WHERE t.userId = :userId ...")
List<Transaction> findFiltered(String userId, Long from, Long to, String category);
```

### 4. Entity (Сущности)
**Путь:** `src/main/java/com/acs/finance/entity/`

Представляют таблицы в SQLite.

| Сущность | Описание |
|----------|----------|
| `User` | Пользователь (id, username, passwordHash, share) |
| `Session` | Сессия авторизации (sid → username) |
| `Transaction` | Транзакция (дата, сумма, категория, описание) |
| `Budget` | Лимит бюджета (категория, limit, spent) |
| `Reminder` | Напоминание (дата, сообщение, сумма) |
| `Group` | Группа для совместного бюджета |
| `GroupMember` | Участник группы с долей (share) |

### 5. Scheduler (Планировщик)
**Путь:** `src/main/java/com/acs/finance/scheduler/`

Фоновые задачи.

```java
@Scheduled(fixedRate = 10000) // Каждые 10 секунд
public void checkReminders() {
    // Проверяет напоминания и отправляет через SSE
}
```

### 6. Логирование

Используется **Lombok @Slf4j** — логи пишутся в консоль:

```java
log.info("Transaction added: user={}, amount={}", username, amount);
log.warn("Budget exceeded: category={}", category);
log.error("Failed to fetch currency rates", exception);
```

Настройки в `application.properties`:
```properties
logging.level.com.acs.finance=INFO
logging.pattern.console=%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n
```

---

## Frontend (Vue 3 + TypeScript)

### Структура

```
web/src/
├── App.vue              # Главный компонент, объединяет всё
├── main.ts              # Точка входа, монтирование Vue
├── api/client.ts        # HTTP-клиент + кэширование + SSE
├── store/index.ts       # Глобальное состояние (Pinia)
├── composables/         # Реактивная бизнес-логика
└── components/          # UI-компоненты
```

### 1. API Client + Кэширование
**Путь:** `web/src/api/client.ts`

Централизованный HTTP-клиент с **офлайн-кэшированием** через IndexedDB.

```typescript
// Кэширование GET-запросов
export async function api(path: string, opts: ApiOptions = {}): Promise<any> {
    // 1. Если POST/PUT/DELETE → инвалидируем связанный кэш
    // 2. Делаем fetch()
    // 3. При успехе → сохраняем в IndexedDB с TTL 5 минут
    // 4. При ошибке сети → возвращаем данные из кэша
}
```

**Принцип работы кэша:**
- **GET-запросы** кэшируются в IndexedDB на 5 минут
- **POST/PUT/DELETE** автоматически инвалидируют кэш
- При **офлайне** — данные берутся из кэша

### 2. SSE (Server-Sent Events)
**Путь:** `web/src/api/client.ts`

Real-time уведомления от сервера:

```typescript
export function sseConnect(onEvent: (e: any) => void) {
    const es = new EventSource('/api/events');
    es.onmessage = (ev) => onEvent(JSON.parse(ev.data));
}
```

**Типы событий:**
- `tx-added` — добавлена транзакция
- `budget-update` — обновился бюджет
- `alert` — превышен лимит бюджета
- `reminder` — сработало напоминание

### 3. Store (Глобальное состояние)
**Путь:** `web/src/store/index.ts`

Используется **Pinia** для хранения:

```typescript
state: () => ({
    user: null,              // Текущий пользователь
    favoritesSet: Set(),     // Избранные категории
    theme: 'dark',           // Тема интерфейса
    baseCurrency: 'MDL'      // Базовая валюта
})
```

Данные сохраняются в **localStorage** между сессиями.

### 4. Composables (Хуки)
**Путь:** `web/src/composables/`

Каждый composable инкапсулирует логику определённой фичи:

| Файл | Что делает |
|------|------------|
| `useAuth.ts` | Логин, регистрация, логаут |
| `useTransactions.ts` | CRUD транзакций, фильтры, шаблоны, экспорт CSV |
| `useBudgets.ts` | Управление лимитами бюджета |
| `useChart.ts` | Данные для графиков (группировка по категориям) |
| `useCurrency.ts` | Конвертация валют |
| `useRecommendations.ts` | Умные рекомендации по экономии |
| `useReminders.ts` | Напоминания о платежах |
| `useGroup.ts` | Групповой бюджет |
| `useCrypto.ts` | Курсы криптовалют |
| `useToast.ts` | Всплывающие уведомления |

**Пример использования:**
```typescript
const { transactions, addTransaction, deleteTx } = useTransactions(toast, refreshBudget);
```

### 5. Components (Компоненты)
**Путь:** `web/src/components/`

Визуальные блоки интерфейса:

| Компонент | Описание |
|-----------|----------|
| `AuthForm.vue` | Форма логина/регистрации |
| `TransactionForm.vue` | Добавление транзакции |
| `TransactionList.vue` | Список транзакций с фильтрами |
| `ChartView.vue` | Графики расходов/доходов |
| `BudgetManager.vue` | Установка лимитов по категориям |
| `CurrencyConverter.vue` | Конвертер валют |
| `Recommendations.vue` | Рекомендации по экономии |
| `Reminders.vue` | Напоминания |
| `GroupBudget.vue` | Групповой бюджет |
| `CryptoView.vue` | Курсы криптовалют |

---

## Взаимодействие Frontend и Backend

### API Endpoints (21 эндпоинт)

#### Авторизация
| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/me` | Получить текущего пользователя |
| POST | `/api/register` | Регистрация |
| POST | `/api/login` | Вход |
| POST | `/api/logout` | Выход |

#### Транзакции
| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/transactions` | Список транзакций (с фильтрами from, to, category) |
| POST | `/api/transaction/add` | Добавить транзакцию |
| POST | `/api/transaction/delete` | Удалить транзакцию |

#### Бюджеты
| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/budget` | Список лимитов |
| POST | `/api/budget/set` | Установить лимит |
| POST | `/api/budget/delete` | Удалить лимит |

#### Группы
| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/group/me` | Моя группа |
| GET | `/api/group/peers` | Участники группы со статистикой |
| GET | `/api/group/budget` | Бюджет группы |
| POST | `/api/group/create` | Создать группу |
| POST | `/api/group/join` | Присоединиться |
| POST | `/api/group/leave` | Покинуть группу |

#### Прочее
| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/currency/rates` | Курсы валют |
| GET | `/api/currency/convert` | Конвертация |
| GET | `/api/reminders` | Список напоминаний |
| POST | `/api/reminder/add` | Добавить напоминание |
| GET | `/api/categorizer/suggest` | Предложить категорию |
| GET | `/api/events` | SSE-поток событий |

### Пример Полного Цикла

**Добавление транзакции:**

```
1. Пользователь заполняет форму TransactionForm.vue
2. Вызывается useTransactions.addTransaction()
3. api('/api/transaction/add', { method: 'POST', form: {...} })
4. → Backend: TransactionController.add()
5. → AuthService.getUserBySession() — проверка авторизации
6. → FinanceService.addTransaction() — сохранение в БД
7. → BudgetRepository.addSpent() — обновление потраченного
8. → SseService.send() — уведомление клиента
9. ← JSON: { ok: true, id: "..." }
10. Frontend обновляет UI и показывает toast
```

---

## Ключевые Фичи

### 1. Офлайн-режим
- GET-запросы кэшируются в IndexedDB
- При потере сети — данные из кэша
- TTL кэша: 5 минут

### 2. Real-time уведомления (SSE)
- Превышение бюджета → алерт
- Напоминания о платежах
- Синхронизация между вкладками

### 3. Автокатегоризация
- Вводишь описание "Яндекс Такси"
- Сервис определяет категорию "Транспорт"
- На основе ключевых слов

### 4. Умные рекомендации
- Анализ трат за 30 дней
- Определение подписок (повторяющиеся платежи)
- Советы по категориям-лидерам

### 5. Групповой бюджет
- Создание группы (например, семейный бюджет)
- Ссылка-приглашение
- Общая статистика участников

### 6. Шаблоны транзакций
- Сохранение часто используемых операций
- Хранятся в localStorage

### 7. Избранные категории
- Быстрый фильтр по любимым категориям
- Персистентность в localStorage

### 8. Экспорт в CSV
- Выгрузка транзакций в файл
- Для анализа в Excel

---

## База Данных

**SQLite** (`data/fintrack.db`)

Таблицы создаются автоматически через Hibernate (`ddl-auto=update`).

### Схема

```
users
├── id (UUID)
├── username (unique)
├── password_hash
└── share

sessions
├── sid (primary key)
├── username
└── created_at

transactions
├── id (UUID)
├── user_id → users.id
├── date_epoch_day
├── category
├── description
└── amount

budgets
├── user_id (composite PK)
├── category (composite PK)
├── limit_amount
└── spent

reminders
├── id (UUID)
├── user_id
├── due_epoch_day
├── message
├── amount
└── sent

groups
├── id (UUID)
└── name

group_members
├── group_id (composite PK)
├── user_id (composite PK)
└── share
```

---

## Авторизация

### Механизм
1. При логине создаётся **session token** (случайные 24 байта в Base64)
2. Token сохраняется в **cookie** (`sid`)
3. При каждом запросе контроллер извлекает `sid` из cookie
4. `AuthService.getUserBySession()` возвращает пользователя

### Кэширование сессий
```java
// In-memory для быстрого доступа
private final Map<String, User> userCache = new ConcurrentHashMap<>();
private final Map<String, String> sessionCache = new ConcurrentHashMap<>();

// При первом запросе — из БД, далее из памяти
```

---

## Конфигурация

**`src/main/resources/application.properties`**

```properties
# Сервер
server.port=8080

# SQLite
spring.datasource.url=jdbc:sqlite:./data/fintrack.db

# Hibernate — автосоздание таблиц
spring.jpa.hibernate.ddl-auto=update

# Логирование
logging.level.com.acs.finance=INFO
```

---

## Как Запустить

### Backend
```bash
mvn spring-boot:run
```

### Frontend (для разработки)
```bash
cd web
npm install
npm run dev
```

### Production Build
```bash
cd web
npm run build  # Собирает в web/public/
# Backend отдаёт статику из web/public/
```

---

## Диаграмма Потока Данных

```
┌─────────────────────────────────────────────────────────────┐
│                        FRONTEND                              │
│  ┌─────────┐    ┌────────────┐    ┌─────────────────────┐  │
│  │Components│ ←→ │Composables │ ←→ │ API Client + Cache  │  │
│  │  (UI)   │    │  (Logic)   │    │    (IndexedDB)      │  │
│  └─────────┘    └────────────┘    └──────────┬──────────┘  │
│                                               │              │
│                      Store (Pinia)           │              │
│                    ┌─────────────┐            │              │
│                    │ user, theme │            │              │
│                    │ favorites   │            │              │
│                    └─────────────┘            │              │
└───────────────────────────────────────────────┼──────────────┘
                                                │
                        HTTP / SSE              │
                                                ▼
┌───────────────────────────────────────────────┴──────────────┐
│                        BACKEND                                │
│  ┌──────────────┐    ┌──────────────┐    ┌───────────────┐  │
│  │ Controllers  │ → │   Services   │ → │  Repositories │  │
│  │  (REST API)  │    │   (Logic)    │    │    (JPA)      │  │
│  └──────────────┘    └──────────────┘    └───────┬───────┘  │
│                                                   │          │
│         ┌─────────────┐        ┌─────────────┐   │          │
│         │  Scheduler  │        │  SSE Service │   │          │
│         │ (Reminders) │        │ (Push events)│   │          │
│         └─────────────┘        └─────────────┘   │          │
│                                                   │          │
│                                                   ▼          │
│                                          ┌──────────────┐   │
│                                          │   SQLite DB  │   │
│                                          └──────────────┘   │
└──────────────────────────────────────────────────────────────┘
```

---

Это руководство покрывает основную архитектуру проекта. Для деталей — смотри исходники с комментариями.
