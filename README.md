# 🌦️ Weather & AQI App

A **Spring Boot + Vaadin** application that provides **weather forecasts** and **air quality index (AQI) data**.  
Built for Indian cities, but easily extendable for global use.

---

## ✨ Features

- 📅 **7-day weather forecast**
- 🌤️ **Current weather conditions**
- 📜 **Historical weather data**
- 🌍 **Current Air Quality Index (AQI)**
- 🏙️ **Historical AQI trends**
- 🖥️ **Clean Vaadin UI** with Weather and AQI views

---

## 📂 Project Structure

src/main/java/com/myapp/weatheraqi
├── Application.java # Spring Boot entry point
├── backend/ # Backend logic (fetchers, services)
└── ui/ # UI views (WeatherView, AqiView, MainView)
pom.xml # Maven dependencies & build config

---

## 🛠️ Tech Stack

- ☕ **Java 17**
- 🚀 **Spring Boot 3.2**
- 🎨 **Vaadin 24**
- 🏗️ **Maven**
- 🗄️ **MySQL** (for persistent storage)
- 📦 **Gson** (for JSON parsing)

---

## ⚙️ Installation & Setup

### 1️⃣ Clone the repository

```sh
git clone https://github.com/your-username/weather-aqi-app.git
cd weather-aqi-app
```

### 2️⃣ Install dependencies

mvn clean install

### 3️⃣ Run the application

mvn spring-boot:run

Access the app at 👉 http://localhost:8080/
