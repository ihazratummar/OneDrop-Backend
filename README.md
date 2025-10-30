# onedrop

This project was created using the [Ktor Project Generator](https://start.ktor.io).

Here are some useful links to get you started:

- [Ktor Documentation](https://ktor.io/docs/home.html)
- [Ktor GitHub page](https://github.com/ktorio/ktor)
- The [Ktor Slack chat](https://app.slack.com/client/T09229ZC6/C0A974TJ9). You'll need to [request an invite](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up) to join.

## Features

Here's a list of features included in this project:

| Name                                                                   | Description                                                                        |
| ------------------------------------------------------------------------|------------------------------------------------------------------------------------ |
| [Content Negotiation](https://start.ktor.io/p/content-negotiation)     | Provides automatic content conversion according to Content-Type and Accept headers |
| [Routing](https://start.ktor.io/p/routing)                             | Provides a structured routing DSL                                                  |
| [kotlinx.serialization](https://start.ktor.io/p/kotlinx-serialization) | Handles JSON serialization using kotlinx.serialization library                     |
| [MongoDB](https://start.ktor.io/p/mongodb)                             | Adds MongoDB database to your application                                          |
| [GSON](https://start.ktor.io/p/ktor-gson)                              | Handles JSON serialization using GSON library                                      |

## Building & Running

To build or run the project, use one of the following tasks:

| Task                          | Description                                                          |
| -------------------------------|---------------------------------------------------------------------- |
| `./gradlew test`              | Run the tests                                                        |
| `./gradlew build`             | Build everything                                                     |
| `buildFatJar`                 | Build an executable JAR of the server with all dependencies included |
| `buildImage`                  | Build the docker image to use with the fat JAR                       |
| `publishImageToLocalRegistry` | Publish the docker image locally                                     |
| `run`                         | Run the server                                                       |
| `runDocker`                   | Run using the local docker image                                     |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:9091
```

# OneDrop-Backend


This project is the backend service for **OneDrop**, a blood donation app where users can join as blood donors
and request blood from the community. The backend is built using **Ktor** and **MongoDB**,
providing a RESTful API for managing donors and blood requests.

## App Feature

- User Registration & Blood Donor Management
- Blood Request Posting & Matching System
- Real-time Notifications using FCM
- Authorization
- Location-based Donor Search


## 📁 API Routes

### Blood Donor Endpoints
| Endpoint                          | Method | Description                                      |
| --------------------------------- | ------ | ------------------------------------------------ |
| `/api/donors/create-update-donor` | POST   | Create or update a blood donor profile          |
| `/api/donors/get-donors`          | GET    | Fetch all blood donors                          |
| `/api/donors/donor-profile?userId={id}` | GET    | Retrieve a donor's profile by user ID           |
| `/api/donors/is-donor-exist?userId={id}` | GET    | Check if a user is registered as a blood donor  |
| `/api/donors/toggle-availability?userId={id}&key={key}` | POST   | Toggle availability status of a donor           |

### Blood Request Endpoints

| Endpoint                                  | Method | Description                              |
| ---------------------------------------- | ------ | ---------------------------------------- |
| `/api/blood-request/create-blood-request` | POST   | Create a new blood request              |
| `/api/blood-request/get-blood-requests`   | GET    | Fetch all blood requests                |

# Developed & Maintained by 🚀
## [@ihazratummar](https://github.com/ihazratummar)