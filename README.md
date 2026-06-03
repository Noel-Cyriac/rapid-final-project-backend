# ⚙️ DriftBox Backend

DriftBox Backend is the server-side application for the DriftBox file sharing and cloud storage platform, built for the Rapid Rise Final Project. It provides secure authentication, file and folder management, sharing, storage tracking, notifications, and user management through REST APIs.

Built with **Spring Boot, Spring Security, JWT Authentication, JPA/Hibernate, and MySQL**.

## 🛠️ Tech Stack

- Java
- Spring Boot
- Spring Security
- JWT Authentication
- Spring Data JPA / Hibernate
- MySQL
- Maven
- REST APIs

## 📦 Installation

Clone the repository and install dependencies:

```bash
mvn clean install
```

## ⚙️ Configure Environment

Update your database and application configuration in:

```txt
src/main/resources/application.properties
```

Example configuration:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/driftbox
spring.datasource.username=your_username
spring.datasource.password=your_password

jwt.secret=your_secret_key
jwt.expiration=86400000
```

## ▶️ Run the Application

Start the Spring Boot server:

```bash
mvn spring-boot:run
```

Or run the main application class from your IDE.

The backend will run on:

```txt
http://localhost:8080
```
