package com.griddynamics.user_service;

import com.griddynamics.user_service.entities.User;
import com.griddynamics.user_service.repositories.UserRepository;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

public class MainVerticle extends AbstractVerticle {

  private UserRepository userRepository;
  private KafkaConsumerService kafkaConsumer;

  public static void main(String[] args) {
    io.vertx.core.Vertx vertx = io.vertx.core.Vertx.vertx();
    vertx.deployVerticle(new MainVerticle());
  }

  @Override
  public void start(Promise<Void> startPromise) throws Exception {

    // 1. Setup DB
    JDBCConnectOptions dbConfig = new JDBCConnectOptions()
      .setJdbcUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")
      .setUser("sa")
      .setPassword("");

    Pool dbClient = JDBCPool.pool(vertx, dbConfig, new PoolOptions().setMaxSize(5));
    userRepository = new UserRepository(dbClient);

    // 2. Async Chain: Init DB -> Init Kafka -> Start Server
    userRepository.initData()
      .compose(v -> {
        // Init Kafka Consumer (It starts listening immediately)
        kafkaConsumer = new KafkaConsumerService(vertx, userRepository);
        kafkaConsumer.start();

        // Start HTTP Server
        return startHttpServer();
      })
      .onSuccess(v -> {
        System.out.println("User Service Ready (DB + Kafka + HTTP)");
        startPromise.complete();
      })
      .onFailure(err -> {
        System.err.println("Startup Failed: " + err.getMessage());
        startPromise.fail(err);
      });
  }

  // --- Refactored: Returns Future instead of taking Promise ---
  private Future<Void> startHttpServer() {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    // Routes
    router.get("/health").handler(ctx -> ctx.json(new JsonObject().put("status", "UP")));
    router.post("/register").handler(this::registerUser);
    router.post("/login").handler(this::loginUser);
    router.get("/users").handler(this::getAllUsers);

    // Create Server and return Future
    return vertx.createHttpServer()
      .requestHandler(router)
      .listen(8888)
      .mapEmpty(); // Converts Future<HttpServer> to Future<Void>
  }

  // --- Handlers ---

  private void registerUser(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();

    if (body == null || body.getString("username") == null) {
      ctx.response().setStatusCode(400).end("Username is required");
      return;
    }

    User newUser = new User(
      body.getString("username"),
      body.getString("email"),
      body.getString("password")
    );

    userRepository.save(newUser)
      .onSuccess(v -> ctx.response().setStatusCode(201).end(new JsonObject().put("message", "User Registered").encode()))
      .onFailure(err -> ctx.response().setStatusCode(500).end(err.getMessage()));
  }

  private void loginUser(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    String username = body.getString("username");
    String password = body.getString("password");

    userRepository.findByUsername(username)
      .onSuccess(user -> {
        if (user == null || !user.getPassword().equals(password)) {
          ctx.response().setStatusCode(401).end("Invalid Credentials");
        } else {
          ctx.json(new JsonObject().put("message", "Login Success").put("userId", user.getId()));
        }
      })
      .onFailure(err -> ctx.response().setStatusCode(500).end(err.getMessage()));
  }

  private void getAllUsers(RoutingContext ctx) {
    userRepository.findAll()
      .onSuccess(users -> {
        JsonArray response = new JsonArray();
        users.forEach(u -> response.add(u.toJson()));
        ctx.json(response);
      })
      .onFailure(err -> ctx.response().setStatusCode(500).end(err.getMessage()));
  }
}
