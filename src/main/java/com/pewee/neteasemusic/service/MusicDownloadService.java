package com.pewee.neteasemusic.service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.Resource;
import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import com.pewee.neteasemusic.config.AnalysisConfig;
import com.pewee.neteasemusic.enums.CommonRespInfo;
import com.pewee.neteasemusic.exceptions.ServiceException;
import com.pewee.neteasemusic.models.dtos.AlbumAnalysisRespDTO;
import com.pewee.neteasemusic.models.dtos.DownloadTaskDTO;
import com.pewee.neteasemusic.models.dtos.PlaylistAnalysisRespDTO;
import com.pewee.neteasemusic.models.dtos.SingleMusicAnalysisRespDTO;
import com.pewee.neteasemusic.models.dtos.TrackDTO;
import com.pewee.neteasemusic.models.dtos.TrackProgressDTO;
import java.util.ArrayList;
import com.pewee.neteasemusic.utils.FileUtils;
import com.pewee.neteasemusic.utils.HttpClientUtil;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MusicDownloadService {
	
	// 使用实例变量而不是静态变量，避免内存泄漏
	private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
		5, 5, 60, TimeUnit.MINUTES, 
		new ArrayBlockingQueue<>(10000),
		new ThreadPoolExecutor.CallerRunsPolicy()
	);
	
	@Resource
	private AnalysisConfig config;
	
	@Value("${download.path}")
	private String path;
	
	@Resource
	private AnalysisService analysisService;
	
	@Resource
	private AudioMetadataService audioMetadataService;
	
	@Resource
	private DownloadTaskService downloadTaskService;
	
	// API端点常量
	private static final String SINGLE_SONG = "Song_V1";
	private static final String PLAY_LIST = "Playlist";
	private static final String ALBUM = "Album";
	
	@PreDestroy
	public void shutdown() {
		if (executor != null && !executor.isShutdown()) {
			executor.shutdown();
			try {
				if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
					executor.shutdownNow();
				}
			} catch (InterruptedException e) {
				executor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
	}
	
	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	/**
	 * 从URL中提取文件扩展名
	 */
	private String getFileExtension(String url) {
		if (url == null || url.isEmpty()) {
			return ".mp3"; // 默认扩展名
		}
		int lastDotIndex = url.lastIndexOf(".");
		int questionIndex = url.indexOf("?");
		if (lastDotIndex > 0 && (questionIndex == -1 || lastDotIndex < questionIndex)) {
			return url.substring(lastDotIndex, questionIndex > 0 ? questionIndex : url.length());
		}
		return ".mp3";
	}
	
	/**
	 * 构建API URL
	 */
	private String buildApiUrl(String endpoint, String paramName, Long id) {
		return String.format("http://%s:%s/%s?type=json&level=lossless&%s=%d", 
			config.getIp(), config.getPort(), endpoint, paramName, id);
	}
	
	/**
	 * 通用下载单曲方法
	 */
	private void downloadSingleSongInternal(Long id, String downloadPath, String taskId, String songName) {
		try {
			SingleMusicAnalysisRespDTO musicInfo = analysisService.analyzeSingleSong(id, "lossless");
			if (musicInfo == null || musicInfo.getStatus() != 200) {
				String errorMsg = "获取歌曲信息失败";
				if (taskId != null) {
					downloadTaskService.updateProgress(taskId, songName, false);
				}
				throw new ServiceException(CommonRespInfo.SYS_ERROR.getCode(), errorMsg);
			}
			
			String fileName = sanitizeFileName(musicInfo.getAr_name()) + "-" + sanitizeFileName(musicInfo.getName());
			String fileExtension = getFileExtension(musicInfo.getUrl());
			String audioFilePath = downloadPath + "/" + fileName + fileExtension;

			// 校验文件是否存在且完整
			java.io.File audioFile = new java.io.File(audioFilePath);
			Long expectedSize = null;
			try {
				String sizeStr = musicInfo.getSize();
				if (sizeStr != null && sizeStr.matches("\\d+")) {
					expectedSize = Long.parseLong(sizeStr);
				}
			} catch (Exception ignore) {}
			if (audioFile.exists() && expectedSize != null && audioFile.length() == expectedSize) {
				log.info("文件已存在且完整，跳过下载: {} ({} bytes)", audioFilePath, expectedSize);
				if (taskId != null) {
					downloadTaskService.updateProgress(taskId, songName, true);
				}
				return;
			}
			
			// 下载音频文件
			log.info("开始下载歌曲: {} 到目录: {}", fileName, downloadPath);
			HttpClientUtil.getInputStreamWithHandler(musicInfo.getUrl(), null, in -> {
				FileUtils.writeToFile(Paths.get(downloadPath, fileName + fileExtension), in);
				return null;
			});
			log.info("歌曲下载完成: {}", fileName);
			
			// 添加元数据
			log.info("开始为歌曲: {} 添加元数据", fileName);
			audioMetadataService.addMetadataToAudioFile(audioFilePath, musicInfo);
			log.info("歌曲元数据添加完成: {}", fileName);
			
			// 下载歌词
			
			// 更新任务进度
			if (taskId != null) {
				downloadTaskService.updateProgress(taskId, songName, true);
			}
			
		} catch (Exception e) {
			if (taskId != null) {
				downloadTaskService.updateProgress(taskId, songName, false);
			}
			log.error("下载歌曲失败: {} - {}", songName, e.getMessage(), e);
			throw new ServiceException(CommonRespInfo.SYS_ERROR, e);
		}
	}
	
	// ========== V1 API 方法（兼容旧版本） ==========
	
	public void downloadAlbum(Long id) {
		AlbumAnalysisRespDTO analysisAlbum = analysisAlbum(id);
		if (analysisAlbum.getStatus() != 200) {
			throw new ServiceException(CommonRespInfo.SYS_ERROR);
		}
		List<TrackDTO> tracks = analysisAlbum.getAlbum().getSongs();
		for (TrackDTO trackDTO : tracks) {
			executor.execute(() -> downloadSingleSong(trackDTO.getId()));
		}
	}
	
	public void downloadPlaylist(Long id) {
		PlaylistAnalysisRespDTO analysisPlaylist = analysisPlaylist(id);
		if (analysisPlaylist.getStatus() != 200) {
			throw new ServiceException(CommonRespInfo.SYS_ERROR);
		}
		List<TrackDTO> tracks = analysisPlaylist.getPlaylist().getTracks();
		for (TrackDTO trackDTO : tracks) {
			executor.execute(() -> downloadSingleSong(trackDTO.getId()));
		}
	}
	
	public void downloadSingleSong(Long id) {
		SingleMusicAnalysisRespDTO analysisSingleMusic = analysisSingleMusic(id);
		if (analysisSingleMusic == null || analysisSingleMusic.getStatus() != 200) {
			throw new ServiceException(CommonRespInfo.SYS_ERROR);
		}
		
		String dir = path + sanitizeFileName(analysisSingleMusic.getAl_name());
		String fileName = sanitizeFileName(analysisSingleMusic.getAr_name()) + "-" + sanitizeFileName(analysisSingleMusic.getName());
		String audioFilePath = dir + "/" + fileName + getFileExtension(analysisSingleMusic.getUrl());
		
		// 校验文件是否存在且完整
		java.io.File audioFile = new java.io.File(audioFilePath);
		Long expectedSize = null;
		try {
			String sizeStr = analysisSingleMusic.getSize();
			if (sizeStr != null && sizeStr.matches("\\d+")) {
				expectedSize = Long.parseLong(sizeStr);
			}
		} catch (Exception ignore) {}
		if (audioFile.exists() && expectedSize != null && audioFile.length() == expectedSize) {
			log.info("文件已存在且完整，跳过下载: {} ({} bytes)", audioFilePath, expectedSize);
			return;
		}
		
		log.info("开始下载歌曲: {} 到目录: {}", fileName, dir);
		HttpClientUtil.getInputStreamWithHandler(analysisSingleMusic.getUrl(), null, in -> {
			FileUtils.writeToFile(Paths.get(dir, fileName + getFileExtension(analysisSingleMusic.getUrl())), in);
			return null;
		});
		log.info("歌曲下载完成: {}", fileName);
		
		// 添加元数据
		log.info("开始为歌曲: {} 添加元数据", fileName);
		audioMetadataService.addMetadataToAudioFile(audioFilePath, analysisSingleMusic);
		log.info("歌曲元数据添加完成: {}", fileName);
		
		// 下载歌词
		
	}
	
	private AlbumAnalysisRespDTO analysisAlbum(Long id) {
		log.info("解析专辑id: {}", id);
		String url = buildApiUrl(ALBUM, "id", id);
		try {
			String response = HttpClientUtil.executeGet(url, null, null);
			AlbumAnalysisRespDTO respDTO = JSON.parseObject(response, AlbumAnalysisRespDTO.class);
			log.debug("专辑解析返回: {}", JSON.toJSONString(respDTO));
			return respDTO;
		} catch (IOException | URISyntaxException e) {
			log.error("调用专辑解析失败", e);
			throw new ServiceException(CommonRespInfo.SYS_ERROR, e);
		}
	}
	
	private PlaylistAnalysisRespDTO analysisPlaylist(Long id) {
		log.info("解析歌单id: {}", id);
		String url = buildApiUrl(PLAY_LIST, "id", id);
		try {
			String response = HttpClientUtil.executeGet(url, null, null);
			PlaylistAnalysisRespDTO respDTO = JSON.parseObject(response, PlaylistAnalysisRespDTO.class);
			log.debug("歌单解析返回: {}", JSON.toJSONString(respDTO));
			return respDTO;
		} catch (IOException | URISyntaxException e) {
			log.error("调用歌单解析失败", e);
			throw new ServiceException(CommonRespInfo.SYS_ERROR, e);
		}
	}
	
	private SingleMusicAnalysisRespDTO analysisSingleMusic(Long id) {
		log.info("解析音乐id: {}", id);
		String url = buildApiUrl(SINGLE_SONG, "ids", id);
		try {
			String response = HttpClientUtil.executeGet(url, null, null);
			SingleMusicAnalysisRespDTO respDTO = JSON.parseObject(response, SingleMusicAnalysisRespDTO.class);
			log.debug("音乐解析返回: {}", JSON.toJSONString(respDTO));
			return respDTO;
		} catch (IOException | URISyntaxException e) {
			log.error("调用音乐解析失败", e);
			throw new ServiceException(CommonRespInfo.SYS_ERROR, e);
		}
	}
	
	// ========== V2 API 方法（新版本） ==========
	
	public void downloadSingleSongV2(Long id) {
		// 创建下载任务
		DownloadTaskDTO task = downloadTaskService.createTask("SINGLE", "单曲下载", id, 1, path);
		downloadTaskService.startTask(task.getTaskId());
		
		try {
			downloadSingleSongInternal(id, path, task.getTaskId(), "单曲下载");
			downloadTaskService.completeTask(task.getTaskId());
		} catch (Exception e) {
			downloadTaskService.failTask(task.getTaskId(), e.getMessage());
			throw e;
		}
	}
	
	public void downloadPlaylistV2(Long id) {
		PlaylistAnalysisRespDTO analysisPlaylist = analysisService.analyzePlaylist(id);
		if (analysisPlaylist.getStatus() != 200) {
			throw new ServiceException(CommonRespInfo.SYS_ERROR);
		}
		
		List<TrackDTO> tracks = analysisPlaylist.getPlaylist().getTracks();
		String playlistName = analysisPlaylist.getPlaylist().getName();
		String downloadPath = this.path + "歌单/" + FileUtils.getValidatedPathName(playlistName) + "/";
		// 初始化tracks进度
		ArrayList<TrackProgressDTO> trackProgressList = new ArrayList<>();
		for (TrackDTO track : tracks) {
			TrackProgressDTO t = new TrackProgressDTO();
			t.setId(track.getId());
			t.setName(track.getName());
			t.setProgress(0);
			t.setStatus("PENDING");
			t.setErrorMessage(null);
			trackProgressList.add(t);
		}
		// 创建下载任务
		DownloadTaskDTO task = downloadTaskService.createTask("PLAYLIST", playlistName, id, tracks.size(), downloadPath, trackProgressList);
		downloadTaskService.startTask(task.getTaskId());
		for (TrackDTO trackDTO : tracks) {
			executor.execute(() -> downloadSingleSongInternalV2(trackDTO.getId(), downloadPath, task.getTaskId(), trackDTO.getName()));
		}
	}

	public void downloadAlbumV2(Long id) {
		AlbumAnalysisRespDTO analysisAlbum = analysisService.analyzeAlbum(id);
		if (analysisAlbum.getStatus() != 200) {
			throw new ServiceException(CommonRespInfo.SYS_ERROR);
		}
		
		List<TrackDTO> tracks = analysisAlbum.getAlbum().getSongs();
		String albumName = analysisAlbum.getAlbum().getName();
		String downloadPath = this.path + "专辑/" + FileUtils.getValidatedPathName(albumName) + "/";
		// 初始化tracks进度
		ArrayList<TrackProgressDTO> trackProgressList = new ArrayList<>();
		for (TrackDTO track : tracks) {
			TrackProgressDTO t = new TrackProgressDTO();
			t.setId(track.getId());
			t.setName(track.getName());
			t.setProgress(0);
			t.setStatus("PENDING");
			t.setErrorMessage(null);
			trackProgressList.add(t);
		}
		// 创建下载任务
		DownloadTaskDTO task = downloadTaskService.createTask("ALBUM", albumName, id, tracks.size(), downloadPath, trackProgressList);
		downloadTaskService.startTask(task.getTaskId());
		for (TrackDTO trackDTO : tracks) {
			executor.execute(() -> downloadSingleSongInternalV2(trackDTO.getId(), downloadPath, task.getTaskId(), trackDTO.getName()));
		}
	}

	// 新增：支持每首歌进度的下载实现
	private void downloadSingleSongInternalV2(Long id, String downloadPath, String taskId, String songName) {
		try {
			downloadTaskService.updateTrackProgress(taskId, songName, 10, "DOWNLOADING", null);
			SingleMusicAnalysisRespDTO musicInfo = analysisService.analyzeSingleSong(id, "lossless");
			if (musicInfo == null || musicInfo.getStatus() != 200) {
				String errorMsg = "获取歌曲信息失败";
				downloadTaskService.updateTrackProgress(taskId, songName, 100, "FAILED", errorMsg);
				downloadTaskService.updateProgress(taskId, songName, false);
				throw new ServiceException(CommonRespInfo.SYS_ERROR.getCode(), errorMsg);
			}
			String fileName = sanitizeFileName(musicInfo.getAr_name()) + "-" + sanitizeFileName(musicInfo.getName());
			String fileExtension = getFileExtension(musicInfo.getUrl());
			String audioFilePath = downloadPath + "/" + fileName + fileExtension;
			java.io.File audioFile = new java.io.File(audioFilePath);
			Long expectedSize = null;
			try {
				String sizeStr = musicInfo.getSize();
				if (sizeStr != null && sizeStr.matches("\\d+")) {
					expectedSize = Long.parseLong(sizeStr);
				}
			} catch (Exception ignore) {}
			if (audioFile.exists() && expectedSize != null && audioFile.length() == expectedSize) {
				log.info("文件已存在且完整，跳过下载: {} ({} bytes)", audioFilePath, expectedSize);
				downloadTaskService.updateTrackProgress(taskId, songName, 100, "COMPLETED", null);
				downloadTaskService.updateProgress(taskId, songName, true);
				return;
			}
			log.info("开始下载歌曲: {} 到目录: {}", fileName, downloadPath);
			downloadTaskService.updateTrackProgress(taskId, songName, 30, "DOWNLOADING", null);
			HttpClientUtil.getInputStreamWithHandler(musicInfo.getUrl(), null, in -> {
				FileUtils.writeToFile(Paths.get(downloadPath, fileName + fileExtension), in);
				return null;
			});
			log.info("歌曲下载完成: {}", fileName);
			downloadTaskService.updateTrackProgress(taskId, songName, 70, "DOWNLOADING", null);
			log.info("开始为歌曲: {} 添加元数据", fileName);
			audioMetadataService.addMetadataToAudioFile(audioFilePath, musicInfo);
			log.info("歌曲元数据添加完成: {}", fileName);
			downloadTaskService.updateTrackProgress(taskId, songName, 100, "COMPLETED", null);
			downloadTaskService.updateProgress(taskId, songName, true);
		} catch (Exception e) {
			downloadTaskService.updateTrackProgress(taskId, songName, 100, "FAILED", e.getMessage());
			downloadTaskService.updateProgress(taskId, songName, false);
			log.error("下载歌曲失败: {} - {}", songName, e.getMessage(), e);
		}
	}

    private String sanitizeFileName(String name) {
        if (name == null) return "";
        // 替换常见非法字符: \/:*?"<>| 以及控制字符
        return name.replaceAll("[\\\\/:*?\"<>|\r\n\t]", "_");
    }
}
