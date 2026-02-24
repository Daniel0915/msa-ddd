package com.back.shared.post.dto;

import com.back.global.jpa.entity.HasModelTypeCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@AllArgsConstructor
@Getter
public class PostDto implements HasModelTypeCode {
    private final int           id;
    private final LocalDateTime createdDate;
    private final LocalDateTime modifyDate;
    private final int authorId;
    private final String authorName;
    private final String title;
    private final String content;

    @Override
    public String getModelTypeCode() {
        return "Post";
    }
}
