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
    router.post("/posts/:id/like").handler(this::likePost);

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

    // 1. Validate required fields (Added title and authorName)
    if (body == null || !body.containsKey("authorId") || !body.containsKey("content") || !body.containsKey("title")) {
      ctx.response().setStatusCode(400).end("authorId, title, and content are required");
      return;
    }

    // 2. Create Post Object using the NEW Constructor
    // Post(String title, String content, int authorId, String authorName)
    Post newPost = new Post(
      body.getString("title"),
      body.getString("content"),
      body.getInteger("authorId"),
      body.getString("authorName") // Can be null if not sent, but better to send it
    );

    // 3. Save to DB
    postRepository.save(newPost)
      .onSuccess(v -> {
        // 4. Trigger Kafka Event
        // Ensure your variable name matches (kafkaProducerService vs kafkaProducer)
        kafkaProducer.sendPostCreatedEvent((long) newPost.getAuthorId());

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
        // This will now include 'likes', 'title', and 'authorName' automatically
        // because we updated the Post.toJson() method earlier.
        posts.forEach(p -> response.add(p.toJson()));

        ctx.json(response);
      })
      .onFailure(err -> ctx.response().setStatusCode(500).end(err.getMessage()));
  }

  private void likePost(RoutingContext ctx) {
    String idParam = ctx.pathParam("id");
    Long postId;

    // 1. Parse Post ID
    try {
      postId = Long.parseLong(idParam);
    } catch (NumberFormatException e) {
      ctx.response().setStatusCode(400).end("Invalid Post ID");
      return;
    }

    // 2. Extract User ID (Sent from Frontend/Gateway)
    // If missing, we default to 0 (Anonymous)
    String userIdStr = ctx.request().getHeader("userId");
    Long userId = (userIdStr != null) ? Long.parseLong(userIdStr) : 0L;

    // 3. Update DB First (Atomic Increment)
    postRepository.likePost(postId)
      .compose(v -> {
        // 4. If DB Success -> Send Kafka Event
        return kafkaProducer.sendLikeEvent(postId, userId);
      })
      .onSuccess(v -> {
        // 5. Return Success to Client
        ctx.response()
          .putHeader("Content-Type", "application/json")
          .end(new JsonObject().put("message", "Post Liked").put("postId", postId).encode());
      })
      .onFailure(err -> {
        if (err.getMessage().contains("Post not found")) {
          ctx.response().setStatusCode(404).end("Post Not Found");
        } else {
          ctx.response().setStatusCode(500).end(err.getMessage());
        }
      });
  }
}
