package com.pewee.neteasemusic.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.pewee.neteasemusic.models.dtos.DownloadTaskDTO;
import com.pewee.neteasemusic.models.dtos.TrackProgressDTO;

import lombok.extern.slf4j.Slf4j;

/**
 * 下载任务管理服务
 */
@Service
@Slf4j
public class DownloadTaskService {
    
    // 存储所有下载任务
    private final Map<String, DownloadTaskDTO> taskMap = new ConcurrentHashMap<>();
    
    /**
     * 创建新的下载任务
     */
    public DownloadTaskDTO createTask(String taskType, String taskName, Long musicId, Integer totalCount, String downloadPath, List<TrackProgressDTO> tracks) {
        DownloadTaskDTO task = new DownloadTaskDTO();
        task.setTaskId(UUID.randomUUID().toString());
        task.setTaskType(taskType);
        task.setTaskName(taskName);
        task.setMusicId(musicId);
        task.setStatus("PENDING");
        task.setTotalCount(totalCount);
        task.setCompletedCount(0);
        task.setFailedCount(0);
        task.setProgress(0);
        task.setCreateTime(LocalDateTime.now());
        task.setDownloadPath(downloadPath);
        task.setDescription(String.format("下载%s: %s", getTaskTypeDescription(taskType), taskName));
        task.setTracks(tracks);
        taskMap.put(task.getTaskId(), task);
        log.info("创建下载任务: {}", task.getTaskId());
        return task;
    }

    // 兼容原有API
    public DownloadTaskDTO createTask(String taskType, String taskName, Long musicId, Integer totalCount, String downloadPath) {
        return createTask(taskType, taskName, musicId, totalCount, downloadPath, null);
    }
    
    /**
     * 开始下载任务
     */
    public void startTask(String taskId) {
        DownloadTaskDTO task = taskMap.get(taskId);
        if (task != null) {
            task.setStatus("DOWNLOADING");
            task.setStartTime(LocalDateTime.now());
            log.info("开始下载任务: {}", taskId);
        }
    }
    
    /**
     * 更新任务进度（支持每首歌进度）
     */
    public void updateProgress(String taskId, String songName, boolean success) {
        DownloadTaskDTO task = taskMap.get(taskId);
        if (task != null) {
            task.setCurrentSong(songName);
            // 更新tracks中对应歌曲状态
            if (task.getTracks() != null) {
                for (TrackProgressDTO track : task.getTracks()) {
                    if (track.getName().equals(songName)) {
                        track.setProgress(100);
                        track.setStatus(success ? "COMPLETED" : "FAILED");
                        if (!success) {
                            track.setErrorMessage("下载失败");
                        }
                        break;
                    }
                }
            }
            if (success) {
                task.setCompletedCount(task.getCompletedCount() + 1);
            } else {
                task.setFailedCount(task.getFailedCount() + 1);
            }
            int total = task.getTotalCount();
            int completed = task.getCompletedCount() + task.getFailedCount();
            int progress = total > 0 ? (completed * 100) / total : 0;
            task.setProgress(progress);
            if (completed >= total) {
                completeTask(taskId);
            }
            log.info("更新任务进度: {} - {}/{} ({}%)", taskId, completed, total, progress);
        }
    }

    // 新增：单独更新某首歌的进度
    public void updateTrackProgress(String taskId, String songName, int progress, String status, String errorMessage) {
        DownloadTaskDTO task = taskMap.get(taskId);
        if (task != null && task.getTracks() != null) {
            for (TrackProgressDTO track : task.getTracks()) {
                if (track.getName().equals(songName)) {
                    track.setProgress(progress);
                    track.setStatus(status);
                    track.setErrorMessage(errorMessage);
                    break;
                }
            }
        }
    }
    
    /**
     * 完成任务
     */
    public void completeTask(String taskId) {
        DownloadTaskDTO task = taskMap.get(taskId);
        if (task != null) {
            task.setStatus("COMPLETED");
            task.setCompleteTime(LocalDateTime.now());
            log.info("完成下载任务: {}", taskId);
        }
    }
    
    /**
     * 任务失败
     */
    public void failTask(String taskId, String errorMessage) {
        DownloadTaskDTO task = taskMap.get(taskId);
        if (task != null) {
            task.setStatus("FAILED");
            task.setErrorMessage(errorMessage);
            task.setCompleteTime(LocalDateTime.now());
            log.error("下载任务失败: {} - {}", taskId, errorMessage);
        }
    }
    
    /**
     * 获取任务信息
     */
    public DownloadTaskDTO getTask(String taskId) {
        return taskMap.get(taskId);
    }
    
    /**
     * 获取所有任务
     */
    public List<DownloadTaskDTO> getAllTasks() {
        return taskMap.values().stream()
                .sorted((t1, t2) -> t2.getCreateTime().compareTo(t1.getCreateTime()))
                .collect(Collectors.toList());
    }
    
    /**
     * 获取活跃任务（正在下载或等待中）
     */
    public List<DownloadTaskDTO> getActiveTasks() {
        return taskMap.values().stream()
                .filter(task -> "PENDING".equals(task.getStatus()) || "DOWNLOADING".equals(task.getStatus()))
                .sorted((t1, t2) -> t2.getCreateTime().compareTo(t1.getCreateTime()))
                .collect(Collectors.toList());
    }
    
    /**
     * 获取已完成任务
     */
    public List<DownloadTaskDTO> getCompletedTasks() {
        return taskMap.values().stream()
                .filter(task -> "COMPLETED".equals(task.getStatus()) || "FAILED".equals(task.getStatus()))
                .sorted((t1, t2) -> t2.getCreateTime().compareTo(t1.getCreateTime()))
                .limit(20) // 只返回最近20个已完成的任务
                .collect(Collectors.toList());
    }
    
    /**
     * 删除任务
     */
    public void deleteTask(String taskId) {
        DownloadTaskDTO task = taskMap.remove(taskId);
        if (task != null) {
            log.info("删除下载任务: {}", taskId);
        }
    }
    
    /**
     * 清理已完成的任务（保留最近50个）
     */
    public void cleanupCompletedTasks() {
        List<DownloadTaskDTO> completedTasks = taskMap.values().stream()
                .filter(task -> "COMPLETED".equals(task.getStatus()) || "FAILED".equals(task.getStatus()))
                .sorted((t1, t2) -> t2.getCreateTime().compareTo(t1.getCreateTime()))
                .collect(Collectors.toList());
        
        if (completedTasks.size() > 50) {
            completedTasks.subList(50, completedTasks.size()).forEach(task -> 
                taskMap.remove(task.getTaskId()));
            log.info("清理了 {} 个已完成的任务", completedTasks.size() - 50);
        }
    }
    
    /**
     * 获取任务类型描述
     */
    private String getTaskTypeDescription(String taskType) {
        switch (taskType) {
            case "SINGLE": return "单曲";
            case "PLAYLIST": return "歌单";
            case "ALBUM": return "专辑";
            default: return "未知类型";
        }
    }
    
    /**
     * 获取统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        
        long totalTasks = taskMap.size();
        long pendingTasks = taskMap.values().stream().filter(t -> "PENDING".equals(t.getStatus())).count();
        long downloadingTasks = taskMap.values().stream().filter(t -> "DOWNLOADING".equals(t.getStatus())).count();
        long completedTasks = taskMap.values().stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
        long failedTasks = taskMap.values().stream().filter(t -> "FAILED".equals(t.getStatus())).count();
        
        stats.put("totalTasks", totalTasks);
        stats.put("pendingTasks", pendingTasks);
        stats.put("downloadingTasks", downloadingTasks);
        stats.put("completedTasks", completedTasks);
        stats.put("failedTasks", failedTasks);
        
        return stats;
    }
} 