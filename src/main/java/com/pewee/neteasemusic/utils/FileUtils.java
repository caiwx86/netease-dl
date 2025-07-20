package com.pewee.neteasemusic.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import com.pewee.neteasemusic.enums.CommonRespInfo;
import com.pewee.neteasemusic.exceptions.ServiceException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileUtils {
	
	/**
	 * 将字节数组写入文件
	 * @param path 文件路径
	 * @param bytes 字节数组
	 * @return 文件路径
	 */
	public static Path writeToFile(Path path, byte[] bytes) {
		if (path == null) {
			throw new IllegalArgumentException("文件路径不能为空");
		}
		if (bytes == null) {
			throw new IllegalArgumentException("字节数组不能为空");
		}
		
		try {
			// 创建父目录
			Files.createDirectories(path.getParent());
			
			// 写入文件
			Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			log.debug("文件写入成功: {}", path);
			return path;
		} catch (IOException e) {
			log.error("文件写入失败: {}", path, e);
			throw new ServiceException(CommonRespInfo.SYS_ERROR.getCode(), "文件写入失败: " + e.getMessage(), e);
		}
	}
	
	/**
	 * 验证并清理路径名称，移除非法字符
	 * @param name 原始名称
	 * @return 清理后的名称
	 */
	public static String getValidatedPathName(String name) {
		if (name == null || name.trim().isEmpty()) {
			return "unknown";
		}
		
		// 移除或替换非法字符
		return name.replaceAll("[\\\\/:*?\"<>|]", "_")
				   .replaceAll("\\s+", "_")
				   .trim();
	}
	
	/**
	 * 将输入流写入文件
	 * @param path 文件路径
	 * @param inputStream 输入流
	 * @return 文件路径
	 */
	public static Path writeToFile(Path path, InputStream inputStream) {
		Path tmp_path = null;
        
		if (path == null) {
			throw new IllegalArgumentException("文件路径不能为空");
		}
		if (path != null) {
			tmp_path = path.resolveSibling(path.getFileName() + ".tmp");
			if (Files.exists(path)) {
				// 文件已存在，直接返回
				return path;
			}
			if (Files.exists(tmp_path)) {
				try {
					Files.delete(tmp_path);
				} catch (IOException e) {
					log.error("删除临时文件失败: {}", tmp_path, e);
					throw new ServiceException(CommonRespInfo.SYS_ERROR.getCode(), "删除临时文件失败: " + e.getMessage(), e);
				}
			}
		}
		if (inputStream == null) {
			throw new IllegalArgumentException("输入流不能为空");
		}
		
		try {
			// 创建父目录
			Files.createDirectories(tmp_path.getParent());
			
			// 使用try-with-resources确保资源正确关闭
			try (OutputStream outputStream = Files.newOutputStream(tmp_path, StandardOpenOption.CREATE_NEW)) {
				byte[] buffer = new byte[8192];
				int bytesRead;
				long totalBytes = 0;
				
				while ((bytesRead = inputStream.read(buffer)) != -1) {
					outputStream.write(buffer, 0, bytesRead);
					totalBytes += bytesRead;
				}
				Files.move(tmp_path, path, StandardCopyOption.REPLACE_EXISTING);

				log.debug("文件写入成功: {}, 大小: {} bytes", path, totalBytes);
			}
			
			return path;
		} catch (IOException e) {
			log.error("文件写入失败: {}", path, e);
			// 清理失败的文件
			try {
				Files.deleteIfExists(path);
			} catch (IOException deleteException) {
				log.warn("清理失败文件时出错: {}", path, deleteException);
			}
			throw new ServiceException(CommonRespInfo.SYS_ERROR.getCode(), "文件写入失败: " + e.getMessage(), e);
		} finally {
			// 确保输入流被关闭
			try {
				inputStream.close();
			} catch (IOException e) {
				log.warn("关闭输入流时出错", e);
			}
		}
	}
	
	/**
	 * 安全删除文件
	 * @param path 文件路径
	 * @return 是否删除成功
	 */
	public static boolean deleteFile(Path path) {
		if (path == null) {
			return false;
		}
		
		try {
			boolean deleted = Files.deleteIfExists(path);
			if (deleted) {
				log.debug("文件删除成功: {}", path);
			}
			return deleted;
		} catch (IOException e) {
			log.error("文件删除失败: {}", path, e);
			return false;
		}
	}
	
	/**
	 * 检查文件是否存在且可读
	 * @param path 文件路径
	 * @return 是否可读
	 */
	public static boolean isReadable(Path path) {
		return path != null && Files.exists(path) && Files.isReadable(path);
	}
	
	/**
	 * 获取文件大小
	 * @param path 文件路径
	 * @return 文件大小（字节）
	 */
	public static long getFileSize(Path path) {
		if (!isReadable(path)) {
			return -1;
		}
		
		try {
			return Files.size(path);
		} catch (IOException e) {
			log.error("获取文件大小失败: {}", path, e);
			return -1;
		}
	}
}
