package com.griddynamics.post_service;

import com.griddynamics.post_service.entities.Post;
import com.griddynamics.post_service.repositories.PostRepository;
import io.vertx.core.AbstractVerticle;
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

  private PostRepository postRepository;
  private KafkaProducerService kafkaProducer;

  public static void main(String[] args) {
    io.vertx.core.Vertx vertx = io.vertx.core.Vertx.vertx();
    vertx.deployVerticle(new MainVerticle());
  }

  @Override
  public void start(Promise<Void> startPromise) throws Exception {

    kafkaProducer = new KafkaProducerService(vertx);
    // 1. Database Setup (H2)
    // Note: This is a separate DB instance in memory (jdbc:h2:mem:post_db)
    JDBCConnectOptions dbConfig = new JDBCConnectOptions()
      .setJdbcUrl("jdbc:h2:mem:post_db;DB_CLOSE_DELAY=-1")
      .setUser("sa")
      .setPassword("");

    Pool dbClient = JDBCPool.pool(vertx, dbConfig, new PoolOptions().setMaxSize(5));
    postRepository = new PostRepository(dbClient);

    // 2. Router Setup
    Router router = Router.router(vertx);
    router.route().handler(BodyHandler.create());

    router.get("/health").handler(ctx -> ctx.json(new JsonObject().put("status", "UP")));

    // API Routes
    router.post("/posts").handler(this::createPost);
    router.get("/posts").handler(this::getAllPosts);

    // 3. Initialize DB -> Start Server
    postRepository.initData()
      .onFailure(startPromise::fail)
      .onSuccess(v -> {
        vertx.createHttpServer()
          .requestHandler(router)
          .listen(8889) // Port 8889
          .onSuccess(server -> {
            System.out.println("Post Service started on port 8889");
            startPromise.complete();
          })
          .onFailure(startPromise::fail);
      });
  }

  // --- Handlers ---

  private void createPost(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();

    // Validation
    if (body == null || !body.containsKey("authorId") || !body.containsKey("content")) {
      ctx.response().setStatusCode(400).end("authorId and content are required");
      return;
    }

    Post newPost = new Post(
      body.getLong("authorId"),
      body.getString("content")
    );

    postRepository.save(newPost)
      .onSuccess(v -> {
        kafkaProducer.sendPostCreatedEvent(newPost.getAuthorId());
        ctx.response().setStatusCode(201).end(new JsonObject().put("message", "Post Created").encode());
      })
      .onFailure(err -> {
          System.err.println("DB Save Failed: " + err.getMessage());
          ctx.response().setStatusCode(500).end(err.getMessage());
        }
      );
  }

  private void getAllPosts(RoutingContext ctx) {
    postRepository.findAll()
      .onSuccess(posts -> {
        JsonArray response = new JsonArray();
        posts.forEach(p -> response.add(p.toJson()));
        ctx.json(response);
      })
      .onFailure(err -> ctx.response().setStatusCode(500).end(err.getMessage()));
  }
}
