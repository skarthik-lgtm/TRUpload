package com.trupload.service.RTR;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.trupload.config.RtrProperties;

@Service
public class RemoteExecutionService {

    private static final String EXIT_CODE_MARKER = "__RTR_EXIT_CODE__=";
    private static final String OUTPUT_PATH_MARKER = "__RTR_OUTPUT_PATH__=";
    private static final String FILE_CREATED_MARKER = "__RTR_FILE_CREATED__=";
    private static final String FILE_UPDATED_MARKER = "__RTR_FILE_UPDATED__=";
    private static final String FILE_SIZE_MARKER = "__RTR_FILE_SIZE__=";
    private static final String FILE_LAST_WRITE_MARKER = "__RTR_FILE_LAST_WRITE__=";
    private static final String TOOL_OUTPUT_MARKER = "__RTR_TOOL_OUTPUT_B64__=";
    private static final DateTimeFormatter LOG_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter LOG_FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private static final String POWERSHELL_SCRIPT = """
            $ErrorActionPreference = 'Stop'
            $remoteScript = {
                param(
                    [string] $ExecutablePath,
                    [string] $OutputDirectory,
                    [string] $FileExtension,
                    [string] $Dsn,
                    [string] $DatabaseUsername,
                    [string] $DatabasePassword,
                    [string] $RouteGroupId
                )
                $ErrorActionPreference = 'Stop'
                if (-not (Test-Path -LiteralPath $ExecutablePath -PathType Leaf)) {
                    throw 'Configured ie.exe was not found on the RTR server.'
                }
                if (-not (Test-Path -LiteralPath $OutputDirectory -PathType Container)) {
                    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
                }
                $outputPath = Join-Path $OutputDirectory ($RouteGroupId + '.' + $FileExtension)
                $priorFile = $null
                if (Test-Path -LiteralPath $outputPath -PathType Leaf) {
                    $priorFile = Get-Item -LiteralPath $outputPath
                }
                $toolArguments = @(
                    '/e',
                    '/r',
                    '/oy',
                    ('/dsn:' + $Dsn),
                    ('/UID:' + $DatabaseUsername),
                    ('/PWD:' + $DatabasePassword),
                    ('/DATA:routegroupid = ' + $RouteGroupId),
                    $outputPath
                )
                $toolOutput = @(& $ExecutablePath @toolArguments 2>&1)
                $exitCode = $LASTEXITCODE
                $toolOutputText = ($toolOutput | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine
                $toolOutputEncoded = [Convert]::ToBase64String(
                    [Text.Encoding]::UTF8.GetBytes($toolOutputText))
                $fileCreated = Test-Path -LiteralPath $outputPath -PathType Leaf
                $fileUpdated = $false
                $fileSize = 0
                $fileLastWrite = ''
                if ($fileCreated) {
                    $file = Get-Item -LiteralPath $outputPath
                    $fileSize = $file.Length
                    $fileLastWrite = $file.LastWriteTimeUtc.ToString('o')
                    $fileUpdated = $null -eq $priorFile `
                        -or $file.Length -ne $priorFile.Length `
                        -or $file.LastWriteTimeUtc.Ticks -ne $priorFile.LastWriteTimeUtc.Ticks `
                        -or $file.CreationTimeUtc.Ticks -ne $priorFile.CreationTimeUtc.Ticks
                }
                Write-Output ('__RTR_EXIT_CODE__=' + $exitCode)
                Write-Output ('__RTR_OUTPUT_PATH__=' + $outputPath)
                Write-Output ('__RTR_FILE_CREATED__=' + $fileCreated.ToString().ToLowerInvariant())
                Write-Output ('__RTR_FILE_UPDATED__=' + $fileUpdated.ToString().ToLowerInvariant())
                Write-Output ('__RTR_FILE_SIZE__=' + $fileSize)
                Write-Output ('__RTR_FILE_LAST_WRITE__=' + $fileLastWrite)
                Write-Output ('__RTR_TOOL_OUTPUT_B64__=' + $toolOutputEncoded)
            }

            $timeoutMilliseconds = [int]$env:RTR_CONNECTION_TIMEOUT_SECONDS * 1000
            $sessionOption = New-PSSessionOption -OpenTimeout $timeoutMilliseconds -OperationTimeout $timeoutMilliseconds
            $invokeParameters = @{
                ComputerName = $env:RTR_REMOTE_SERVER
                ScriptBlock = $remoteScript
                ArgumentList = @(
                    $env:RTR_EXECUTABLE_PATH,
                    $env:RTR_OUTPUT_DIRECTORY,
                    $env:RTR_FILE_EXTENSION,
                    $env:RTR_DSN,
                    $env:RTR_DATABASE_USERNAME,
                    $env:RTR_DATABASE_PASSWORD,
                    $env:RTR_ROUTE_GROUP_ID
                )
                SessionOption = $sessionOption
                ErrorAction = 'Stop'
            }
            if (-not [string]::IsNullOrWhiteSpace($env:RTR_REMOTE_USERNAME)) {
                $securePassword = ConvertTo-SecureString $env:RTR_REMOTE_PASSWORD -AsPlainText -Force
                $invokeParameters.Credential = [System.Management.Automation.PSCredential]::new(
                    $env:RTR_REMOTE_USERNAME, $securePassword)
            }
            try {
                Invoke-Command @invokeParameters
            } catch {
                [Console]::Error.WriteLine($_.ToString())
                exit 1
            }
            """;

    public String generateRtrFile(
            RtrProperties properties,
            String dsn,
            String routeGroupId,
            String outputFilePath,
            String requestedBy) {
        LocalDateTime startedAt = LocalDateTime.now();
        LocalDateTime executionStartedAt = null;
        LocalDateTime completedAt = null;
        String executionExitCode = "N/A";
        String toolOutput = "";
        String fileLastWrite = "N/A";
        boolean fileCreated = false;
        boolean fileUpdated = false;
        long fileSize = 0;
        String status = "FAILED";
        String failureDetail = null;
        RuntimeException executionFailure = null;

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    properties.getPowerShellExecutable(),
                    "-NoLogo",
                    "-NoProfile",
                    "-NonInteractive",
                    "-EncodedCommand",
                    encodePowerShell(POWERSHELL_SCRIPT))
                    .redirectErrorStream(true);
            processBuilder.environment().put("RTR_REMOTE_SERVER", properties.getRemoteServer());
            processBuilder.environment().put("RTR_EXECUTABLE_PATH", properties.getRemoteExecutablePath());
            processBuilder.environment().put(
                    "RTR_OUTPUT_DIRECTORY", properties.getOutputDirectory().replace('/', '\\'));
            processBuilder.environment().put("RTR_FILE_EXTENSION", properties.getFileExtension());
            processBuilder.environment().put("RTR_DSN", dsn);
            processBuilder.environment().put("RTR_DATABASE_USERNAME", properties.getDatabaseUsername());
            processBuilder.environment().put("RTR_DATABASE_PASSWORD", properties.getDatabasePassword());
            processBuilder.environment().put("RTR_ROUTE_GROUP_ID", routeGroupId);
            processBuilder.environment().put("RTR_CONNECTION_TIMEOUT_SECONDS",
                    Integer.toString(properties.getConnectionTimeoutSeconds()));
            processBuilder.environment().put("RTR_REMOTE_USERNAME",
                    properties.getRemoteUsername() == null ? "" : properties.getRemoteUsername());
            processBuilder.environment().put("RTR_REMOTE_PASSWORD",
                    properties.getRemotePassword() == null ? "" : properties.getRemotePassword());

            executionStartedAt = LocalDateTime.now();
            Process process;
            try {
                process = processBuilder.start();
            } catch (IOException exception) {
                throw new IllegalStateException("Could not start the configured PowerShell client.", exception);
            }

            CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
                try {
                    return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException exception) {
                    throw new IllegalStateException("Could not read PowerShell output.", exception);
                }
            });

            String commandOutput;
            try {
                boolean completed = process.waitFor(
                        properties.getExecutionTimeoutSeconds(), TimeUnit.SECONDS);
                if (!completed) {
                    process.destroyForcibly();
                    throw new IllegalStateException("Remote RTR generation timed out.");
                }

                commandOutput = output.get(10, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Remote RTR generation was interrupted.", exception);
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
                process.destroyForcibly();
                throw new IllegalStateException("Could not collect the PowerShell result.", exception);
            }

            toolOutput = decodedMarker(commandOutput, TOOL_OUTPUT_MARKER);
            toolOutput = redactSensitiveInformation(toolOutput, properties);
            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        "WinRM RTR generation failed (PowerShell exit code "
                                + process.exitValue() + "). " + usefulOutput(commandOutput, toolOutput, properties));
            }

            String reportedExitCode = markerValue(commandOutput, EXIT_CODE_MARKER);
            if (reportedExitCode == null) {
                throw new IllegalStateException(
                        "WinRM completed without reporting the ie.exe exit code. "
                                + usefulOutput(commandOutput, toolOutput, properties));
            }
            executionExitCode = reportedExitCode;

            String reportedPath = markerValue(commandOutput, OUTPUT_PATH_MARKER);
            if (!outputFilePath.equals(reportedPath)) {
                throw new IllegalStateException(
                        "WinRM did not confirm the requested RTR output path. "
                                + usefulOutput(commandOutput, toolOutput, properties));
            }

            String createdMarker = markerValue(commandOutput, FILE_CREATED_MARKER);
            fileCreated = Boolean.parseBoolean(createdMarker);
            String updatedMarker = markerValue(commandOutput, FILE_UPDATED_MARKER);
            fileUpdated = Boolean.parseBoolean(updatedMarker);
            String sizeMarker = markerValue(commandOutput, FILE_SIZE_MARKER);
            if (sizeMarker != null) {
                try {
                    fileSize = Long.parseLong(sizeMarker);
                } catch (NumberFormatException exception) {
                    throw new IllegalStateException("WinRM returned an invalid RTR file size.", exception);
                }
            }
            String reportedLastWrite = markerValue(commandOutput, FILE_LAST_WRITE_MARKER);
            if (reportedLastWrite != null && !reportedLastWrite.isBlank()) {
                fileLastWrite = reportedLastWrite;
            }

            if (!"0".equals(executionExitCode)) {
                throw new IllegalStateException(
                        "ie.exe failed with exit code " + executionExitCode + ". "
                                + usefulOutput(commandOutput, toolOutput, properties));
            }
            if (!fileCreated) {
                throw new IllegalStateException(
                        "ie.exe completed without creating the requested RTR file. "
                                + usefulOutput(commandOutput, toolOutput, properties));
            }
            if (!fileUpdated) {
                throw new IllegalStateException(
                        "ie.exe completed without creating or updating the requested RTR file. "
                                + usefulOutput(commandOutput, toolOutput, properties));
            }

            status = "SUCCESS";
            return outputFilePath;
        } catch (RuntimeException exception) {
            executionFailure = exception;
            failureDetail = redactSensitiveInformation(exception.getMessage(), properties);
            throw exception;
        } finally {
            completedAt = LocalDateTime.now();
            try {
                writeAttemptLog(
                        properties,
                        startedAt,
                        executionStartedAt,
                        completedAt,
                        requestedBy,
                        dsn,
                        routeGroupId,
                        executionExitCode,
                        toolOutput,
                        fileCreated,
                        fileUpdated,
                        outputFilePath,
                        fileSize,
                        fileLastWrite,
                        status,
                        failureDetail);
            } catch (IOException | RuntimeException logException) {
                IllegalStateException logFailure = new IllegalStateException(
                        "Could not write the RTR generation attempt log.", logException);
                if (executionFailure != null) {
                    executionFailure.addSuppressed(logFailure);
                } else {
                    throw logFailure;
                }
            }
        }
    }

    private void writeAttemptLog(
            RtrProperties properties,
            LocalDateTime startedAt,
            LocalDateTime executionStartedAt,
            LocalDateTime completedAt,
            String requestedBy,
            String dsn,
            String routeGroupId,
            String exitCode,
            String toolOutput,
            boolean fileCreated,
            boolean fileUpdated,
            String outputFilePath,
            long fileSize,
            String fileLastWrite,
            String status,
            String failureDetail) throws IOException {
        String fileRouteGroupId = routeGroupId.replaceAll("[^A-Za-z0-9_-]", "_");
        String logFileName = "RTR_" + fileRouteGroupId + "_"
                + startedAt.format(LOG_FILE_TIMESTAMP) + "_"
                + UUID.randomUUID().toString().substring(0, 8) + ".log";
        Path logDirectory = Path.of(properties.getLogDirectory());
        Files.createDirectories(logDirectory);

        String report = """
                RTR GENERATION LOG

                Started At        : %s
                Requested By      : %s
                Application Host  : %s
                Remote Server     : %s

                Database          : %s
                Route Group ID    : %s
                Executable        : %s

                Execution Started : %s
                Execution Ended   : %s
                Execution Status  : %s
                Exit Code         : %s

                IE Console Output :
                %s

                File Exists       : %s
                File Updated This Attempt: %s
                Output File       : %s
                File Size         : %d bytes
                File Last Write   : %s
                Failure Detail    : %s
                Completed At      : %s
                Final Status      : %s
                """.formatted(
                formatTimestamp(startedAt),
                singleLine(requestedBy),
                applicationHostName(),
                singleLine(properties.getRemoteServer()),
                singleLine(dsn),
                singleLine(routeGroupId),
                singleLine(properties.getRemoteExecutablePath()),
                executionStartedAt == null ? "N/A" : formatTimestamp(executionStartedAt),
                formatTimestamp(completedAt),
                status,
                singleLine(exitCode),
                toolOutput == null || toolOutput.isBlank() ? "(no output)" : toolOutput,
                fileCreated,
                fileUpdated,
                singleLine(outputFilePath),
                fileSize,
                singleLine(fileLastWrite),
                failureDetail == null || failureDetail.isBlank() ? "None" : failureDetail,
                formatTimestamp(completedAt),
                status);

        report = redactSensitiveInformation(report, properties);
        Files.writeString(
                logDirectory.resolve(logFileName),
                report,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
    }

    private String usefulOutput(String commandOutput, String toolOutput, RtrProperties properties) {
        if (toolOutput != null && !toolOutput.isBlank()) {
            return toolOutput;
        }
        String safeOutput = redactSensitiveInformation(commandOutput, properties);
        return safeOutput == null || safeOutput.isBlank() ? "No PowerShell diagnostics were returned." : safeOutput;
    }

    private String decodedMarker(String commandOutput, String marker) {
        String value = markerValue(commandOutput, marker);
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("WinRM returned invalid encoded ie.exe output.", exception);
        }
    }

    private String markerValue(String commandOutput, String marker) {
        return commandOutput.lines()
                .map(String::trim)
                .filter(line -> line.startsWith(marker))
                .map(line -> line.substring(marker.length()))
                .findFirst()
                .orElse(null);
    }

    private String redactSensitiveInformation(String text, RtrProperties properties) {
        if (text == null) {
            return null;
        }
        String redacted = text;
        redacted = redactValue(redacted, properties.getDatabasePassword());
        redacted = redactValue(redacted, properties.getRemotePassword());
        redacted = redactValue(redacted, properties.getDatabaseUsername());
        redacted = redactValue(redacted, properties.getRemoteUsername());
        return redacted
                .replaceAll("(?i)(/PWD:)[^\\s\"']+", "$1[REDACTED]")
                .replaceAll("(?i)(/UID:)[^\\s\"']+", "$1[REDACTED]");
    }

    private String redactValue(String text, String value) {
        return value == null || value.isEmpty() ? text : text.replace(value, "[REDACTED]");
    }

    private String formatTimestamp(LocalDateTime timestamp) {
        return timestamp.format(LOG_TIMESTAMP);
    }

    private String singleLine(String value) {
        return value == null ? "N/A" : value.replace('\r', ' ').replace('\n', ' ');
    }

    private String applicationHostName() {
        String hostName = System.getenv("COMPUTERNAME");
        if (hostName == null || hostName.isBlank()) {
            hostName = System.getenv("HOSTNAME");
        }
        if (hostName != null && !hostName.isBlank()) {
            return singleLine(hostName);
        }
        try {
            return singleLine(InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException exception) {
            return "unavailable";
        }
    }

    private String encodePowerShell(String script) {
        return Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
    }
}
