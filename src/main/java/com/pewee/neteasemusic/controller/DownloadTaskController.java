package com.pewee.neteasemusic.controller;

import java.util.List;
import java.util.Map;

import jakarta.annotation.Resource;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pewee.neteasemusic.enums.CommonRespInfo;
import com.pewee.neteasemusic.models.common.RespEntity;
import com.pewee.neteasemusic.models.dtos.DownloadTaskDTO;
import com.pewee.neteasemusic.service.DownloadTaskService;

import lombok.extern.slf4j.Slf4j;

/**
 * 下载任务管理控制器
 */
@RestController
@Slf4j
@RequestMapping("/api/tasks")
public class DownloadTaskController {
    
    @Resource
    private DownloadTaskService downloadTaskService;
    
    /**
     * 获取所有下载任务
     */
    @GetMapping("/all")
    public RespEntity<List<DownloadTaskDTO>> getAllTasks() {
        try {
            List<DownloadTaskDTO> tasks = downloadTaskService.getAllTasks();
            return RespEntity.apply(CommonRespInfo.SUCCESS, tasks);
        } catch (Exception e) {
            log.error("获取所有任务失败", e);
            return RespEntity.apply(CommonRespInfo.SYS_ERROR, null);
        }
    }
    
    /**
     * 获取活跃任务（正在下载或等待中）
     */
    @GetMapping("/active")
    public RespEntity<List<DownloadTaskDTO>> getActiveTasks() {
        try {
            List<DownloadTaskDTO> tasks = downloadTaskService.getActiveTasks();
            return RespEntity.apply(CommonRespInfo.SUCCESS, tasks);
        } catch (Exception e) {
            log.error("获取活跃任务失败", e);
            return RespEntity.apply(CommonRespInfo.SYS_ERROR, null);
        }
    }
    
    /**
     * 获取已完成任务
     */
    @GetMapping("/completed")
    public RespEntity<List<DownloadTaskDTO>> getCompletedTasks() {
        try {
            List<DownloadTaskDTO> tasks = downloadTaskService.getCompletedTasks();
            return RespEntity.apply(CommonRespInfo.SUCCESS, tasks);
        } catch (Exception e) {
            log.error("获取已完成任务失败", e);
            return RespEntity.apply(CommonRespInfo.SYS_ERROR, null);
        }
    }
    
    /**
     * 获取指定任务详情
     */
    @GetMapping("/{taskId}")
    public RespEntity<DownloadTaskDTO> getTask(@PathVariable String taskId) {
        try {
            DownloadTaskDTO task = downloadTaskService.getTask(taskId);
            if (task != null) {
                return RespEntity.apply(CommonRespInfo.SUCCESS, task);
            } else {
                return RespEntity.apply(CommonRespInfo.SYS_ERROR, null);
            }
        } catch (Exception e) {
            log.error("获取任务详情失败: {}", taskId, e);
            return RespEntity.apply(CommonRespInfo.SYS_ERROR, null);
        }
    }
    
    /**
     * 删除指定任务
     */
    @DeleteMapping("/{taskId}")
    public RespEntity<String> deleteTask(@PathVariable String taskId) {
        try {
            downloadTaskService.deleteTask(taskId);
            return RespEntity.apply(CommonRespInfo.SUCCESS, "任务删除成功");
        } catch (Exception e) {
            log.error("删除任务失败: {}", taskId, e);
            return RespEntity.apply(CommonRespInfo.SYS_ERROR, "删除任务失败");
        }
    }
    
    /**
     * 获取下载统计信息
     */
    @GetMapping("/statistics")
    public RespEntity<Map<String, Object>> getStatistics() {
        try {
            Map<String, Object> stats = downloadTaskService.getStatistics();
            return RespEntity.apply(CommonRespInfo.SUCCESS, stats);
        } catch (Exception e) {
            log.error("获取统计信息失败", e);
            return RespEntity.apply(CommonRespInfo.SYS_ERROR, null);
        }
    }
    
    /**
     * 清理已完成的任务
     */
    @DeleteMapping("/cleanup")
    public RespEntity<String> cleanupCompletedTasks() {
        try {
            downloadTaskService.cleanupCompletedTasks();
            return RespEntity.apply(CommonRespInfo.SUCCESS, "清理完成");
        } catch (Exception e) {
            log.error("清理任务失败", e);
            return RespEntity.apply(CommonRespInfo.SYS_ERROR, "清理任务失败");
        }
    }
} 