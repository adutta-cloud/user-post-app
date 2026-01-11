package com.griddynamics.user_service.entities;

import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;

public class User {
  private Long id;
  private String username;
  private String email;
  private String password;
  private int postCount;

  public User() {
  }

  public User(String username, String email, String password) {
    this.username = username;
    this.email = email;
    this.password = password;
    this.postCount = 0;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public int getPostCount() { return postCount; }

  public void setPostCount(int postCount) { this.postCount = postCount; }

  public static User fromRow(Row row) {
    User user = new User();
    // Use Safe Column Access (works for "ID" or "id")
    user.setId(row.getLong("ID"));
    user.setUsername(row.getString("USERNAME"));
    user.setEmail(row.getString("EMAIL"));
    user.setPassword(row.getString("PASSWORD"));

    Integer count = row.getInteger("POST_COUNT");
    user.setPostCount(count != null ? count : 0);

    return user;
  }

  public JsonObject toJson() {
    return new JsonObject()
      .put("id", id)
      .put("username", username)
      .put("email", email)
      .put("postCount", postCount);
  }
}
