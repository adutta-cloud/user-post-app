package com.griddynamics.user_service.repositories;

import com.griddynamics.user_service.entities.User;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Tuple;

import java.util.ArrayList;
import java.util.List;

public class UserRepository {
  private final Pool dbClient;
  public UserRepository(Pool dbClient) {
    this.dbClient = dbClient;
  }

  // 1. Initialize Table
  public Future<Void> initData() {
    String schema = "CREATE TABLE IF NOT EXISTS users (" +
      "id IDENTITY PRIMARY KEY, " +
      "username VARCHAR(255) UNIQUE NOT NULL, " +
      "email VARCHAR(255) UNIQUE NOT NULL, " +
      "password VARCHAR(255) NOT NULL, " +
      "post_count INT DEFAULT 0 )";

    return dbClient.query(schema).execute().mapEmpty();
  }

  public Future<Void> save(User user) {
    String sql = "INSERT INTO users (username, email, password) VALUES (?, ?, ?)";
    return dbClient.preparedQuery(sql)
      .execute(Tuple.of(user.getUsername(), user.getEmail(), user.getPassword()))
      .mapEmpty();
  }

  public Future<List<User>> findAll() {
    return dbClient.query("SELECT * FROM users")
      .execute()
      .map(rows -> {
        List<User> users = new ArrayList<>();
        for (io.vertx.sqlclient.Row row : rows) {
          users.add(User.fromRow(row));
        }
        return users;
      });
  }

  public Future<User> findByUsername(String username) {
    String sql = "SELECT * FROM users WHERE username = ?";
    return dbClient.preparedQuery(sql)
      .execute(Tuple.of(username))
      .map(rows -> {
        if (rows.size() == 0) return null;
        return User.fromRow(rows.iterator().next());
      });
  }

  public Future<Void> incrementPostCount(Long userId) {
    String sql = "UPDATE users SET post_count = post_count + 1 WHERE id = ?";
    return dbClient.preparedQuery(sql)
      .execute(Tuple.of(userId))
      .mapEmpty();
  }

  public Future<User> findById(Long userId) {
    String sql = "SELECT * FROM users WHERE id = ?";
    return dbClient.preparedQuery(sql)
      .execute(Tuple.of(userId))
      .map(rows -> {
        if (rows.size() == 0) return null;
        return User.fromRow(rows.iterator().next());
      });
  }
}
