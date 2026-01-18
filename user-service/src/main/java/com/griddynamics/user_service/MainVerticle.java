package com.griddynamics.user_service;

import com.griddynamics.user_service.entities.User;
import com.griddynamics.user_service.repositories.UserRepository;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
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
  private JWTAuth jwtAuth;

  public static void main(String[] args) {
    io.vertx.core.Vertx vertx = io.vertx.core.Vertx.vertx();
    vertx.deployVerticle(new MainVerticle());
  }

  @Override
  public void start(Promise<Void> startPromise) throws Exception {

    jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions()
      .addPubSecKey(new PubSecKeyOptions()
        .setAlgorithm("HS256")
        .setBuffer("my-secret-password-key-1234567890")));

    JDBCConnectOptions dbConfig = new JDBCConnectOptions()
      .setJdbcUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")
      .setUser("sa")
      .setPassword("");

    Pool dbClient = JDBCPool.pool(vertx, dbConfig, new PoolOptions().setMaxSize(5));
    userRepository = new UserRepository(dbClient);

    userRepository.initData()
      .compose(v -> {
        // Init Kafka Consumer (It starts listening immediately)
        kafkaConsumer = new KafkaConsumerService(vertx, userRepository);
        kafkaConsumer.start();

        return startHttpServer();
      })
      .onSuccess(v -> {
        System.out.println("✅ User Service Ready (DB + Kafka + HTTP)");
        startPromise.complete();
      })
      .onFailure(err -> {
        System.err.println("❌ Startup Failed: " + err.getMessage());
        startPromise.fail(err);
      });
  }

  private Future<Void> startHttpServer() {
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    // Routes
    router.get("/health").handler(ctx -> ctx.json(new JsonObject().put("status", "UP")));
    router.post("/register").handler(this::registerUser);
    router.post("/login").handler(this::loginUser);
    router.get("/users").handler(this::getAllUsers);

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
          ctx.response().setStatusCode(401).end(new JsonObject().put("message", "Invalid Credentials").encode());
        } else {

          // ✨ Generate JWT Token ✨
          String token = jwtAuth.generateToken(
            new JsonObject()
              .put("sub", user.getUsername())  // Subject (Username)
              .put("userId", user.getId()),    // Custom Claim (User ID)
            new JWTOptions().setExpiresInSeconds(3600) // Expires in 1 hour
          );

          // Return Token to Client
          ctx.json(new JsonObject()
            .put("message", "Login Success")
            .put("token", token)
            .put("userId", user.getId())
            .put("username", user.getUsername())
          );
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
