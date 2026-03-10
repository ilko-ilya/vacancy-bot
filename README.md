# 🤖 Vacancy Search Bot

![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring--Boot-4.x-green?style=for-the-badge&logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)

A real-time Telegram bot built with **Java 21** and **Spring Boot 4** that aggregates IT vacancies from multiple platforms, filters them according to predefined criteria, and instantly notifies users about new relevant opportunities — ensuring zero duplicates via PostgreSQL.

---

## 🧠 Architecture Overview

```text
External Platforms
       ↓
Platform Parsers
       ↓
Aggregation Service
       ↓
PostgreSQL (Deduplication Layer)
       ↓
Telegram Notification Service
```

- Each platform is implemented as an isolated parser.
- Failures in one source do not break the whole system.
- Vacancies are processed immediately after detection (no scheduled batching).

---

## 🛠 Tech Stack

- **Language:** Java 21
- **Framework:** Spring Boot 4 (Web, WebFlux)
- **Database:** PostgreSQL
- **ORM:** Spring Data JPA / Hibernate
- **Parsing:** Jsoup (HTML scraping), Jackson (JSON/GraphQL parsing)
- **Integration:** Telegram Bot API
- **Build Tool:** Maven

---

## 🔍 Supported Platforms

The bot performs concurrent parsing of:

- Robota.ua (via GraphQL API)
- DOU.ua
- Djinni.co
- GlobalLogic
- DataArt

Integration method depends on platform capabilities:
- Public/Private API (GraphQL, REST)
- HTML parsing via Jsoup

---

## ✨ Key Features

This bot automates the routine of job hunting by providing the following core capabilities:

### ⚡ Real-Time Vacancy Processing
New vacancies are processed immediately after discovery and sent to Telegram without delay.

### 🎯 Smart Filtering
- **Advanced Experience Filtering:** Smart parsing to exclude Senior (4+ years) roles, including deep DOM parsing for hidden requirements and handling mixed text formats.
- **Noise Reduction:** Strict keyword blacklist to instantly drop irrelevant matches (e.g., JavaScript, 1C, Cyber Security, DevOps) often returned by inaccurate search engines.
- **Tech Stack & Location:** Precise filtering by specific Java/Spring requirements and remote/on-site formats.

### 🛡 Duplicate Protection & Data Management
- **Zero Spam:** Vacancy history is stored in PostgreSQL with unique constraints to prevent duplicate notifications.
- **Data Lifecycle Management:** Automated background jobs (Spring Scheduler) clean up vacancies older than 30 days to optimize database storage and server resources.

### 📩 Instant Telegram Notifications

**Example message:**
> 🔥 **Новая вакансия:** Java Developer / Back end Engineer<br>
> 🏢 **Компания:** GlobalLogic<br>
> 📍 **Локация:** Kyiv / Remote<br>
> 🎓 **Уровень:** Middle<br>
> ⏳ **Опыт:** от 2-х лет<br>
> 📅 **Опубликовано:** 2 March<br>
> 🔗 **View Source**
---

## 🚀 Deployment & CI/CD

The project features a fully automated deployment pipeline:
- **Hosting:** Deployed on a **Google Cloud Platform (GCP)** virtual machine.
- **CI/CD Pipeline:** Configured via **GitHub Actions**. Every push to the `main` branch automatically triggers code checkout, Maven build, and zero-downtime deployment to the GCP server via SSH.

## 🚀 Getting Started

### Prerequisites

- JDK 21
- PostgreSQL 14+
- Telegram Bot Token

---

## 🔐 Environment Variables

Ensure the following environment variables are set before running the application:

```bash
DB_URL=jdbc:postgresql://localhost:5432/vacancydb
DB_USERNAME=your_db_user
DB_PASSWORD=your_db_password
BOT_TOKEN=your_telegram_bot_token
CHAT_ID=your_telegram_chat_id
```

---

## 💻 Local Installation

Clone the repository and run the application:

```bash
git clone https://github.com/ilko-ilya/vacancy-bot.git
cd vacancy-bot

# Build the project
./mvnw clean package -DskipTests

# Run the bot (Ensure environment variables from the section above are set)
DB_PASSWORD=your_db_password BOT_TOKEN=your_telegram_bot_token CHAT_ID=your_telegram_chat_id java -jar target/vacancy-bot-0.0.1-SNAPSHOT.jar