package com.pewee.neteasemusic.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.Header;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pewee.neteasemusic.enums.CommonRespInfo;
import com.pewee.neteasemusic.exceptions.ServiceException;
import com.pewee.neteasemusic.utils.HttpClientUtil;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class NeteaseAPIService implements InitializingBean{
	
	private volatile boolean ready = false;
	
	 private static final String AES_KEY = "e82ckenh8dichen8";
	 
	 @Value("${download.path}")
	 private String path;
	 
	 private final String cookieFile = "cookie.txt";
	 
	 private transient String cookie;
	 
	 	private transient Long uid; // 登录用户 UID
	
	// 二维码缓存相关
	private static final Map<String, QrCodeInfo> qrCodeCache = new ConcurrentHashMap<>();
	private static final long QR_CODE_CACHE_DURATION = 5 * 60 * 1000; // 5分钟缓存时间（增加1分钟）
	
	/**
	 * 二维码信息缓存类
	 */
	private static class QrCodeInfo {
		private final String unikey;
		private final long createTime;
		private final String qrUrl;
		private volatile int lastStatusCode = 801; // 默认等待扫码状态
		private volatile long lastCheckTime = 0;
		private volatile int consecutiveSameStatus = 0; // 连续相同状态计数
		
		public QrCodeInfo(String unikey, String qrUrl) {
			this.unikey = unikey;
			this.qrUrl = qrUrl;
			this.createTime = System.currentTimeMillis();
		}
		
		public boolean isExpired() {
			return System.currentTimeMillis() - createTime > QR_CODE_CACHE_DURATION;
		}
		
		public boolean shouldCheck() {
			// 根据状态码和连续次数决定检查频率
			long now = System.currentTimeMillis();
			long baseInterval;
			
			switch (lastStatusCode) {
				case 801: // 等待扫码
					baseInterval = 3000; // 3秒
					break;
				case 802: // 已扫码
					baseInterval = 2000; // 2秒
					break;
				case 803: // 登录成功
					baseInterval = 1000; // 1秒
					break;
				case 800: // 已过期
					baseInterval = 15000; // 15秒（增加间隔）
					break;
				default:
					baseInterval = 5000; // 5秒
					break;
			}
			
			// 如果连续相同状态超过3次，增加检查间隔
			long adjustedInterval = baseInterval;
			if (consecutiveSameStatus > 3) {
				adjustedInterval = Math.min(baseInterval * 2, 30000); // 最多30秒
			}
			
			return now - lastCheckTime >= adjustedInterval;
		}
		
		public void updateStatus(int statusCode) {
			if (statusCode == lastStatusCode) {
				consecutiveSameStatus++;
			} else {
				consecutiveSameStatus = 0;
			}
			this.lastStatusCode = statusCode;
			this.lastCheckTime = System.currentTimeMillis();
		}
	}
	
	/**
	 * 清理过期的二维码缓存
	 */
	private void cleanExpiredQrCodes() {
		qrCodeCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
	}
	
	public Long getUserUid() {
		return this.uid;
	}
	 public void refreshCookie(String cookie) {
		 String cookiePath =  path + cookieFile;
		 File file = new File(cookiePath); 
		 if (file.exists()) {
			 file.delete();
		 }
		 File pFile = new File(file.getParent());
		 if(!pFile.exists()) {
			 pFile.mkdirs();
		 }
		 FileOutputStream outputStream = null;
		 try {
			outputStream = new FileOutputStream(file);
			IOUtils.write(cookie.getBytes("utf-8"), outputStream);
		} catch (Exception e) {
			throw new ServiceException(CommonRespInfo.SYS_ERROR,e);
		} finally {
			if (null != outputStream) {
				IOUtils.closeQuietly(outputStream);
			}
		}
		 this.cookie = cookie;
		 try {
				getAccountInfo();
				ready = true;
			} catch (Exception e) {
				ready = false;
				this.cookie = null;
				this.uid = null;
				file.delete();
				log.info("获取账号信息失败!,请重新登录!!");
				log.error("获取账号信息失败,需要重新登录",e);
			}
	 }
	  
	 
	@Override
	public void afterPropertiesSet() throws Exception {
		String cookiePath =  path + cookieFile;
		File file = new File(cookiePath);
		
		if (file.exists() && file.length() > 0) {
			byte[] buff = new byte[(int)file.length()];
			try (FileInputStream fileInputStream = new FileInputStream(file)) {
				IOUtils.readFully(fileInputStream, buff);
			} ;
			this.cookie = new String(buff,"utf-8");
			try {
				getAccountInfo();
				ready = true;
			} catch (Exception e) {
				ready = false;
				this.cookie = null;
				this.uid = null;
				file.delete();
				log.info("获取账号信息失败!,请重新登录!!");
				log.error("获取账号信息失败,需要重新登录",e);
			}
		}
	}
	 
	 public boolean checkReady() {
		 return this.ready;
	 }
	 
	 
	 private static  String getCookieValue(String cookie) {
		// 设置 Cookie
	    StringBuilder cookieHeader = new StringBuilder("os=pc;appver=;osver=;deviceId=pyncm!");
	    cookieHeader.append(";").append(cookie);
	    return cookieHeader.toString();
	 }

	private static String md5Hex(String input) throws Exception {
	        MessageDigest md = MessageDigest.getInstance("MD5");
	        byte[] digest = md.digest(input.getBytes("UTF-8"));
	        StringBuilder sb = new StringBuilder();
	        for (byte b : digest) {
	            sb.append(String.format("%02x", b & 0xff));
	        }
	        return sb.toString();
	    }

	    private static String aesEncryptECB(String input, String key) throws Exception {
	        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding");
	        javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(key.getBytes("UTF-8"), "AES");
	        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec);
	        byte[] encrypted = cipher.doFinal(input.getBytes("UTF-8"));
	        StringBuilder sb = new StringBuilder();
	        for (byte b : encrypted) {
	            sb.append(String.format("%02x", b & 0xff));
	        }
	        return sb.toString();
	    }
	    
	    // 修复 getAccountInfo，自动处理 Set-Cookie
	    public Long getAccountInfo() throws Exception {
	        String url = "https://interface3.music.163.com/eapi/w/nuser/account/get";
	        String apiPath = "/api/w/nuser/account/get";

	        Map<String, Object> config = new LinkedHashMap<>();
	        config.put("os", "pc");
	        config.put("appver", "");
	        config.put("osver", "");
	        config.put("deviceId", "pyncm!");
	        config.put("requestId", String.valueOf(20000000 + new Random().nextInt(10000000)));

	        Map<String, Object> payload = new LinkedHashMap<>();
	        payload.put("header", new ObjectMapper().writeValueAsString(config));

	        String jsonPayload = new ObjectMapper().writeValueAsString(payload);
	        String digest = md5Hex("nobody" + apiPath + "use" + jsonPayload + "md5forencrypt");
	        String rawParams = apiPath + "-36cd479b6b5-" + jsonPayload + "-36cd479b6b5-" + digest;
	        String encParams = aesEncryptECB(rawParams, AES_KEY);

	        Map<String, String> headers = new HashMap<>();
	        headers.put("Referer", "");
	        headers.put("Cookie", getCookieValue(this.cookie));

	        Map<String, String> params = new HashMap<>();
	        params.put("params", encParams);
	        
	        // 用 postFormAndReturnHeaders 获取 Set-Cookie
	        Pair<String, Header[]> respPair = HttpClientUtil.postFormAndReturnHeaders(url, headers, params);
	        String accInfo = respPair.getLeft();
	        Header[] allHeaders = respPair.getRight();
	        // 自动刷新 Cookie
	        if (allHeaders != null) {
	            for (Header header : allHeaders) {
	                if (header.getName().equalsIgnoreCase("Set-Cookie")) {
	                    String newCookie = header.getValue();
	                    if (newCookie.startsWith("MUSIC_U")) {
	                        refreshCookie(newCookie);
	                        break;
	                    }
	                }
	            }
	        }
	        ObjectMapper mapper = new ObjectMapper();
	        @SuppressWarnings("unchecked")
	        Map<String, Object> result = mapper.readValue(accInfo, Map.class);
	        Integer code = (Integer) result.get("code");
	        if (200 != code) {
	            throw new ServiceException(CommonRespInfo.SERVICE_EXECUTION_ERROR);
	        }
	        @SuppressWarnings("unchecked")
	        Map<String, Object> accountMap = (Map<String, Object>)result.get("account");
	        Long id = Long.valueOf("" + accountMap.get("id")) ;
	        this.uid = id;
	        log.info("获取到当前登录用户id: {}",id);
	        return id;
	    }

	    
	    
	    /**
	     * 获取用户详情
	     * @param userId
	     * @return
	     * @throws Exception
	     */
	    public String getUserDetail(Long userId) throws Exception {
	        String apiPath = "/api/v1/user/detail";
	        String url = "https://interface3.music.163.com/eapi/v1/user/detail";

	        Map<String, Object> config = new LinkedHashMap<>();
	        config.put("os", "pc");
	        config.put("appver", "");
	        config.put("osver", "");
	        config.put("deviceId", "pyncm!");
	        config.put("requestId", String.valueOf(20000000 + new Random().nextInt(10000000)));

	        Map<String, Object> payload = new LinkedHashMap<>();
	        payload.put("userId", userId);
	        payload.put("header", new ObjectMapper().writeValueAsString(config));

	        String jsonPayload = new ObjectMapper().writeValueAsString(payload);
	        String digest = md5Hex("nobody" + apiPath + "use" + jsonPayload + "md5forencrypt");
	        String rawParams = apiPath + "-36cd479b6b5-" + jsonPayload + "-36cd479b6b5-" + digest;
	        String encParams = aesEncryptECB(rawParams, AES_KEY);

	        Map<String, String> headers = new HashMap<>();
	        headers.put("Referer", "");
	        headers.put("Cookie", getCookieValue(this.cookie));

	        Map<String, String> params = new HashMap<>();
	        params.put("params", encParams);

	        return HttpClientUtil.postForm(url, headers, params);
	    }

	    
	    
	    /**
	     * 获取用户歌单
	     * @param userId
	     * @param limit
	     * @param offset
	     * @return
	     * @throws Exception
	     */
	    public String getUserPlaylist(Long userId, int limit, int offset) throws Exception {
	        String apiPath = "/api/user/playlist";
	        String url = "https://interface3.music.163.com/eapi/user/playlist";

	        Map<String, Object> config = new LinkedHashMap<>();
	        config.put("os", "pc");
	        config.put("appver", "");
	        config.put("osver", "");
	        config.put("deviceId", "pyncm!");
	        config.put("requestId", String.valueOf(20000000 + new Random().nextInt(10000000)));

	        Map<String, Object> payload = new LinkedHashMap<>();
	        payload.put("uid", userId);
	        payload.put("limit", limit);
	        payload.put("offset", offset);
	        payload.put("includeVideo", true);
	        payload.put("header", new ObjectMapper().writeValueAsString(config));

	        String jsonPayload = new ObjectMapper().writeValueAsString(payload);
	        String digest = md5Hex("nobody" + apiPath + "use" + jsonPayload + "md5forencrypt");
	        String rawParams = apiPath + "-36cd479b6b5-" + jsonPayload + "-36cd479b6b5-" + digest;
	        String encParams = aesEncryptECB(rawParams, AES_KEY);

	        Map<String, String> headers = new HashMap<>();
	        headers.put("Referer", "");
	        headers.put("Cookie", getCookieValue(this.cookie));

	        Map<String, String> params = new HashMap<>();
	        params.put("params", encParams);

	        return HttpClientUtil.postForm(url, headers, params);
	    }

	    
	    /**
	     * 搜索 
	     * @param keyword 关键词
	     * @param limit 每页条数
	     * @param offset 偏移量
	     * @param type  搜索类型
	     * 	单曲	1
			歌手	100
			专辑	10
			歌单	1000
			用户	1002
			MV	1004
			歌词	1006
	     * @return
	     * @throws Exception
	     */
	    public String searchMusic(String keyword, int limit, int offset, int type) throws Exception {
	        String apiPath = "/api/cloudsearch/pc";
	        String url = "https://interface3.music.163.com/eapi/cloudsearch/pc";

	        Map<String, Object> config = new LinkedHashMap<>();
	        config.put("os", "pc");
	        config.put("appver", "");
	        config.put("osver", "");
	        config.put("deviceId", "pyncm!");
	        config.put("requestId", String.valueOf(20000000 + new Random().nextInt(10000000)));

	        Map<String, Object> payload = new LinkedHashMap<>();
	        payload.put("s", keyword);   // 搜索关键词
	        payload.put("limit", limit); // 每页条数
	        payload.put("offset", offset); // 偏移量
	        payload.put("type", type);   // 搜索类型：1=单曲 10=专辑 100=歌手 1000=歌单 ...
	        payload.put("header", new ObjectMapper().writeValueAsString(config));

	        String jsonPayload = new ObjectMapper().writeValueAsString(payload);
	        String digest = md5Hex("nobody" + apiPath + "use" + jsonPayload + "md5forencrypt");
	        String rawParams = apiPath + "-36cd479b6b5-" + jsonPayload + "-36cd479b6b5-" + digest;
	        String encParams = aesEncryptECB(rawParams, AES_KEY);

	        Map<String, String> headers = new HashMap<>();
	        headers.put("Referer", "");
	        headers.put("Cookie", getCookieValue(this.cookie));

	        Map<String, String> params = new HashMap<>();
	        params.put("params", encParams);

	        return HttpClientUtil.postForm(url, headers, params);
	    }

	    
	    
	    //专辑详情
	    public String getAlbumDetail(Long albumId) throws Exception {
	        String apiPath = "/api/v1/album/" + albumId;
	        String url = "https://interface3.music.163.com/eapi/v1/album/" + albumId;

	        Map<String, Object> config = new LinkedHashMap<>();
	        config.put("os", "pc");
	        config.put("appver", "");
	        config.put("osver", "");
	        config.put("deviceId", "pyncm!");
	        config.put("requestId", String.valueOf(20000000 + new Random().nextInt(10000000)));

	        Map<String, Object> payload = new LinkedHashMap<>();
	        payload.put("header", new ObjectMapper().writeValueAsString(config));
	        payload.put("total", "true"); // 通常需要设置此字段
	        payload.put("id", albumId);   // 实际上该字段在路径中，但也加在 payload 中

	        String jsonPayload = new ObjectMapper().writeValueAsString(payload);
	        String digest = md5Hex("nobody" + apiPath + "use" + jsonPayload + "md5forencrypt");
	        String rawParams = apiPath + "-36cd479b6b5-" + jsonPayload + "-36cd479b6b5-" + digest;
	        String encParams = aesEncryptECB(rawParams, AES_KEY);

	        Map<String, String> headers = new HashMap<>();
	        headers.put("Referer", "");
	        headers.put("Cookie", getCookieValue(this.cookie));

	        Map<String, String> params = new HashMap<>();
	        params.put("params", encParams);

	        return HttpClientUtil.postForm(url, headers, params);
	    }

	    
	    //歌单详情
	    public String getPlaylistDetail(Long playlistId) throws Exception {
	        String url = "https://interface3.music.163.com/eapi/v6/playlist/detail";
	        String apiPath = "/api/v6/playlist/detail";

	        Map<String, Object> config = new LinkedHashMap<>();
	        config.put("os", "pc");
	        config.put("appver", "");
	        config.put("osver", "");
	        config.put("deviceId", "pyncm!");
	        config.put("requestId", String.valueOf(20000000 + new Random().nextInt(10000000)));

	        Map<String, Object> payload = new LinkedHashMap<>();
	        payload.put("id", playlistId);
	        payload.put("n", 1000);  // 获取最多 1000 首歌
	        payload.put("s", 8);     // 歌单订阅者数等冗余信息
	        payload.put("header", new ObjectMapper().writeValueAsString(config));

	        String jsonPayload = new ObjectMapper().writeValueAsString(payload);
	        String digest = md5Hex("nobody" + apiPath + "use" + jsonPayload + "md5forencrypt");
	        String rawParams = apiPath + "-36cd479b6b5-" + jsonPayload + "-36cd479b6b5-" + digest;
	        String encParams = aesEncryptECB(rawParams, AES_KEY);

	        Map<String, String> headers = new HashMap<>();
	        headers.put("Referer", "");
	        headers.put("Cookie", getCookieValue(this.cookie));

	        Map<String, String> params = new HashMap<>();
	        params.put("params", encParams);

	        return HttpClientUtil.postForm(url, headers, params);
	    }

	    
	    //歌词
	    public String getLyric(Long songId) throws Exception {
	        String url = "https://interface3.music.163.com/eapi/song/lyric";
	        String apiPath = "/api/song/lyric";

	        Map<String, Object> config = new LinkedHashMap<>();
	        config.put("os", "pc");
	        config.put("appver", "");
	        config.put("osver", "");
	        config.put("deviceId", "pyncm!");
	        config.put("requestId", String.valueOf(20000000 + new Random().nextInt(10000000)));

	        Map<String, Object> payload = new LinkedHashMap<>();
	        payload.put("id", songId);
	        payload.put("os", "pc");
	        payload.put("lv", -1);
	        payload.put("kv", -1);
	        payload.put("tv", -1);
	        payload.put("header", new ObjectMapper().writeValueAsString(config));

	        String jsonPayload = new ObjectMapper().writeValueAsString(payload);
	        String digest = md5Hex("nobody" + apiPath + "use" + jsonPayload + "md5forencrypt");
	        String rawParams = apiPath + "-36cd479b6b5-" + jsonPayload + "-36cd479b6b5-" + digest;
	        String encParams = aesEncryptECB(rawParams, AES_KEY);

	        Map<String, String> headers = new HashMap<>();
	        headers.put("Referer", "");
	        headers.put("Cookie", getCookieValue(this.cookie));

	        Map<String, String> params = new HashMap<>();
	        params.put("params", encParams);

	        return HttpClientUtil.postForm(url, headers, params);
	    }

	    
	    //获取详情
	    public String songDetail(List<Long> ids) throws Exception {
	        String url = "https://interface3.music.163.com/eapi/v3/song/detail";
	        String apiPath = "/api/v3/song/detail";

	        Map<String, Object> config = new LinkedHashMap<>();
	        config.put("os", "pc");
	        config.put("appver", "");
	        config.put("osver", "");
	        config.put("deviceId", "pyncm!");
	        config.put("requestId", String.valueOf(20000000 + new Random().nextInt(10000000)));

	        Map<String, Object> payload = new LinkedHashMap<>();
	        payload.put("c", new ObjectMapper().writeValueAsString(
	                ids.stream().map(id -> {
	                    Map<String, Object> item = new HashMap<>();
	                    item.put("id", id);
	                    return item;
	                }).toArray()
	        ));
	        payload.put("ids", ids);
	        payload.put("header", new ObjectMapper().writeValueAsString(config));

	        String jsonPayload = new ObjectMapper().writeValueAsString(payload);
	        String digest = md5Hex("nobody" + apiPath + "use" + jsonPayload + "md5forencrypt");
	        String rawParams = apiPath + "-36cd479b6b5-" + jsonPayload + "-36cd479b6b5-" + digest;
	        String encParams = aesEncryptECB(rawParams, AES_KEY);

	        Map<String, String> headers = new HashMap<>();
	        headers.put("Referer", "");
	        headers.put("Cookie", getCookieValue(this.cookie));

	        Map<String, String> params = new HashMap<>();
	        params.put("params", encParams);

	        return HttpClientUtil.postForm(url, headers, params);
	    }

	    
	    
	    //获取下载url
	    public String urlV1(Long id, String level) throws Exception {
	        String url = "https://interface3.music.163.com/eapi/song/enhance/player/url/v1";
	        String apiPath = "/api/song/enhance/player/url/v1";

	        Map<String, Object> config = new LinkedHashMap<>();
	        config.put("os", "pc");
	        config.put("appver", "");
	        config.put("osver", "");
	        config.put("deviceId", "pyncm!");
	        config.put("requestId", String.valueOf(20000000 + new Random().nextInt(10000000)));

	        Map<String, Object> payload = new LinkedHashMap<>();
	        payload.put("ids", Collections.singletonList(id));
	        payload.put("level", level);
	        payload.put("encodeType", "flac");
	        payload.put("header", new ObjectMapper().writeValueAsString(config));
	        if ("sky".equals(level)) {
	            payload.put("immerseType", "c51");
	        }

	        String jsonPayload = new ObjectMapper().writeValueAsString(payload);
	        String digest = md5Hex("nobody" + apiPath + "use" + jsonPayload + "md5forencrypt");
	        String rawParams = apiPath + "-36cd479b6b5-" + jsonPayload + "-36cd479b6b5-" + digest;
	        String encParams = aesEncryptECB(rawParams, AES_KEY);

	       Map<String, String> headers = new HashMap<String,String>();
	       Map<String, String> params = new HashMap<String,String>();
	       headers.put("Referer", "");
	       headers.put("Cookie",getCookieValue(cookie));
	       params.put("params", encParams);
	       return  HttpClientUtil.postForm(url, headers, params);
	    }
	    
	    
	    //===================================================================================================================
	    //以下为qr登录相关接口
	    
	    
	    // 修复 getLoginQrKey，自动处理 Set-Cookie
	    public String getLoginQrKey() throws Exception {
        cleanExpiredQrCodes();
        
        String apiPath = "/api/login/qrcode/unikey";
        String url = "https://interface3.music.163.com/eapi/login/qrcode/unikey";

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("os", "pc");
        config.put("appver", "");
        config.put("osver", "");
        config.put("deviceId", "pyncm!");
        config.put("requestId", String.valueOf(20000000 + new Random().nextInt(10000000)));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", 1);
        payload.put("header", new ObjectMapper().writeValueAsString(config));

        String jsonPayload = new ObjectMapper().writeValueAsString(payload);
        String digest = md5Hex("nobody" + apiPath + "use" + jsonPayload + "md5forencrypt");
        String rawParams = apiPath + "-36cd479b6b5-" + jsonPayload + "-36cd479b6b5-" + digest;
        String encParams = aesEncryptECB(rawParams, AES_KEY);

        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "");
        headers.put("Cookie", getCookieValue(this.cookie));

        Map<String, String> params = new HashMap<>();
        params.put("params", encParams);

        log.info("[getLoginQrKey] 请求Cookie: {}", headers.get("Cookie"));
        Pair<String, Header[]> respPair = HttpClientUtil.postFormAndReturnHeaders(url, headers, params);
        String response = respPair.getLeft();
        Header[] allHeaders = respPair.getRight();
        log.info("[getLoginQrKey] 响应内容: {}", response);
        // 自动刷新 Cookie
        if (allHeaders != null) {
            for (Header header : allHeaders) {
                if (header.getName().equalsIgnoreCase("Set-Cookie")) {
                    String newCookie = header.getValue();
                    log.info("[getLoginQrKey] Set-Cookie: {}", newCookie);
                    if (newCookie.startsWith("MUSIC_U")) {
                        refreshCookie(newCookie);
                        break;
                    }
                }
            }
        }
        // 解析响应并缓存二维码信息
        try {
            JSONObject jsonResponse = JSON.parseObject(response);
            if (jsonResponse.getInteger("code") == 200) {
                String unikey = jsonResponse.getString("unikey");
                String qrUrl = "https://music.163.com/login?codekey=" + unikey;
                qrCodeCache.put(unikey, new QrCodeInfo(unikey, qrUrl));
                log.debug("二维码已缓存: {}", unikey);
            }
        } catch (Exception e) {
            log.warn("缓存二维码信息失败", e);
        }
        
        return response;
    }
	    
	    
	    /**
	     *  登录状态说明（checkLoginQrStatus返回值）：
				code = 800：二维码过期
				
				code = 801：等待扫码
				
				code = 802：已扫码，等待确认
				
				code = 803：登录成功，返回 cookie（需保存）
	     * @param unikey
	     * @return
	     * @throws Exception
	     */
	    public String checkLoginQrStatus(String unikey) throws Exception {
        // 检查缓存中的二维码信息
        QrCodeInfo qrInfo = qrCodeCache.get(unikey);
        if (qrInfo != null) {
            // 如果二维码已过期，直接返回过期状态
            if (qrInfo.isExpired()) {
                log.debug("二维码已过期（缓存检查）: {}", unikey);
                qrCodeCache.remove(unikey);
                return "{\"code\":800,\"message\":\"二维码不存在或已过期\"}";
            }
            
            // 如果不需要检查，返回缓存的状态
            if (!qrInfo.shouldCheck()) {
                log.debug("跳过二维码状态检查（频率限制）: {}", unikey);
                return String.format("{\"code\":%d,\"message\":\"%s\"}", 
                    qrInfo.lastStatusCode, getStatusMessage(qrInfo.lastStatusCode));
            }
        }
        
        String apiPath = "/api/login/qrcode/client/login";
        String url = "https://interface3.music.163.com/eapi/login/qrcode/client/login";

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("os", "pc");
        config.put("appver", "");
        config.put("osver", "");
        config.put("deviceId", "pyncm!");
        config.put("requestId", String.valueOf(20000000 + new Random().nextInt(10000000)));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", 1);
        payload.put("key", unikey);
        payload.put("header", new ObjectMapper().writeValueAsString(config));

        String jsonPayload = new ObjectMapper().writeValueAsString(payload);
        String digest = md5Hex("nobody" + apiPath + "use" + jsonPayload + "md5forencrypt");
        String rawParams = apiPath + "-36cd479b6b5-" + jsonPayload + "-36cd479b6b5-" + digest;
        String encParams = aesEncryptECB(rawParams, AES_KEY);

        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "");
        headers.put("Cookie", getCookieValue(this.cookie));

        Map<String, String> params = new HashMap<>();
        params.put("params", encParams);

        log.info("[checkLoginQrStatus] 请求Cookie: {}", headers.get("Cookie"));
        log.info("[checkLoginQrStatus] 请求unikey: {}", unikey);
        try {
            Pair<String,Header[]> responseAll = HttpClientUtil.postFormAndReturnHeaders(url, headers, params);
            String response = responseAll.getLeft();
            log.info("[checkLoginQrStatus] 响应内容: {}", response);
            
            // 检查响应是否为空或无效
            if (response == null || response.trim().isEmpty()) {
                log.warn("二维码状态检查返回空响应: {}", unikey);
                if (qrInfo != null) {
                    return String.format("{\"code\":%d,\"message\":\"%s\"}", 
                        qrInfo.lastStatusCode, getStatusMessage(qrInfo.lastStatusCode));
                }
                return "{\"code\":800,\"message\":\"网络异常，请重试\"}";
            }
            
            // 解析 JSON
            JSONObject responseJSON = null;
            try {
                responseJSON = JSON.parseObject(response);
            } catch (Exception jsonException) {
                log.warn("解析二维码状态响应JSON失败: {}, 响应内容: {}", unikey, response, jsonException);
                if (qrInfo != null) {
                    return String.format("{\"code\":%d,\"message\":\"%s\"}", 
                        qrInfo.lastStatusCode, getStatusMessage(qrInfo.lastStatusCode));
                }
                return "{\"code\":800,\"message\":\"响应格式错误\"}";
            }
            
            // 检查解析后的JSON对象是否为空
            if (responseJSON == null) {
                log.warn("二维码状态响应JSON解析为空: {}, 响应内容: {}", unikey, response);
                if (qrInfo != null) {
                    return String.format("{\"code\":%d,\"message\":\"%s\"}", 
                        qrInfo.lastStatusCode, getStatusMessage(qrInfo.lastStatusCode));
                }
                return "{\"code\":800,\"message\":\"响应解析失败\"}";
            }
            
            Integer code = (Integer) responseJSON.get("code");
            
            // 更新缓存中的状态
            if (qrInfo != null && code != null) {
                qrInfo.updateStatus(code);
            }
            
            // 优化日志级别，避免刷屏
            if (code != null && code == 803) {
                log.info("二维码扫码登录成功: code={}", code);
                // 登录成功后清理缓存
                qrCodeCache.remove(unikey);
            } else if (code != null && code == 800) {
                log.debug("二维码已过期: code={}", code);
                // 过期后清理缓存
                qrCodeCache.remove(unikey);
            } else {
                log.debug("二维码状态: code={}", code);
            }
            
            if (code != null && code.intValue() == 803) {
                // 登录成功，获取 cookie 写入文件
                Header[] headersresp = responseAll.getRight();
                boolean cookieFound = false;
                for(int i = 0; i < headersresp.length ; i++) {
                    Header header = headersresp[i];
                    
                    if (header.getName().equalsIgnoreCase("Set-Cookie")) {
                        String newCookie = (String) header.getValue();
                        if (newCookie.startsWith("MUSIC_U")) {
                            log.info("登录成功!开始写入cookie:{}",newCookie);
                            refreshCookie(newCookie);
                            cookieFound = true;
                            break;
                        }
                    }
                }
                
                // 如果没有找到MUSIC_U cookie，尝试从响应体中获取
                if (!cookieFound) {
                    try {
                        JSONObject data = responseJSON.getJSONObject("data");
                        if (data != null) {
                            String cookieFromData = data.getString("cookie");
                            if (cookieFromData != null && cookieFromData.contains("MUSIC_U")) {
                                log.info("从响应体获取cookie并写入: {}", cookieFromData);
                                refreshCookie(cookieFromData);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("从响应体获取cookie失败", e);
                    }
                }
                
                // 确保登录状态正确设置
                if (!this.ready) {
                    log.info("强制设置登录状态为已登录");
                    this.ready = true;
                }
            }
            
            return response;
        } catch (Exception e) {
            log.warn("检查二维码状态失败: {}", unikey, e);
            // 网络错误时，如果有缓存信息，返回缓存状态
            if (qrInfo != null) {
                return String.format("{\"code\":%d,\"message\":\"%s\"}", 
                    qrInfo.lastStatusCode, getStatusMessage(qrInfo.lastStatusCode));
            }
            throw e;
        }
    }
    
    /**
     * 获取状态码对应的消息
     */
    private String getStatusMessage(int code) {
        switch (code) {
            case 800:
                return "二维码不存在或已过期";
            case 801:
                return "等待扫码";
            case 802:
                return "已扫码，等待确认";
            case 803:
                return "登录成功";
            default:
                return "未知状态";
        }
    }
	    
	    


	    public static void main(String[] args) throws Exception {
	    	NeteaseAPIService service = new NeteaseAPIService();
	    	service.cookie = "1234"; 
	        //String json = service.urlV1(589938L, "lossless");
	        //System.out.println(json);
	        
	        //String json1 = service.songDetail( Lists.newArrayList(589938L) );
	        //System.out.println(json1);
	        
	        //String lyricJson = service.getLyric(589938L);
	        //System.out.println(lyricJson);
	    	
	    	//String detailJson = service.getPlaylistDetail(13615904765L);
	        //System.out.println(detailJson);
	    	
	    	//String albumjson = service.getAlbumDetail(274821019L); 
	        //System.out.println(albumjson);
	    	
	    	 //String searchresult = service.searchMusic("周杰伦", 10, 0, 1);
	    	 //System.out.println(searchresult);
	    	
	    	
	    	//String userPlayList = service.getUserPlaylist(321664453L, 20, 0); 
	        //System.out.println(userPlayList);
	    	
	    	
	    	//Long id = service.getAccountInfo();
	    	//System.out.println(id);
	    }

		
}
