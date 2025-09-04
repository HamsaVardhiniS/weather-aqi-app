🌦️ Weather & AQI App

A Spring Boot + Vaadin application that provides weather forecasts and air quality index (AQI) data.
Built for Indian cities, but can be easily extended for global use.

✨ Features

📅 7-day weather forecast
🌤️ Current weather conditions
📜 Historical weather data
🌍 Current Air Quality Index (AQI)
🏙️ Historical AQI trends
🖥️ Clean Vaadin UI with Weather and AQI views

📂 Project Structure
src/main/java/com/myapp/weatheraqi
├── Application.java # Spring Boot entry point
├── backend/ # Backend logic (fetchers, services)
└── ui/ # UI views (WeatherView, AqiView, MainView)
pom.xml # Maven dependencies & build config

🛠️ Tech Stack
Java 17
Spring Boot 3.2
Vaadin 24
Maven
MySQL (for persistent storage)
Gson (for JSON parsing)

⚙️ Installation & Setup
1️⃣ Clone the repository
git clone https://github.com/your-username/weather-aqi-app.git
cd weather-aqi-app

2️⃣ Install dependencies
mvn clean install
This will automatically download all required dependencies (Spring Boot, Vaadin, Gson, MySQL driver, etc.) defined in pom.xml.

3️⃣ Run the application
mvn spring-boot:run

Access the app at:
http://localhost:8080/

🗄️ Database Configuration (Optional: MySQL)

If you want to store weather and AQI history:

Create a MySQL database:
CREATE DATABASE weather_aqi;

Update application:

spring.datasource.url=jdbc:mysql://localhost:3306/weather_aqi
spring.datasource.username=root
spring.datasource.password=yourpassword
spring.jpa.hibernate.ddl-auto=update
Restart the app. Tables will be auto-generated.

📦 Packaging for Production

To create a runnable JAR:

mvn clean package
java -jar target/weather-aqi-app-1.0-SNAPSHOT.jar

👨‍💻 Development Notes

Version controlled:

/src/main/java/com/myapp/weatheraqi/backend
/src/main/java/com/myapp/weatheraqi/ui
Application.java, pom.xml
Ignored by Git: build artifacts (/target), IDE configs, logs, and auto-generated frontend code.
Uses .gitignore configured for Java + Maven + Vaadin projects.
