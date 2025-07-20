package com.pewee.neteasemusic.models.dtos;

import lombok.Data;

@Data
public class TrackProgressDTO {
    private Long id;
    private String name;
    private Integer progress; // 0-100
    private String status; // PENDING, DOWNLOADING, COMPLETED, FAILED
    private String errorMessage;
} 