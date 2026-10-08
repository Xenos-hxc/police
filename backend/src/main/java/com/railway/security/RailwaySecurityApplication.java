package com.railway.security;

import java.net.URL;
import java.util.Map;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@MapperScan("com.railway.security.persistence.mapper")
@SpringBootApplication
// 模块化单体的启动入口。介绍项目时沿档案、季度任务、材料、整改、统计的业务链展开；文件扫描和本地推理是独立资源边界。
public class RailwaySecurityApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(RailwaySecurityApplication.class);
        if (isExplodedClasspath()) {
            application.setDefaultProperties(Map.of("spring.profiles.default", "dev"));
        }
        application.run(args);
    }

    private static boolean isExplodedClasspath() {
        URL classResource =
                RailwaySecurityApplication.class.getResource("RailwaySecurityApplication.class");
        return classResource != null && "file".equalsIgnoreCase(classResource.getProtocol());
    }
}
