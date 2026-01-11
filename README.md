# Event-Driven Microservices POC

![Java](https://img.shields.io/badge/Java-17-orange?style=flat-square)
![Vert.x](https://img.shields.io/badge/Vert.x-5.0.6-purple?style=flat-square)
![Kafka](https://img.shields.io/badge/Apache%20Kafka-7.4-black?style=flat-square)
![Kubernetes](https://img.shields.io/badge/Kubernetes-Minikube-blue?style=flat-square)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat-square)

This Proof of Concept (POC) demonstrates a fully containerized, event-driven microservices architecture. It uses **Vert.x 5** for high-performance reactive services and **Apache Kafka** for asynchronous communication, orchestrated by **Kubernetes (Minikube)**.

---

## 📋 Table of Contents
- [Architecture Overview](#-architecture-overview)
- [Tech Stack](#-tech-stack)
- [Prerequisites](#-prerequisites)
- [Option 1: Run with Docker Compose (Local)](#-option-1-run-with-docker-compose-local)
- [Option 2: Run with Kubernetes (Scalable)](#-option-2-run-with-kubernetes-scalable)
- [API Usage & Testing](#-api-usage--testing)
- [Troubleshooting](#-troubleshooting)

---

## 🏗 Architecture Overview

The system consists of two isolated microservices:

1.  **User Service (Port 8888)**:
    * Manages user registration and profiles using an H2 in-memory database.
    * **Consumer:** Listens to the `post-created-topic` Kafka topic.
    * Updates the user's `postCount` in real-time when an event is received.

2.  **Post Service (Port 8889)**:
    * Allows creating new posts.
    * **Producer:** Publishes a `PostCreatedEvent` to Kafka whenever a post is successfully created.

3.  **Infrastructure**:
    * **Kafka**: Message broker for decoupling services.
    * **Zookeeper**: Manages Kafka cluster state.

---

## 🛠 Tech Stack

* **Language:** Java 17
* **Framework:** Vert.x 5.0.6 (Reactive)
* **Build Tool:** Maven 3.9+
* **Containerization:** Docker (Multistage builds)
* **Orchestration:** Kubernetes (Minikube)
* **Messaging:** Apache Kafka & Zookeeper

---

## 📦 Prerequisites

Ensure you have the following installed before starting:

* [Docker Desktop](https://www.docker.com/products/docker-desktop/)
* [Minikube](https://minikube.sigs.k8s.io/docs/start/)
* [Kubectl](https://kubernetes.io/docs/tasks/tools/)
* Java 17 JDK & Maven (for local compilation)

---

## 🚀 Option 1: Run with Docker Compose (Local)

Use this for quick local testing without Kubernetes overhead.

1.  **Build the project & containers:**
    ```bash
    docker-compose build --no-cache
    ```

2.  **Start the infrastructure and services:**
    ```bash
    docker-compose up -d
    ```

3.  **Verify running containers:**
    ```bash
    docker ps
    ```
    *You should see `grid-user-service`, `grid-post-service`, `grid-kafka`, and `grid-zookeeper`.*

4.  **View Logs:**
    ```bash
    docker logs -f grid-user-service
    ```

5.  **Stop:**
    ```bash
    docker-compose down
    ```

---

## ☸️ Option 2: Run with Kubernetes (Scalable)

This section deploys the application to a Minikube cluster, simulating a production environment.

### 1. Start Cluster
Start Minikube with sufficient RAM for Kafka (Critical step).
```bash
minikube start --memory=4096 --cpus=2
```

### 2. Load Images
Since Minikube runs in a separate VM, we must load the locally built Docker images into it.

```Bash

# 1. Tag images to match K8s manifests
docker tag user-post-app-user-service:latest grid-user-service:latest
docker tag user-post-app-post-service:latest grid-post-service:latest

# 2. Load into Minikube
minikube image load grid-user-service:latest
minikube image load grid-post-service:latest
```

### 3. Deploy Resources
Apply the configuration files for Infrastructure (Kafka/Zookeeper) and Applications.

```Bash

kubectl apply -f k8s/infrastructure.yaml
kubectl apply -f k8s/apps.yaml
```

### 4. Wait for Startup
Monitor the pods until all status show Running.

```Bash

kubectl get pods -w
```

Note: Kafka may restart once or twice during initialization. This is normal.

### 5. Initialize Topic (One-time Setup)
Manually create the topic to ensure immediate availability.

```Bash

kubectl exec -it $(kubectl get pod -l app=kafka -o jsonpath='{.items[0].metadata.name}') -- kafka-topics --bootstrap-server localhost:9092 --create --topic post-created-topic --partitions 1 --replication-factor 1
```

### 6. Expose Network
Open a tunnel to access the services via localhost. Keep this terminal window open.

```Bash

minikube tunnel
```

## 🧪 API Usage & Testing
Use Postman or curl to verify the Event-Driven flow.

### 1. Create a User (User Service)

* **Method:** POST

* **URL:** http://localhost:8888/register

* **Body:**

```JSON
{
    "username": "demo_user",
    "email": "demo@example.com",
    "password": "securePass123"
}
```
* **Response:** `201 Created`


### 2. Create a Post (Post Service)

* **Method:** POST

* **URL:** http://localhost:8889/posts

* **Body:**

```JSON
{
    "authorId": 1,
    "content": "Hello Kubernetes World!"
}
```
* **Response:** `201 Created`

### 3. Verify Event Processing (User Service)
Check if the user's post count incremented automatically via Kafka.

* **Method:** GET

* **URL:** http://localhost:8888/users

* **Expected Output:**

```JSON
[
    {
        "id": 1,
        "username": "demo_user",
        "postCount": 1  // <--- This confirms the event was processed
    }
]
```

---

## 🔧 Troubleshooting
### Kafka Pod "CrashLoopBackOff" or Error
* **Cause:** Insufficient RAM allocated to Minikube or Kafka Heap size too large.

* **Fix:** Ensure Minikube was started with --memory=4096. Ensure infrastructure.yaml has KAFKA_HEAP_OPTS: "-Xmx512M -Xms512M".

* Check Logs: kubectl logs -l app=kafka --previous

### "Topic not present in metadata"
* **Cause:** The application started before Kafka was fully ready.

* **Fix:** Restart the application pods to trigger a reconnection.

```Bash
kubectl rollout restart deployment/post-service
```

### Changes in code not reflecting?
* **Cause:** Minikube is using the old cached image.

* **Fix:**

    1. docker-compose build (Rebuild locally)

    2. minikube image load grid-user-service:latest (Push new image)

    3. kubectl rollout restart deployment/user-service (Restart pod)