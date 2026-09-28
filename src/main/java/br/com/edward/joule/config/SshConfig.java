// SPDX-License-Identifier: GPL-3.0-only
// SPDX-FileCopyrightText: 2026 Edward
package br.com.edward.joule.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "ssh")
public class SshConfig {

    private Integer sshTimeout = 5000;
    private String shutdownCommand = "sudo -n poweroff";
    private List<RemoteServer> servers = new ArrayList<>();

    @Data
    public static class RemoteServer {
        private String name;
        private String host;
        private Integer port = 22;
        private String user;
        private String privateKeyPath;
    }
}
