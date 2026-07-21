package com.xakcch.web.controller.common;



import java.awt.image.BufferedImage;

import java.io.IOException;

import java.util.concurrent.TimeUnit;

import jakarta.annotation.Resource;

import javax.imageio.ImageIO;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.util.FastByteArrayOutputStream;

import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.RestController;

import com.google.code.kaptcha.Producer;

import com.xakcch.common.config.LandPatchConfig;

import com.xakcch.common.constant.CacheConstants;

import com.xakcch.common.constant.Constants;

import com.xakcch.common.core.domain.AjaxResult;

import com.xakcch.common.core.redis.RedisCache;

import com.xakcch.common.utils.sign.Base64;

import com.xakcch.common.utils.uuid.IdUtils;

import com.xakcch.system.service.ISysConfigService;



/**

 * 楠岃瘉鐮佹搷浣滃鐞?
 * 

 * @author liuyonghui

 */

@RestController

public class CaptchaController

{

    @Resource(name = "captchaProducer")

    private Producer captchaProducer;



    @Resource(name = "captchaProducerMath")

    private Producer captchaProducerMath;



    @Autowired

    private RedisCache redisCache;

    

    @Autowired

    private ISysConfigService configService;

    /**

     * 鐢熸垚楠岃瘉鐮?
     */

    @GetMapping("/captchaImage")

    public AjaxResult getCode(HttpServletResponse response) throws IOException

    {

        AjaxResult ajax = AjaxResult.success();

        boolean captchaEnabled = configService.selectCaptchaEnabled();

        ajax.put("captchaEnabled", captchaEnabled);

        if (!captchaEnabled)

        {

            return ajax;

        }



        // 淇濆瓨楠岃瘉鐮佷俊鎭?
        String uuid = IdUtils.simpleUUID();

        String verifyKey = CacheConstants.CAPTCHA_CODE_KEY + uuid;



        String capStr = null, code = null;

        BufferedImage image = null;



        // 鐢熸垚楠岃瘉鐮?
        String captchaType = LandPatchConfig.getCaptchaType();

        if ("math".equals(captchaType))

        {

            String capText = captchaProducerMath.createText();

            capStr = capText.substring(0, capText.lastIndexOf("@"));

            code = capText.substring(capText.lastIndexOf("@") + 1);

            image = captchaProducerMath.createImage(capStr);

        }

        else if ("char".equals(captchaType))

        {

            capStr = code = captchaProducer.createText();

            image = captchaProducer.createImage(capStr);

        }



        redisCache.setCacheObject(verifyKey, code, Constants.CAPTCHA_EXPIRATION, TimeUnit.MINUTES);

        // 杞崲娴佷俊鎭啓鍑?
        FastByteArrayOutputStream os = new FastByteArrayOutputStream();

        try

        {

            ImageIO.write(image, "jpg", os);

        }

        catch (IOException e)

        {

            return AjaxResult.error(e.getMessage());

        }



        ajax.put("uuid", uuid);

        ajax.put("img", Base64.encode(os.toByteArray()));

        return ajax;

    }

}

