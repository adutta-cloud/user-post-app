package com.griddynamics.api_gateway;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;

public class MainVerticle extends AbstractVerticle {

  private WebClient webClient;

  private String userServiceHost = "localhost";
  private int userServicePort = 8888;

  private String postServiceHost = "localhost";
  private int postServicePort = 8889;

  public static void main(String[] args) {
    io.vertx.core.Vertx vertx = io.vertx.core.Vertx.vertx();
    vertx.deployVerticle(new MainVerticle());
  }

  @Override
  public void start(Promise<Void> startPromise) {
    // 1. Read Env Variables (For Docker/Kubernetes)
    if (System.getenv("USER_SERVICE_HOST") != null) {
      userServiceHost = System.getenv("USER_SERVICE_HOST");
    }
    if (System.getenv("POST_SERVICE_HOST") != null) {
      postServiceHost = System.getenv("POST_SERVICE_HOST");
    }
    // 2. Initialize WebClient
    webClient = WebClient.create(vertx);
    System.out.println("API Gateway started. Routing to User Service at " + userServiceHost + ":" + userServicePort +
      " and Post Service at " + postServiceHost + ":" + postServicePort);

    Router router = Router.router(vertx);

    // 3. Global Handlers (CORS + Body)
    router.route().handler(ctx -> {
      ctx.response().putHeader("Access-Control-Allow-Origin", "*");
      ctx.response().putHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
      ctx.response().putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
      if (ctx.request().method().name().equals("OPTIONS")) {
        ctx.response().setStatusCode(204).end();
      } else {
        ctx.next();
      }
    });

    router.route().handler(BodyHandler.create());

    // 4. Define Routes (Direct Mapping)
    router.get("/health").handler(ctx -> ctx.json(new JsonObject().put("status", "API Gateway UP")));

    // ==========================================
    // 🚦 USER SERVICE ROUTES (Port 8888)
    // ==========================================

    // 1. POST /register
    router.post("/register").handler(ctx -> proxyRequest(ctx, userServiceHost, userServicePort));

    // 2. POST /login
    router.post("/login").handler(ctx -> proxyRequest(ctx, userServiceHost, userServicePort));

    // 3. GET /users
    router.get("/users").handler(ctx -> proxyRequest(ctx, userServiceHost, userServicePort));

    // ==========================================
    // 📝 POST SERVICE ROUTES (Port 8889)
    // ==========================================

    // 4. POST /posts (Create a post)
    router.post("/posts").handler(ctx -> proxyRequest(ctx, postServiceHost, postServicePort));

    // 5. GET /posts (Get all posts)
    router.get("/posts").handler(ctx -> proxyRequest(ctx, postServiceHost, postServicePort));

    // 5. Start Server
    vertx.createHttpServer()
      .requestHandler(router)
      .listen(8080)
      .onSuccess(server -> {
        System.out.println("🚀 API Gateway started on port 8080");
        System.out.println("   Forwarding Users -> " + userServiceHost + ":" + userServicePort);
        System.out.println("   Forwarding Posts -> " + postServiceHost + ":" + postServicePort);
        startPromise.complete();
      })
      .onFailure(err -> System.err.println("Failed to start API Gateway: " + err.getMessage()));
  }

  private void proxyRequest(RoutingContext ctx, String host, int port) {
    String uri = ctx.request().uri();
    HttpMethod method = ctx.request().method();

    webClient.request(method, port, host, uri)
      .putHeaders(ctx.request().headers())
      .sendBuffer(ctx.body().buffer())
      .onSuccess(response -> {
        ctx.response()
          .setStatusCode(response.statusCode())
          .headers().setAll(response.headers()); // Copy response headers
        ctx.response().send(response.bodyAsBuffer());
      })
      .onFailure(err -> {
        System.err.println("❌ Proxy failed to " + host + ": " + err.getMessage());
        ctx.response().setStatusCode(502).end("Bad Gateway: " + err.getMessage());
      });
  }
}
