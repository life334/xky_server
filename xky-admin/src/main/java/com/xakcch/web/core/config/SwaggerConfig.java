package com.xakcch.web.core.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xakcch.common.config.LandPatchConfig;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Swagger2的接口配置
 * 
 * @author liuyonghui
 */
@Configuration
public class SwaggerConfig
{
    @Autowired
    private LandPatchConfig LandPatchConfig;

    /**
     * 自定义的 OpenAPI 对象
     */
    @Bean
    public OpenAPI customOpenApi()
    {
        return new OpenAPI().components(new Components()
            .addSecuritySchemes("apikey", securityScheme()))
            .addSecurityItem(new SecurityRequirement().addList("apikey"))
            .info(getApiInfo());
    }

    @Bean
    public SecurityScheme securityScheme()
    {
        return new SecurityScheme()
            .type(SecurityScheme.Type.APIKEY)
            .name("Authorization")
            .in(SecurityScheme.In.HEADER)
            .scheme("Bearer");
    }

    /**
     * 添加概要信息
     */
    public Info getApiInfo()
    {
        return new Info()
            .title("标题：若依管理系统_接口文档")
            .description("描述：用于管理集团旗下公司的人员信息,具体包括XXX,XXX模块...")
            .contact(new Contact().name(LandPatchConfig.getName()))
            .version("版本号:" + LandPatchConfig.getVersion());
    }
}