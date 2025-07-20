package com.pewee.neteasemusic.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.stereotype.Service;

import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.ID3v24Tag;
import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.Mp3File;
import com.mpatric.mp3agic.NotSupportedException;
import com.mpatric.mp3agic.UnsupportedTagException;
import com.pewee.neteasemusic.models.dtos.SingleMusicAnalysisRespDTO;
import com.pewee.neteasemusic.utils.HttpClientUtil;

import lombok.extern.slf4j.Slf4j;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

@Service
@Slf4j
public class AudioMetadataService {
    
    /**
     * 为音频文件添加元数据信息
     * @param audioFilePath 音频文件路径
     * @param musicInfo 音乐信息
     */
    public void addMetadataToAudioFile(String audioFilePath, SingleMusicAnalysisRespDTO musicInfo) {
        File audioFile = new File(audioFilePath);
        if (!audioFile.exists()) {
            log.error("音频文件不存在: {}", audioFilePath);
            return;
        }
        if (audioFilePath.toLowerCase().endsWith(".mp3")) {
            addMetadataToMp3File(audioFilePath, musicInfo);
        } else if (audioFilePath.toLowerCase().endsWith(".flac")) {
            addMetadataToFlacFile(audioFilePath, musicInfo);
        } else {
            log.warn("只支持MP3/FLAC文件，跳过: {}", audioFilePath);
        }
    }

    private void addMetadataToMp3File(String audioFilePath, SingleMusicAnalysisRespDTO musicInfo) {
        try {
            Mp3File mp3file = new Mp3File(audioFilePath);
            ID3v2 id3v2Tag = mp3file.getId3v2Tag();
            if (id3v2Tag == null) {
                id3v2Tag = new ID3v24Tag();
                mp3file.setId3v2Tag(id3v2Tag);
            }
            setCommonMetadata(id3v2Tag, musicInfo);
            // 1. 保存到临时文件
            String tempFilePath = audioFilePath + ".tmp";
            mp3file.save(tempFilePath);
            // 2. 删除原文件
            File original = new File(audioFilePath);
            if (!original.delete()) {
                log.warn("删除原MP3文件失败: {}", audioFilePath);
            }
            // 3. 重命名临时文件为原文件名
            File temp = new File(tempFilePath);
            if (!temp.renameTo(original)) {
                log.warn("重命名临时MP3文件失败: {} -> {}", tempFilePath, audioFilePath);
            }
            log.info("MP3元数据写入完成: {}", audioFilePath);
        } catch (Exception e) {
            log.error("MP3元数据写入失败: {}", audioFilePath, e);
        }
    }

    private void addMetadataToFlacFile(String audioFilePath, SingleMusicAnalysisRespDTO musicInfo) {
        try {
            AudioFile f = AudioFileIO.read(new File(audioFilePath));
            Tag tag = f.getTagOrCreateAndSetDefault();
            setCommonMetadata(tag, musicInfo);
            f.commit();
            log.info("FLAC元数据写入完成: {}", audioFilePath);
        } catch (Exception e) {
            log.error("FLAC元数据写入失败: {}", audioFilePath, e);
        }
    }

    // 通用元数据写入（支持ID3v2和jaudiotagger Tag）
    private void setCommonMetadata(Object tagObj, SingleMusicAnalysisRespDTO musicInfo) {
        try {
            // 标题
            setField(tagObj, "TITLE", musicInfo.getName());
            // 艺术家
            setField(tagObj, "ARTIST", musicInfo.getAr_name());
            // 专辑
            setField(tagObj, "ALBUM", musicInfo.getAl_name());
            // 歌词
            setField(tagObj, "LYRICS", musicInfo.getLyric());
            // 翻译歌词/评论
            setField(tagObj, "COMMENT", musicInfo.getTlyric() != null ? "翻译歌词:\n" + musicInfo.getTlyric() : null);
            // 封面
            setCover(tagObj, musicInfo.getPic());
        } catch (Exception e) {
            log.error("设置元数据失败", e);
        }
    }

    // 兼容ID3v2和jaudiotagger Tag的字段设置
    private void setField(Object tagObj, String key, String value) {
        if (value == null || value.trim().isEmpty()) return;
        try {
            if (tagObj instanceof ID3v2) {
                ID3v2 tag = (ID3v2) tagObj;
                switch (key) {
                    case "TITLE": tag.setTitle(value); break;
                    case "ARTIST": tag.setArtist(value); break;
                    case "ALBUM": tag.setAlbum(value); break;
                    case "LYRICS": tag.setLyrics(value); break;
                    case "COMMENT": tag.setComment(value); break;
                }
            } else if (tagObj instanceof Tag) {
                Tag tag = (Tag) tagObj;
                switch (key) {
                    case "TITLE": tag.setField(FieldKey.TITLE, value); break;
                    case "ARTIST": tag.setField(FieldKey.ARTIST, value); break;
                    case "ALBUM": tag.setField(FieldKey.ALBUM, value); break;
                    case "LYRICS": tag.setField(FieldKey.LYRICS, value); break;
                    case "COMMENT": tag.setField(FieldKey.COMMENT, value); break;
                }
            }
        } catch (Exception e) {
            log.warn("设置字段{}失败: {}", key, e.getMessage());
        }
    }

    // 兼容ID3v2和jaudiotagger Tag的封面设置
    private void setCover(Object tagObj, String picUrl) {
        if (picUrl == null || picUrl.trim().isEmpty()) return;
        try {
            byte[] imageData = downloadImage(picUrl);
            if (imageData == null || imageData.length == 0) {
                log.warn("封面图片下载失败或图片为空: {}", picUrl);
                return;
            }
            // 自动压缩图片为最大500x500像素，JPEG格式
            imageData = compressImageToJpeg(imageData, 500, 500);
            String mimeType = "image/jpeg";
            log.info("封面图片下载并压缩成功，字节数: {}, url: {}, mimeType: {}", imageData.length, picUrl, mimeType);
            if (tagObj instanceof ID3v2) {
                ((ID3v2) tagObj).setAlbumImage(imageData, mimeType);
                log.info("MP3封面图片写入成功");
            } else if (tagObj instanceof Tag) {
                Tag tag = (Tag) tagObj;
                tag.deleteArtworkField();
                Artwork artwork = ArtworkFactory.getNew();
                artwork.setBinaryData(imageData);
                artwork.setMimeType(mimeType);
                tag.setField(artwork);
                log.info("FLAC封面图片写入成功");
            }
        } catch (Exception e) {
            log.warn("设置封面失败: {}，图片url: {}", e.getMessage(), picUrl);
        }
    }

    // 新增：图片压缩为最大maxWidth x maxHeight，输出JPEG格式
    private byte[] compressImageToJpeg(byte[] imageData, int maxWidth, int maxHeight) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(imageData);
            BufferedImage original = ImageIO.read(bais);
            if (original == null) return imageData;
            int width = original.getWidth();
            int height = original.getHeight();
            double scale = Math.min((double)maxWidth/width, (double)maxHeight/height);
            if (scale > 1) scale = 1; // 不放大
            int newW = (int)(width * scale);
            int newH = (int)(height * scale);
            BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
            resized.getGraphics().drawImage(original, 0, 0, newW, newH, null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resized, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            log.warn("图片压缩失败，使用原图: {}", e.getMessage());
            return imageData;
        }
    }

    private byte[] downloadImage(String imageUrl) {
        try {
            return HttpClientUtil.getInputStreamWithHandler(imageUrl, null, in -> {
                try {
                    return in.readAllBytes();
                } catch (Exception e) {
                    log.warn("图片流读取失败: {}", e.getMessage());
                    return null;
                }
            });
        } catch (Exception e) {
            log.warn("下载图片失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 批量处理音频文件的元数据
     * @param directoryPath 目录路径
     * @param musicInfo 音乐信息
     */
    public void addMetadataToDirectory(String directoryPath, SingleMusicAnalysisRespDTO musicInfo) {
        try {
            Path directory = Paths.get(directoryPath);
            if (!Files.exists(directory) || !Files.isDirectory(directory)) {
                log.error("目录不存在: {}", directoryPath);
                return;
            }
            Files.list(directory)
                .filter(path -> {
                    String fileName = path.getFileName().toString().toLowerCase();
                    return fileName.endsWith(".mp3") || fileName.endsWith(".flac");
                })
                .forEach(path -> addMetadataToAudioFile(path.toString(), musicInfo));
        } catch (IOException e) {
            log.error("处理目录失败: {}", e.getMessage(), e);
        }
    }
} 