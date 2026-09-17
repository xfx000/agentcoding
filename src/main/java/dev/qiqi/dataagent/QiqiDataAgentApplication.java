package dev.qiqi.dataagent;

import dev.qiqi.dataagent.config.QiqiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(QiqiProperties.class)
public class QiqiDataAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(QiqiDataAgentApplication.class, args);
    }
}
