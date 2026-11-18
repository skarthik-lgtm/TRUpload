
package com.trupload.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "trupload.rtr")
public class RtrProperties {

    private String remoteServer;
    private String remoteExecutablePath;
    private String remoteStagingDirectory;
    private String localOutputDirectory;
    private String fileExtension;
    private int connectionTimeoutSeconds;
    private int executionTimeoutSeconds;

    public String getRemoteServer() {
        return remoteServer;
    }

    public void setRemoteServer(String remoteServer) {
        this.remoteServer = remoteServer;
    }

    public String getRemoteExecutablePath() {
        return remoteExecutablePath;
    }

    public void setRemoteExecutablePath(String remoteExecutablePath) {
        this.remoteExecutablePath = remoteExecutablePath;
    }

    public String getRemoteStagingDirectory() {
        return remoteStagingDirectory;
    }

    public void setRemoteStagingDirectory(String remoteStagingDirectory) {
        this.remoteStagingDirectory = remoteStagingDirectory;
    }

    public String getLocalOutputDirectory() {
        return localOutputDirectory;
    }

    public void setLocalOutputDirectory(String localOutputDirectory) {
        this.localOutputDirectory = localOutputDirectory;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public int getConnectionTimeoutSeconds() {
        return connectionTimeoutSeconds;
    }

    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }

    public int getExecutionTimeoutSeconds() {
        return executionTimeoutSeconds;
    }

    public void setExecutionTimeoutSeconds(int executionTimeoutSeconds) {
        this.executionTimeoutSeconds = executionTimeoutSeconds;
    }
}
