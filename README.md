# FinTrack - Personal Finance Tracker

Современное веб-приложение для управления личными финансами с поддержкой группового бюджетирования, автоматической категоризации транзакций и конвертации валют.

## 📋 Содержание

- [Описание](#описание)
- [Основные возможности](#основные-возможности)
- [Технологический стек](#технологический-стек)
- [Архитектура](#архитектура)
- [API эндпоинты](#api-эндпоинты)
- [Установка и запуск](#установка-и-запуск)
- [Структура проекта](#структура-проекта)

## 🎯 Описание

FinTrack - это full-stack приложение для учета личных финансов с расширенными возможностями для управления бюджетом, отслеживания расходов и доходов, совместного планирования бюджета в группах и конвертации валют.

## ✨ Основные возможности

### 💰 Управление транзакциями
- Добавление доходов и расходов
- Категоризация транзакций
- Автоматическое предложение категорий на основе описания
- Фильтрация по датам и категориям
- Удаление транзакций

### 📊 Бюджетирование
- Установка лимитов по категориям
- Отслеживание расходов в реальном времени
- Уведомления о превышении бюджета
- Визуализация бюджета с помощью графиков

### 👥 Групповой бюджет
- Создание семейных/групповых бюджетов
- Приглашение участников по ссылке или ID
- Распределение долей участников
- Агрегированная статистика по группе
- Просмотр расходов участников группы

### 💱 Конвертация валют
- Актуальные курсы валют (USD, EUR, RUB, MDL)
- Конвертация между валютами
- Поддержка различных базовых валют

### 🔔 Напоминания
- Создание напоминаний о платежах
- Указание суммы и даты платежа
- Автоматическая отправка уведомлений через SSE

### 📈 Аналитика и рекомендации
- Статистика доходов и расходов
- Визуализация данных с помощью Chart.js
- Персонализированные рекомендации по управлению бюджетом
- Прогнозирование расходов на основе истории

### 🔐 Безопасность
- Регистрация и аутентификация пользователей
- Сессионное управление с HTTP-only cookies
- Хеширование паролей
- Изоляция данных пользователей

### ⚡ Real-time обновления
- Server-Sent Events (SSE) для мгновенных обновлений
- Уведомления о превышении бюджета
- Автоматическое обновление данных в UI

## 🛠 Технологический стек

### Backend
- **Java 17** - основной язык программирования
- **Spring Boot 3.2.4** - фреймворк для создания приложений
  - Spring Web - REST API
  - Spring Data JPA - работа с базой данных
  - Spring WebFlux - поддержка SSE
  - Spring Validation - валидация данных
- **Hibernate 6.4.4** - ORM для работы с БД
- **SQLite 3.45.1** - встроенная база данных
- **Lombok** - уменьшение boilerplate кода
- **Maven** - система сборки

### Frontend
- **Vue.js 3.5.12** - прогрессивный JavaScript фреймворк
- **TypeScript 5.6.3** - типизированный JavaScript
- **Pinia 2.2.6** - state management для Vue
- **Chart.js 4.4.4** - библиотека для визуализации данных
- **Vite 5.4.10** - быстрый инструмент сборки

### База данных
- **SQLite** - легковесная встроенная БД
- Поддержка транзакций
- Автоматическое создание схемы через Hibernate

## 🏗 Архитектура

Приложение построено по многоуровневой архитектуре:

```
┌─────────────────────────────────────┐
│         Frontend (Vue.js)           │
│  - Components                       │
│  - Composables                      │
│  - Pinia Store                      │
└──────────────┬──────────────────────┘
               │ HTTP/SSE
┌──────────────┴──────────────────────┐
│      Controllers (REST API)         │
│  - AuthController                   │
│  - TransactionController            │
│  - BudgetController                 │
│  - GroupController                  │
│  - ReminderController               │
│  - CurrencyController               │
│  - CategorizerController            │
│  - SseController                    │
└──────────────┬──────────────────────┘
               │
┌──────────────┴──────────────────────┐
│         Services (Business Logic)   │
│  - AuthService                      │
│  - FinanceService                   │
│  - GroupService                     │
│  - CurrencyService                  │
│  - AutoCategorizerService           │
│  - SseService                       │
└──────────────┬──────────────────────┘
               │
┌──────────────┴──────────────────────┐
│      Repositories (Data Access)     │
│  - UserRepository                   │
│  - SessionRepository                │
│  - TransactionRepository            │
│  - BudgetRepository                 │
│  - GroupRepository                  │
│  - GroupMemberRepository            │
│  - ReminderRepository               │
└──────────────┬──────────────────────┘
               │
┌──────────────┴──────────────────────┐
│      Database (SQLite)              │
│  - Users                            │
│  - Sessions                         │
│  - Transactions                     │
│  - Budgets                          │
│  - Groups                           │
│  - GroupMembers                     │
│  - Reminders                        │
└─────────────────────────────────────┘
```

### Основные компоненты

#### Controllers
- Обработка HTTP запросов
- Валидация входных данных
- Формирование ответов
- Управление сессиями

#### Services
- Бизнес-логика приложения
- Координация между компонентами
- Обработка транзакций
- Вычисления и агрегация данных

#### Repositories
- Взаимодействие с базой данных
- CRUD операции
- Кастомные запросы

#### Entities
- `User` - пользователи системы
- `Session` - сессии пользователей
- `Transaction` - финансовые транзакции
- `Budget` - бюджеты по категориям
- `Group` - группы пользователей
- `GroupMember` - участники групп с долями
- `Reminder` - напоминания о платежах

## 📡 API эндпоинты

### Аутентификация

#### `POST /api/register`
Регистрация нового пользователя
- **Параметры**: `username`, `password`
- **Ответ**: `{ ok: boolean }`

#### `POST /api/login`
Вход в систему
- **Параметры**: `username`, `password`
- **Ответ**: `{ ok: boolean, user: { id, username } }`
- **Cookie**: устанавливает `SID`

#### `POST /api/logout`
Выход из системы
- **Ответ**: `{ ok: boolean }`

#### `GET /api/me`
Получение текущего пользователя
- **Ответ**: `{ ok: boolean, user?: { id, username } }`

### Транзакции

#### `POST /api/transaction/add`
Добавление транзакции
- **Параметры**: `date`, `amount`, `category?`, `description?`
- **Ответ**: `{ ok: boolean, id: string }`

#### `GET /api/transactions`
Получение списка транзакций
- **Параметры**: `from?`, `to?`, `category?`
- **Ответ**: `{ items: [{ id, date, category, description, amount }] }`

#### `POST /api/transaction/delete`
Удаление транзакции
- **Параметры**: `id`
- **Ответ**: `{ ok: boolean }`

### Бюджет

#### `POST /api/budget/set`
Установка лимита бюджета
- **Параметры**: `category`, `limit`
- **Ответ**: `{ ok: boolean, category, limit }`

#### `GET /api/budget`
Получение бюджетов
- **Ответ**: `{ items: [{ category, limit, spent }] }`

#### `POST /api/budget/delete`
Удаление бюджета
- **Параметры**: `category`
- **Ответ**: `{ ok: boolean }`

### Группы

#### `POST /api/group/create`
Создание группы
- **Параметры**: `name?`
- **Ответ**: `{ ok: boolean, groupId, name }`

#### `POST /api/group/join`
Присоединение к группе
- **Параметры**: `groupId`, `share?`
- **Ответ**: `{ ok: boolean }`

#### `GET /api/group/budget`
Получение группового бюджета
- **Ответ**: `{ items: [{ category, limit, spent }] }`

#### `GET /api/group/peers`
Получение участников группы
- **Параметры**: `from?`, `to?`
- **Ответ**: `{ items: [{ userId, username, income, expense }] }`

#### `POST /api/group/leave`
Выход из группы
- **Ответ**: `{ ok: boolean }`

#### `GET /api/group/me`
Информация о текущей группе
- **Ответ**: `{ ok: boolean, groupId?, groupName?, share? }`

### Напоминания

#### `POST /api/reminder/add`
Добавление напоминания
- **Параметры**: `dueDate`, `message?`, `amount?`
- **Ответ**: `{ ok: boolean }`

#### `GET /api/reminders`
Получение напоминаний
- **Ответ**: `{ items: [{ id, date, message, amount? }] }`

### Валюты

#### `GET /api/currency`
Получение курсов валют
- **Параметры**: `base?`, `symbols?`
- **Ответ**: `{ base, rates: { [currency]: rate } }`

#### `GET /api/currency/convert`
Конвертация валют
- **Параметры**: `from?`, `to?`, `amount?`, `date?`
- **Ответ**: `{ ok: boolean, from, to, amount, result }`

### Автокатегоризация

#### `GET /api/categorizer/suggest`
Предложение категории
- **Параметры**: `desc`
- **Ответ**: `{ category: string | null }`

### SSE

#### `GET /api/sse`
Server-Sent Events подключение
- **Ответ**: stream событий

#### `GET /api/health`
Проверка здоровья сервера
- **Ответ**: `{ status: "OK" }`

## 🚀 Установка и запуск

### Требования
- Java 17 или выше
- Maven 3.6+
- Node.js 16+ и npm

### Backend

1. Клонирование репозитория:
```bash
git clone <repository-url>
cd ACS
```

2. Сборка проекта:
```bash
mvn clean install
```

3. Запуск приложения:
```bash
mvn spring-boot:run
```

Backend будет доступен на `http://localhost:8080`

### Frontend

1. Переход в директорию фронтенда:
```bash
cd web
```

2. Установка зависимостей:
```bash
npm install
```

3. Запуск dev-сервера:
```bash
npm run dev
```

Frontend будет доступен на `http://localhost:5173`

4. Сборка для продакшена:
```bash
npm run build
```

## 📁 Структура проекта

```
ACS/
├── src/main/java/com/acs/finance/
│   ├── controller/          # REST контроллеры
│   │   ├── AuthController.java
│   │   ├── TransactionController.java
│   │   ├── BudgetController.java
│   │   ├── GroupController.java
│   │   ├── ReminderController.java
│   │   ├── CurrencyController.java
│   │   ├── CategorizerController.java
│   │   └── SseController.java
│   ├── service/             # Бизнес-логика
│   │   ├── AuthService.java
│   │   ├── FinanceService.java
│   │   ├── GroupService.java
│   │   ├── CurrencyService.java
│   │   ├── AutoCategorizerService.java
│   │   └── SseService.java
│   ├── repository/          # Репозитории JPA
│   │   ├── UserRepository.java
│   │   ├── SessionRepository.java
│   │   ├── TransactionRepository.java
│   │   ├── BudgetRepository.java
│   │   ├── GroupRepository.java
│   │   ├── GroupMemberRepository.java
│   │   └── ReminderRepository.java
│   ├── entity/              # JPA сущности
│   │   ├── User.java
│   │   ├── Session.java
│   │   ├── Transaction.java
│   │   ├── Budget.java
│   │   ├── Group.java
│   │   ├── GroupMember.java
│   │   └── Reminder.java
│   ├── dto/                 # Data Transfer Objects
│   ├── config/              # Конфигурация Spring
│   ├── exception/           # Обработка исключений
│   ├── model/               # Модели данных
│   └── scheduler/           # Планировщики задач
├── src/main/resources/
│   ├── application.properties   # Конфигурация приложения
│   └── static/                  # Статические ресурсы
├── web/                     # Frontend приложение
│   ├── src/
│   │   ├── components/      # Vue компоненты
│   │   │   ├── AuthForm.vue
│   │   │   ├── TransactionForm.vue
│   │   │   ├── TransactionList.vue
│   │   │   ├── BudgetManager.vue
│   │   │   ├── GroupBudget.vue
│   │   │   ├── CurrencyConverter.vue
│   │   │   ├── Reminders.vue
│   │   │   ├── ChartView.vue
│   │   │   ├── StatsCards.vue
│   │   │   ├── Recommendations.vue
│   │   │   └── Header.vue
│   │   ├── composables/     # Vue composables
│   │   ├── store/           # Pinia store
│   │   ├── api/             # API клиент
│   │   ├── utils/           # Утилиты
│   │   ├── App.vue          # Главный компонент
│   │   └── main.ts          # Точка входа
│   ├── public/              # Публичные файлы
│   ├── package.json
│   └── vite.config.ts
├── data/                    # База данных SQLite
├── logs/                    # Логи приложения
├── pom.xml                  # Maven конфигурация
└── README.md                # Документация
```

## 🔧 Конфигурация

### application.properties

Основные настройки приложения находятся в `src/main/resources/application.properties`:

- **Порт сервера**: `server.port=8080`
- **База данных**: `spring.datasource.url=jdbc:sqlite:./data/fintrack.db`
- **JPA**: автоматическое создание схемы включено
- **Логирование**: уровень INFO для приложения

### Environment Variables

Приложение поддерживает переменные окружения для гибкой конфигурации в разных средах.

## 🎨 Особенности реализации

### Автоматическая категоризация
Система автоматически предлагает категории для транзакций на основе описания, используя ключевые слова и паттерны.

### Групповое бюджетирование
Уникальная функция агрегации бюджетов участников группы с учетом их долей. Позволяет семьям и командам эффективно планировать общие расходы.

### Real-time уведомления
Использование SSE для мгновенной отправки уведомлений о важных событиях:
- Превышение бюджета
- Добавление транзакций
- Обновление данных

### Responsive Design
Frontend адаптирован для различных устройств и размеров экрана.

## 📝 Лицензия

Этот проект создан в образовательных целях.

## 👥 Авторы

Разработано в рамках учебного проекта по Java и веб-разработке.

---

**FinTrack** - управляйте финансами с умом! 💰📊
