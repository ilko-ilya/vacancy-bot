# 🤖 Vacancy Search Bot

![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring--Boot-4.0.3-green?style=for-the-badge&logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)

An efficient Telegram bot built with **Java 21** and **Spring Boot 4.0.3** to automate IT job searching. The bot aggregates the latest vacancies from multiple major platforms into one convenient feed.

## 🛠 Tech Stack
* **Language:** Java 21
* **Framework:** Spring Boot 4.0.3
* **Database:** PostgreSQL
* **Libraries:** Spring Data JPA, Hibernate, Jsoup (for web scraping), Telegram Bots Spring Boot Starter
* **Build Tool:** Maven

## 🔍 Supported Platforms
The bot performs multi-threaded parsing of the following resources:
* **DOU.ua**
* **Djinni.co**
* **GlobalLogic**
* **DataArt**

## ✨ Key Features
* **Real-time Parsing:** Simultaneously collects data from 5+ job boards.
* **Smart Filtering:** Filter vacancies by tech stack, experience level, and location.
* **Duplicate Protection:** Stores vacancy history in PostgreSQL to ensure you only see new postings.
* **Instant Notifications:** Get immediate alerts in Telegram when a matching job is found.

## 🚀 Getting Started

### Prerequisites
* **JDK 21**
* **PostgreSQL 14+**
* **Telegram Bot Token**

### Local Installation
1. Clone the repository:
   ```bash
   git clone [https://github.com/ilko-ilya/vacancy-bot.git](https://github.com/ilko-ilya/vacancy-bot.git)