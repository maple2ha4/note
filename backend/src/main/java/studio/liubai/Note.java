package studio.liubai;

import java.time.*;
import java.util.List;

public record Note(
    String id,
    String title,
    String content,
    String space,
    String category,
    List<String> tags,
    boolean favorite,
    boolean published,
    boolean draft,
    String shareId,
    LocalDate entryDate,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    long version) {}
