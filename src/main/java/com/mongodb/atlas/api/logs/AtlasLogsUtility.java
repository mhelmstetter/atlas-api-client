package com.mongodb.atlas.api.logs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.atlas.api.clients.AtlasApiClient;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.PropertiesDefaultProvider;

@Command(name = "AtlasLogsUtility", mixinStandardHelpOptions = true, description = "MongoDB Atlas Logs Utility - Download compressed log files for a cluster", defaultValueProvider = PropertiesDefaultProvider.class)
public class AtlasLogsUtility implements Callable<Integer> {

	private static final Logger logger = LoggerFactory.getLogger(AtlasLogsUtility.class);

	@Option(names = { "--apiPublicKey" }, description = "Atlas API public key", required = false)
	private String apiPublicKey;

	@Option(names = { "--apiPrivateKey" }, description = "Atlas API private key", required = false)
	private String apiPrivateKey;

	@Option(names = { "--projectNames" }, description = "project names to be processed", required = false, split = ",")
	private Set<String> projectNames;

	@Option(names = { "--config",
			"-c" }, description = "config file", required = false, defaultValue = "atlas-client.properties")
	private File configFile;

	@Option(names = { "--debugLevel" }, description = "Debug level (0-3)", defaultValue = "2")
	private int debugLevel;

	@Option(names = {"--clusterName"}, description = "Cluster name", arity = "0..1")
	private String clusterName;

	@Option(names = { "--outputDir", "-o" }, description = "Output directory for log files", defaultValue = "./logs")
	private String outputDirectory;

	@Option(names = { "--days",
			"-d" }, description = "Number of days back from now to retrieve logs", defaultValue = "1")
	private int days;

	@Option(names = { "--startTime" }, description = "Start time (ISO-8601 format, e.g., 2025-06-01T10:00:00Z)")
	private String startTime;

	@Option(names = { "--endTime" }, description = "End time (ISO-8601 format, e.g., 2025-06-06T10:00:00Z)")
	private String endTime;

	@Option(names = { "--includeAudit" }, description = "Include audit logs in download")
	private boolean includeAudit;

	private AtlasApiClient apiClient;

	@Override
	public Integer call() throws Exception {
	    this.apiClient = new AtlasApiClient(apiPublicKey, apiPrivateKey, debugLevel);

	    // If no project name or cluster name provided, show error
	    if (projectNames == null || clusterName == null) {
	        String errorMsg = "ERROR: Required parameters missing.";
	        if (projectNames == null && clusterName == null) {
	            errorMsg += " Both --projectNames and --clusterName must be provided.";
	        } else if (projectNames == null) {
	            errorMsg += " --projectNames must be provided.";
	        } else {
	            errorMsg += " --clusterName must be provided.";
	        }
	        System.err.println(errorMsg);
	        System.err.println();
	        System.err.println("Usage: java -jar atlas-logs-utility.jar --projectNames <projectName> --clusterName <clusterName> [options]");
	        System.err.println("Example: java -jar atlas-logs-utility.jar --projectNames myProject --clusterName myCluster --days 7");
	        logger.error(errorMsg);
	        return 1;
	    }

	    try {
	        for (String projectName : projectNames) {

	            // Determine time range (same as before)
	            Instant startTimeInstant;
	            Instant endTimeInstant;

	            if (startTime != null && endTime != null) {
	                startTimeInstant = Instant.parse(startTime);
	                endTimeInstant = Instant.parse(endTime);
	                logger.debug("Using explicit time range: {} to {}", startTimeInstant, endTimeInstant);
	                System.out.println("📅 Time range: " + startTimeInstant + " to " + endTimeInstant);
	            } else {
	                endTimeInstant = Instant.now();
	                startTimeInstant = endTimeInstant.minus(days, ChronoUnit.DAYS);
	                logger.debug("Using relative time range: last {} days ({} to {})", days, startTimeInstant,
	                        endTimeInstant);
	                System.out.println("📅 Time range: last " + days + " days");
	            }

	            // UPDATED: Use enum-based log type determination
	            List<String> targetLogTypeFileNames;
	            if (includeAudit) {
	                targetLogTypeFileNames = AtlasLogType.getAllLogTypeFileNames();
	                logger.debug("Including audit logs: {}", targetLogTypeFileNames);
	                System.out.println("📊 Log types: " + targetLogTypeFileNames + " (including audit)");
	            } else {
	                targetLogTypeFileNames = AtlasLogType.getDefaultLogTypeFileNames();
	                logger.debug("Using default log types (no audit): {}", targetLogTypeFileNames);
	                System.out.println("📊 Log types: " + targetLogTypeFileNames);
	            }

	            logger.debug("Downloading log types: {}", targetLogTypeFileNames);
	            logger.debug("Output directory: {}", outputDirectory);
	            System.out.println("📁 Output directory: " + outputDirectory);
	            System.out.println();

	            // Get project ID
	            String projectId = getProjectId(projectName);

	            // Download logs
	            List<Path> downloadedFiles = apiClient.logs().downloadCompressedLogFilesForCluster(projectId,
	                    clusterName, startTimeInstant, endTimeInstant, outputDirectory, targetLogTypeFileNames);

	            logger.debug("Successfully downloaded {} log files:", downloadedFiles.size());
	            // Note: File listing removed - summary is handled by AtlasLogsClient

	            if (downloadedFiles.isEmpty()) {
	                System.out.println("⚠️  No log files were downloaded. Check cluster name and time range.");
	                logger.warn("No log files were downloaded. Check cluster name and time range.");
	            }

	        }

	    } catch (IOException e) {
	        logger.error("Failed to download logs: {}", e.getMessage());
	        return 1;
	    } catch (Exception e) {
	        logger.error("Unexpected error during log download: {}", e.getMessage(), e);
	        return 2;
	    }
	    return 0;
	}

	private String getProjectId(String projectName) {
		Map<String, String> projects = apiClient.clusters().getProjects(Set.of(projectName));

		if (projects.isEmpty()) {
			throw new IllegalArgumentException("Project not found: " + projectName);
		}

		return projects.get(projectName);
	}

	public static void main(String[] args) {
		AtlasLogsUtility client = new AtlasLogsUtility();

		int exitCode = 0;
		try {
			CommandLine cmd = new CommandLine(client);
			ParseResult parseResult = cmd.parseArgs(args);

			File defaultsFile;
			if (client.configFile != null) {
				defaultsFile = client.configFile;
			} else {
				defaultsFile = new File("atlas-client.properties");
			}

			if (defaultsFile.exists()) {
				logger.debug("Loading configuration from {}", defaultsFile.getAbsolutePath());
				cmd.setDefaultValueProvider(new PropertiesDefaultProvider(defaultsFile));
			} else {
				logger.debug("Configuration file {} not found", defaultsFile.getAbsolutePath());
			}
			parseResult = cmd.parseArgs(args);

			if (!CommandLine.printHelpIfRequested(parseResult)) {
				logger.debug("Starting MongoDB Atlas client");
				exitCode = cmd.execute(args);
				logger.debug("MongoDB Atlas client completed with exit code {}", exitCode);
			}
		} catch (ParameterException ex) {
			logger.error("Parameter error: {}", ex.getMessage());
			ex.getCommandLine().usage(System.err);
			exitCode = 1;
		} catch (Exception e) {
			logger.error("Unexpected error: {}", e.getMessage(), e);
			exitCode = 2;
		}

		System.exit(exitCode);
	}
}