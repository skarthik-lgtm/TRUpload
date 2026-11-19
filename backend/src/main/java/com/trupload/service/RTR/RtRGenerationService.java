package com.trupload.service.RTR;

import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.trupload.config.RtrProperties;

@Service
public class RtRGenerationService {

    private static final Pattern SAFE_DATABASE_NAME = Pattern.compile("CSTR[A-Za-z0-9_]*");
    private static final Pattern SAFE_ROUTE_GROUP_ID = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Pattern SAFE_FILE_EXTENSION = Pattern.compile("[A-Za-z0-9]+");

    private final RtrProperties properties;
    private final RemoteExecutionService remoteExecutionService;

    public RtRGenerationService(
            RtrProperties properties,
            RemoteExecutionService remoteExecutionService) {
        this.properties = properties;
        this.remoteExecutionService = remoteExecutionService;
    }

    public String generate(String databaseName, String routeGroupId, String requestedBy) {
        if (databaseName == null || !SAFE_DATABASE_NAME.matcher(databaseName).matches()) {
            throw new IllegalArgumentException("Invalid RTR data source name.");
        }
        if (routeGroupId == null || !SAFE_ROUTE_GROUP_ID.matcher(routeGroupId).matches()) {
            throw new IllegalArgumentException("Invalid route group ID for RTR generation.");
        }
        if (isBlank(properties.getRemoteServer())
                || isBlank(properties.getRemoteExecutablePath())
                || isBlank(properties.getOutputDirectory())
                || isBlank(properties.getPowerShellExecutable())) {
            throw new IllegalStateException("RTR remote execution is not fully configured.");
        }
        if (isBlank(properties.getDatabaseUsername()) || isBlank(properties.getDatabasePassword())) {
            throw new IllegalStateException("RTR database credentials are not configured.");
        }
        if (properties.getRemoteUsername() != null
                && !properties.getRemoteUsername().isBlank()
                && isBlank(properties.getRemotePassword())) {
            throw new IllegalStateException("RTR WinRM credentials are incomplete.");
        }
        if (properties.getExecutionTimeoutSeconds() <= 0
                || properties.getConnectionTimeoutSeconds() <= 0) {
            throw new IllegalStateException("RTR execution timeouts must be greater than zero.");
        }
        if (isBlank(properties.getLogDirectory())) {
            throw new IllegalStateException("RTR log directory is not configured.");
        }

        String fileExtension = properties.getFileExtension();
        if (isBlank(fileExtension) || !SAFE_FILE_EXTENSION.matcher(fileExtension).matches()) {
            throw new IllegalStateException("RTR file extension is invalid.");
        }

        String outputDirectory = properties.getOutputDirectory()
                .trim()
                .replace('/', '\\')
                .replaceAll("\\\\+$", "");
        String outputFilePath = outputDirectory + "\\" + routeGroupId + "." + fileExtension;
        return remoteExecutionService.generateRtrFile(
                properties, databaseName, routeGroupId, outputFilePath, requestedBy);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
