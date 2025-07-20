package com.pewee.neteasemusic.models.dtos;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 下载任务数据传输对象
 */
@Data
public class DownloadTaskDTO {

    /** 任务ID */
    private String taskId;

    /** 任务类型：SINGLE, PLAYLIST, ALBUM */
    private String taskType;

    /** 任务名称 */
    private String taskName;

    /** 音乐ID或歌单/专辑ID */
    private Long musicId;

    /** 任务状态：PENDING, DOWNLOADING, COMPLETED, FAILED */
    private String status;

    /** 总数量 */
    private Integer totalCount;

    /** 已完成数量 */
    private Integer completedCount;

    /** 失败数量 */
    private Integer failedCount;

    /** 进度百分比 */
    private Integer progress;

    /** 错误信息 */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 完成时间 */
    private LocalDateTime completeTime;

    /** 下载路径 */
    private String downloadPath;

    /** 当前下载的歌曲信息 */
    private String currentSong;

    /** 任务描述 */
    private String description;

    /** 每首歌的进度 */
    private List<TrackProgressDTO> tracks;
} 