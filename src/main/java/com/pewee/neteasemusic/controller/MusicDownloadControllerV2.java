package com.pewee.neteasemusic.controller;

import jakarta.annotation.Resource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.pewee.neteasemusic.enums.CommonRespInfo;
import com.pewee.neteasemusic.models.common.RespEntity;
import com.pewee.neteasemusic.service.MusicDownloadService;
import com.pewee.neteasemusic.service.AudioMetadataService;
import com.pewee.neteasemusic.service.AnalysisService;

import lombok.extern.slf4j.Slf4j;

/**
 * 音乐下载控制器 - 统一版本
 * 支持V1和V2 API，V2为推荐版本
 * @author pewee
 */
@RestController
@Slf4j
public class MusicDownloadControllerV2 {
	
	@Resource
	private MusicDownloadService musicService;
	
	@Resource
	private AudioMetadataService audioMetadataService;
	
	@Resource
	private AnalysisService analysisService;
	
	// ========== V2 API 端点（推荐使用） ==========
	
	@GetMapping("/v2/single")
	public RespEntity<String> downloadSingleV2(@RequestParam(value = "id") Long id) {
		try {
		musicService.downloadSingleSongV2(id);
			return RespEntity.apply(CommonRespInfo.SUCCESS, "单曲下载任务已启动");
		} catch (Exception e) {
			log.error("单曲下载失败: {}", id, e);
			return RespEntity.apply(CommonRespInfo.SYS_ERROR, "下载失败: " + e.getMessage());
		}
	}
	
	@GetMapping("/v2/playlist")
	public RespEntity<String> downloadPlaylistV2(@RequestParam(value = "id") Long id) {
		try {
		musicService.downloadPlaylistV2(id);
			return RespEntity.apply(CommonRespInfo.SUCCESS, "歌单下载任务已启动");
		} catch (Exception e) {
			log.error("歌单下载失败: {}", id, e);
			return RespEntity.apply(CommonRespInfo.SYS_ERROR, "下载失败: " + e.getMessage());
	}
	}
	
	@GetMapping("/v2/album")
	public RespEntity<String> downloadAlbumV2(@RequestParam(value = "id") Long id) {
		try {
		musicService.downloadAlbumV2(id);
			return RespEntity.apply(CommonRespInfo.SUCCESS, "专辑下载任务已启动");
		} catch (Exception e) {
			log.error("专辑下载失败: {}", id, e);
			return RespEntity.apply(CommonRespInfo.SYS_ERROR, "下载失败: " + e.getMessage());
		}
	}
	
	// ========== V1 API 端点（兼容旧版本） ==========
	
	@GetMapping("/v1/single")
	public RespEntity<String> downloadSingleV1(@RequestParam(value = "id") Long id) {
		try {
			musicService.downloadSingleSong(id);
			return RespEntity.apply(CommonRespInfo.SUCCESS, "OK");
		} catch (Exception e) {
			log.error("V1单曲下载失败: {}", id, e);
			return RespEntity.apply(CommonRespInfo.SYS_ERROR, "下载失败: " + e.getMessage());
		}
	}
	
	@GetMapping("/v1/playlist")
	public RespEntity<String> downloadPlaylistV1(@RequestParam(value = "id") Long id) {
		try {
			musicService.downloadPlaylist(id);
			return RespEntity.apply(CommonRespInfo.SUCCESS, "OK");
		} catch (Exception e) {
			log.error("V1歌单下载失败: {}", id, e);
			return RespEntity.apply(CommonRespInfo.SYS_ERROR, "下载失败: " + e.getMessage());
		}
	}
	
	@GetMapping("/v1/album")
	public RespEntity<String> downloadAlbumV1(@RequestParam(value = "id") Long id) {
		try {
			musicService.downloadAlbum(id);
			return RespEntity.apply(CommonRespInfo.SUCCESS, "OK");
		} catch (Exception e) {
			log.error("V1专辑下载失败: {}", id, e);
			return RespEntity.apply(CommonRespInfo.SYS_ERROR, "下载失败: " + e.getMessage());
		}
	}
	
	// ========== 元数据管理 API ==========
	
	/**
	 * 为指定音频文件添加元数据
	 * @param filePath 音频文件路径
	 * @param musicId 音乐ID
	 * @return 处理结果
	 */
	@GetMapping("/metadata/add")
	public RespEntity<String> addMetadataToFile(
			@RequestParam(value = "filePath") String filePath,
			@RequestParam(value = "musicId") Long musicId) {
		try {
			// 获取音乐信息
			com.pewee.neteasemusic.models.dtos.SingleMusicAnalysisRespDTO musicInfo = 
				analysisService.analyzeSingleSong(musicId, "lossless");
			
			if (musicInfo.getStatus() != 200) {
				return RespEntity.apply(CommonRespInfo.SYS_ERROR, "获取音乐信息失败");
			}
			
			// 添加元数据
			audioMetadataService.addMetadataToAudioFile(filePath, musicInfo);
			return RespEntity.apply(CommonRespInfo.SUCCESS, "元数据添加成功");
		} catch (Exception e) {
			log.error("添加元数据失败", e);
			return RespEntity.apply(CommonRespInfo.SYS_ERROR, "添加元数据失败: " + e.getMessage());
		}
	}
	
	/**
	 * 为目录中的所有音频文件添加元数据
	 * @param directoryPath 目录路径
	 * @param musicId 音乐ID
	 * @return 处理结果
	 */
	@GetMapping("/metadata/addDirectory")
	public RespEntity<String> addMetadataToDirectory(
			@RequestParam(value = "directoryPath") String directoryPath,
			@RequestParam(value = "musicId") Long musicId) {
		try {
			// 获取音乐信息
			com.pewee.neteasemusic.models.dtos.SingleMusicAnalysisRespDTO musicInfo = 
				analysisService.analyzeSingleSong(musicId, "lossless");
			
			if (musicInfo.getStatus() != 200) {
				return RespEntity.apply(CommonRespInfo.SYS_ERROR, "获取音乐信息失败");
			}
			
			// 添加元数据
			audioMetadataService.addMetadataToDirectory(directoryPath, musicInfo);
			return RespEntity.apply(CommonRespInfo.SUCCESS, "目录元数据添加成功");
		} catch (Exception e) {
			log.error("添加目录元数据失败", e);
			return RespEntity.apply(CommonRespInfo.SYS_ERROR, "添加目录元数据失败: " + e.getMessage());
		}
	}
}
