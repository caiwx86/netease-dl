package com.pewee.neteasemusic.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.pewee.neteasemusic.enums.CommonRespInfo;
import com.pewee.neteasemusic.models.common.RespEntity;
import com.pewee.neteasemusic.service.NeteaseAPIService;
import com.pewee.neteasemusic.utils.QrUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * 提供扫码登录功能
 * @author pewee
 *
 */
@Controller
@Slf4j
public class QrLoginController {
	
	@Autowired
    private NeteaseAPIService neteaseAPIService;
	
	@GetMapping("/")
    public String all(Model model) {
        return generateQr(model); 
    }
	
	
	@GetMapping("/home")
    public String home(Model model) {
		if(neteaseAPIService.checkReady()) {
    		log.info("已登录,跳转到功能页面");
    		return "home";
    	} else {
    		log.info("未登录,重定向到登录页面");
    		return generateQr(model);
    	}
    }
	

    /**
     * 获取二维码 key，并返回二维码链接和Base64二维码图
     */
    @GetMapping("/index")
    public String generateQr(Model model) {
    	if(neteaseAPIService.checkReady()) {
    		log.info("已登录,跳转到功能页面");
    		return "home";
    	}
    	try {
    		log.info("未登录,生成Qr码");
            String unikey = null;
			String reqp = neteaseAPIService.getLoginQrKey();
			JSONObject jsonObject = JSON.parseObject(reqp);
			unikey = jsonObject.getString("unikey");
            Assert.notNull(unikey, "unikey不能为空!");

            String qrUrl = "https://music.163.com/login?codekey=" + unikey;
            String qrImageBase64 = QrUtils.generateQrBase64(qrUrl);

            model.addAttribute("unikey", unikey);
            model.addAttribute("qrUrl", qrUrl);
            model.addAttribute("qrImageBase64", "data:image/png;base64," + qrImageBase64); // 前端 <img :src="qrImageBase64">
            log.info("二维码生成成功: unikey={}", unikey);
            return "qr_login"; // Thymeleaf 模板名
    	} catch (Exception e) {
            log.error("生成二维码失败", e);
            model.addAttribute("error", "生成二维码失败：" + e.getMessage());
            return "error";
        }
    }

    /**
     * 轮询查询二维码扫码状态（兼容旧接口，返回标准结构）
     */
    @ResponseBody
    @GetMapping("/qr/status")
    public Map<String, Object> checkQrStatusCompat(@RequestParam("unikey") String unikey) throws Exception {
        log.debug("检查二维码状态（兼容接口）: unikey={}", unikey);
        String rawResp = neteaseAPIService.checkLoginQrStatus(unikey);
        JSONObject resp = JSON.parseObject(rawResp);
        Map<String, Object> result = new HashMap<>();
        result.put("code", resp.getInteger("code"));
        result.put("message", resp.getString("message"));
        result.put("data", resp.getInteger("code") == 803);
        return result;
    }
    
    /**
     * 轮询查询二维码扫码状态（新接口，与 /qr/status 保持一致）
     */
    @ResponseBody
    @GetMapping("/qr/status2")
    public Map<String, Object> checkQrStatus2(@RequestParam("unikey") String unikey) throws Exception {
        log.debug("检查二维码状态（新接口）: unikey={}", unikey);
        String rawResp = neteaseAPIService.checkLoginQrStatus(unikey);
        JSONObject resp = JSON.parseObject(rawResp);
        Map<String, Object> result = new HashMap<>();
        result.put("code", resp.getInteger("code"));
        result.put("message", resp.getString("message"));
        result.put("data", resp.getInteger("code") == 803);
        
        // 如果是登录成功状态，记录详细信息
        if (resp.getInteger("code") == 803) {
            log.info("二维码登录成功确认: unikey={}", unikey);
        }
        
        return result;
    }
    
    /**
     * 查询当前登录状态
     */
    @ResponseBody
    @GetMapping("/login/status")
    public RespEntity<Boolean> checkLoginStatus()  throws Exception{
        boolean isReady = neteaseAPIService.checkReady();
        log.debug("检查登录状态: ready={}", isReady);
        return RespEntity.apply(CommonRespInfo.SUCCESS, isReady);
    }

    

}
