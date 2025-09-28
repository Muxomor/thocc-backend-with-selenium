# Keyboard News Aggregator API

This is a Ktor-based backend service designed to aggregate news from the mechanical keyboard community. It currently parses two primary sources: the Geekhack forum (via RSS) and ZFrontier (a dynamic website). The service stores findings in a database, prevents duplicates, and posts new content to a Telegram channel.

This project was initially the API for a larger application intended for inventory management, but its scope was later focused on news aggregation.

-----

## Features

  * **Source Parsing**:
    * **Geekhack**: Parses RSS feeds for "Group Buys" and "Interest Checks" using Ktor Client and Jsoup.
    * **ZFrontier**: Uses Selenium and a headless Firefox browser to render and parse dynamically-loaded JavaScript content.
        * **Automatic Translation**: Since ZFrontier is a Chinese forum, all post titles are automatically translated into English using the [Free Translate API (by cuberkam)](https://github.com/cuberkam/free_translate_api) to make them universally understandable.
  * **Background Jobs**: Parsing tasks run as scheduled coroutine jobs to check for new content periodically.
  * **Database Storage**: Persists news articles in a PostgreSQL database to track posted content and avoid duplicates.
  * **Telegram Integration**: Automatically sends new articles to a configured Telegram channel, supporting text messages and media groups.
  * **REST API**: Exposes endpoints for querying the stored news data.

-----

## Technology Stack

  * **Backend**: Ktor `3.0.3`
  * **Language**: Kotlin `1.9.0`
  * **Database**: PostgreSQL with Ktorm `4.1.1`
  * **Web Scraping**: Jsoup `2.3.0` and Selenium `4.18.0`
  * **Dependency Injection**: Koin `3.5.6`
  * **Containerization**: Docker & Docker Compose
  * **Build Tool**: Gradle

-----

## Installation and Setup Guide

### Prerequisites

  * Docker
  * Docker Compose
  * A running PostgreSQL instance

### Step 1: Database Setup

Connect to your PostgreSQL instance and execute the following SQL to create the necessary tables and populate the news sources.

```sql
CREATE TABLE newssource (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

CREATE TABLE news (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL,
    originalname TEXT NOT NULL,
    source_id INTEGER NOT NULL REFERENCES newssource(id),
    link TEXT NOT NULL UNIQUE,
    timestamp TEXT NOT NULL
);

INSERT INTO newssource (id, name) VALUES
(1, 'GH'),
(2, 'ZF'),
(3, 'Other');
```

### Step 2: Configure Environment

Create a `.env` file in the project root. Populate it with your configuration details:

```dotenv
DB_URL="jdbc:postgresql://HOST:PORT/DATABASE_NAME"
DB_USER="YOUR_DB_USER"
DB_PASSWORD="YOUR_DB_PASSWORD"

TELEGRAM_BOT_TOKEN="YOUR_TELEGRAM_BOT_TOKEN"
TELEGRAM_CHAT_ID="@YourChannelName"
```

### Step 3: Build and Run


```bash
docker-compose up --build
```

-----

## Architectural Notes

**ARM Architecture**: The provided `Dockerfile` is configured for an **ARM/aarch64** architecture. To run this on an x86/amd64 machine, you must modify the `Dockerfile` to download the appropriate `geckodriver` version.
