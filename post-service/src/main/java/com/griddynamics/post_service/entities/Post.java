package com.griddynamics.post_service.entities;

import io.vertx.core.json.JsonObject;

public class Post {
  private Long id;
  private Long authorId; // Foreign key (logically) to User Service
  private String content;
  private int likes;

  public Post() {}

  public Post(Long authorId, String content) {
    this.authorId = authorId;
    this.content = content;
    this.likes = 0; // Default to 0
  }

  // Getters & Setters
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public Long getAuthorId() { return authorId; }
  public void setAuthorId(Long authorId) { this.authorId = authorId; }

  public String getContent() { return content; }
  public void setContent(String content) { this.content = content; }

  public int getLikes() { return likes; }
  public void setLikes(int likes) { this.likes = likes; }

  public JsonObject toJson() {
    return new JsonObject()
      .put("id", id)
      .put("authorId", authorId)
      .put("content", content)
      .put("likes", likes);
  }
}
