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
      "author_id BIGINT NOT NULL, " +
      "content VARCHAR(1000), " +
      "likes INT DEFAULT 0 )";
    return dbClient.query(schema).execute().mapEmpty();
  }

  // 2. Create Post
  public Future<Void> save(Post post) {
    String sql = "INSERT INTO posts (author_id, content, likes) VALUES (?, ?, ?)";
    return dbClient.preparedQuery(sql)
      .execute(Tuple.of(post.getAuthorId(), post.getContent(), post.getLikes()))
      .mapEmpty();
  }

  // 3. Find All Posts
  public Future<List<Post>> findAll() {
    return dbClient.query("SELECT * FROM posts")
      .execute()
      .map(rows -> {
        List<Post> posts = new ArrayList<>();
        for (Row row : rows) {
          Post p = new Post();
          p.setId(row.getLong("ID"));
          p.setAuthorId(row.getLong("AUTHOR_ID"));
          p.setContent(row.getString("CONTENT"));
          p.setLikes(row.getInteger("LIKES"));
          posts.add(p);
        }
        return posts;
      });
  }
}
