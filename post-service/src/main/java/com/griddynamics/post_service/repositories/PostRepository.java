package com.griddynamics.post_service.repositories;

import com.griddynamics.post_service.entities.Post;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;
import io.vertx.sqlclient.Row;
import java.util.ArrayList;
import java.util.List;

public class PostRepository {

  private final Pool dbClient;

  public PostRepository(Pool dbClient) {
    this.dbClient = dbClient;
  }

  // 1. Initialize Schema
  public Future<Void> initData() {
    String schema = "CREATE TABLE IF NOT EXISTS posts (" +
      "id IDENTITY PRIMARY KEY, " +
      "title VARCHAR(255)," +
      "content VARCHAR(1000), " +
      "author_id INT, " +
      "author_name VARCHAR(255)," +
      "likes INT DEFAULT 0 )";
    return dbClient.query(schema).execute().mapEmpty();
  }

  // 2. Create Post
  public Future<Void> save(Post post) {
    String sql = "INSERT INTO posts (title, content, author_id, author_name, likes) VALUES (?, ?, ?, ?, ?)";

    return dbClient.preparedQuery(sql)
      .execute(Tuple.of(
        post.getTitle(),
        post.getContent(),
        post.getAuthorId(),
        post.getAuthorName(),
        post.getLikes() // This will usually be 0
      ))
      .mapEmpty();
  }

  // 3. Find All Posts
  public Future<List<Post>> findAll() {
    return dbClient.query("SELECT * FROM posts")
      .execute()
      .map(rows -> {
        List<Post> posts = new ArrayList<>();
        for (Row row : rows) {
          // Use the helper method to map ALL fields (id, title, content, author, likes)
          posts.add(Post.fromRow(row));
        }
        return posts;
      });
  }

  // 4. Increment Likes (Atomic Update)
  public Future<Void> likePost(Long postId) {
    // ATOMIC UPDATE: This prevents race conditions if 2 users like at the exact same time
    String sql = "UPDATE posts SET likes = likes + 1 WHERE id = ?";

    return dbClient.preparedQuery(sql)
      .execute(Tuple.of(postId))
      .compose(rows -> {
        if (rows.rowCount() == 0) {
          return Future.failedFuture("Post not found");
        }
        return Future.succeededFuture();
      });
  }
}
