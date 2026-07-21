package com.xakcch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * 启动程序
 *
 * @author liuyonghui
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
@ComponentScan(basePackages = {"com.xakcch", "com.xakcch"})
public class XkyProjectApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(XkyProjectApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  项目信息管理系统启动成功   ლ(´ڡ`ლ)ﾞ  ");
    }
}
