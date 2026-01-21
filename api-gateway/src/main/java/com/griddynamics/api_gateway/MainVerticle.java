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
import io.vertx.ext.web.handler.CorsHandler;

import java.util.HashSet;
import java.util.Set;

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

    // ====================================================
    // 🛑 3. PROPER CORS CONFIGURATION (Use Official Handler)
    // ====================================================
    Set<String> allowedHeaders = new HashSet<>();
    allowedHeaders.add("x-requested-with");
    allowedHeaders.add("Access-Control-Allow-Origin");
    allowedHeaders.add("origin");
    allowedHeaders.add("Content-Type");
    allowedHeaders.add("accept");
    allowedHeaders.add("Authorization");

    Set<HttpMethod> allowedMethods = new HashSet<>();
    allowedMethods.add(HttpMethod.GET);
    allowedMethods.add(HttpMethod.POST);
    allowedMethods.add(HttpMethod.OPTIONS);
    allowedMethods.add(HttpMethod.PUT);
    allowedMethods.add(HttpMethod.DELETE);

    // This handles the OPTIONS check automatically AND adds headers to responses
    router.route().handler(CorsHandler.create()
      .addOrigin("http://localhost:5173") // Your Frontend URL
      .allowedHeaders(allowedHeaders)
      .allowedMethods(allowedMethods));

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

    // 4. GET /users/:id
    router.get("/users/:id").handler(ctx -> proxyRequest(ctx, userServiceHost, userServicePort));

    // ==========================================
    // 📝 POST SERVICE ROUTES (Port 8889)
    // ==========================================

    // 4. POST /posts (Create a post)
    router.post("/posts").handler(ctx -> proxyRequest(ctx, postServiceHost, postServicePort));

    // 5. GET /posts (Get all posts)
    router.get("/posts").handler(ctx -> proxyRequest(ctx, postServiceHost, postServicePort));

    // 6. POST /posts/:id/like (Like a post)
    router.post("/posts/:id/like").handler(ctx -> proxyRequest(ctx, postServiceHost, postServicePort));

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

        System.out.println("✅ Backend responded: " + response.statusCode() + " for " + uri);

        // 1. Set Status Code
        ctx.response().setStatusCode(response.statusCode());

        // 2. Copy headers from Backend (BUT SKIP CORS HEADERS to avoid conflicts)
        response.headers().forEach(entry -> {
          String headerName = entry.getKey().toLowerCase();
          if (!headerName.startsWith("access-control-allow")) {
            ctx.response().putHeader(entry.getKey(), entry.getValue());
          }
        });

        // 3. FORCE CORS HEADERS (The "Nuclear" Fix) ☢️
        // This ensures the browser ALWAYS sees these permissions
        ctx.response().putHeader("Access-Control-Allow-Origin", "http://localhost:5173");
        ctx.response().putHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE");
        ctx.response().putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

        System.out.println("👉 Added CORS headers for: " + uri);

        // 4. Send Body
        if (response.body() != null) {
          ctx.response().send(response.bodyAsBuffer());
        } else {
          ctx.response().end();
        }
      })
      .onFailure(err -> {
        System.err.println("❌ Proxy failed to " + host + ": " + err.getMessage());
        ctx.response().setStatusCode(502).end("Bad Gateway: " + err.getMessage());
      });
  }
}
