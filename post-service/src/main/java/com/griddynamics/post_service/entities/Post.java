package com.griddynamics.post_service.entities;

import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;

public class Post {
  private Long id;
  private String title;
  private String content;
  private int authorId;
  private String authorName;
  private int likes;

  public Post() {}

  public Post(String title, String content, int authorId, String authorName) {
    this.title = title;
    this.content = content;
    this.authorId = authorId;
    this.authorName = authorName;
    this.likes = 0; // Default to 0
  }

  // --- Getters & Setters ---
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }

  public String getContent() { return content; }
  public void setContent(String content) { this.content = content; }

  public int getAuthorId() { return authorId; }
  public void setAuthorId(int authorId) { this.authorId = authorId; }

  public String getAuthorName() { return authorName; }
  public void setAuthorName(String authorName) { this.authorName = authorName; }

  // 👈 2. New Getter/Setter
  public int getLikes() { return likes; }
  public void setLikes(int likes) { this.likes = likes; }

  // --- Helper Methods ---

  public static Post fromRow(Row row) {
    Post post = new Post();
    post.setId(row.getLong("ID"));
    post.setTitle(row.getString("TITLE"));
    post.setContent(row.getString("CONTENT"));
    post.setAuthorId(row.getInteger("AUTHOR_ID"));
    post.setAuthorName(row.getString("AUTHOR_NAME"));

    // 👈 3. Map from DB (Handle nulls just in case)
    Integer likes = row.getInteger("LIKES");
    post.setLikes(likes != null ? likes : 0);

    return post;
  }

  public JsonObject toJson() {
    return new JsonObject()
      .put("id", id)
      .put("title", title)
      .put("content", content)
      .put("authorId", authorId)
      .put("authorName", authorName)
      .put("likes", likes); // 👈 4. Include in JSON response
  }
}
