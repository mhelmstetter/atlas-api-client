package com.mongodb.atlas.api.logs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.atlas.api.clients.AtlasApiClient;
import com.mongodb.atlas.api.clients.AtlasLogsClient;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.PropertiesDefaultProvider;

@Command(name = "MongoDBAtlasClient", mixinStandardHelpOptions = true, description = "MongoDB Atlas Logs Utility - Download compressed log files for a cluster", defaultValueProvider = PropertiesDefaultProvider.class)
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

	@Option(names = { "--output-dir", "-o" }, description = "Output directory for log files", defaultValue = "./logs")
	private String outputDirectory;

	@Option(names = { "--days",
			"-d" }, description = "Number of days back from now to retrieve logs", defaultValue = "1")
	private int days;

	@Option(names = { "--start-time" }, description = "Start time (ISO-8601 format, e.g., 2025-06-01T10:00:00Z)")
	private String startTime;

	@Option(names = { "--end-time" }, description = "End time (ISO-8601 format, e.g., 2025-06-06T10:00:00Z)")
	private String endTime;

	@Option(names = { "--include-audit" }, description = "Include audit logs in download")
	private boolean includeAudit;

	private AtlasApiClient apiClient;

	@Override
	public Integer call() throws Exception {
		this.apiClient = new AtlasApiClient(apiPublicKey, apiPrivateKey, debugLevel);

		// If no project name or cluster name provided, just show info
		if (projectNames == null || clusterName == null) {
			logger.info("Atlas API client initialized. Provide project name and cluster name to download logs.");
			logger.info("Usage: java -jar atlas-logs-utility.jar <projectName> <clusterName> [options]");
			return 0;
		}

		try {
			for (String projectName : projectNames) {

				// Determine time range
				Instant startTimeInstant;
				Instant endTimeInstant;

				if (startTime != null && endTime != null) {
					startTimeInstant = Instant.parse(startTime);
					endTimeInstant = Instant.parse(endTime);
					logger.info("Using explicit time range: {} to {}", startTimeInstant, endTimeInstant);
				} else {
					endTimeInstant = Instant.now();
					startTimeInstant = endTimeInstant.minus(days, ChronoUnit.DAYS);
					logger.info("Using relative time range: last {} days ({} to {})", days, startTimeInstant,
							endTimeInstant);
				}

				// Determine log types to download
				List<String> targetLogTypes;
				if (includeAudit) {
					targetLogTypes = Arrays.asList(AtlasLogsClient.LOG_TYPE_MONGODB,
							AtlasLogsClient.LOG_TYPE_MONGOS, AtlasLogsClient.LOG_TYPE_MONGODB_AUDIT,
							AtlasLogsClient.LOG_TYPE_MONGOS_AUDIT);
				} else {
					targetLogTypes = AtlasLogsClient.DEFAULT_LOG_TYPES;
				}

				logger.info("Downloading log types: {}", targetLogTypes);
				logger.info("Output directory: {}", outputDirectory);

				// Get project ID
				String projectId = getProjectId(projectName);

				// Download logs
				List<Path> downloadedFiles = apiClient.logs().downloadCompressedLogFilesForCluster(projectId,
						clusterName, startTimeInstant, endTimeInstant, outputDirectory, targetLogTypes);

				logger.info("Successfully downloaded {} log files:", downloadedFiles.size());
				for (Path file : downloadedFiles) {
					logger.info("  - {}", file.toString());
				}

				if (downloadedFiles.isEmpty()) {
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
				logger.info("Loading configuration from {}", defaultsFile.getAbsolutePath());
				cmd.setDefaultValueProvider(new PropertiesDefaultProvider(defaultsFile));
			} else {
				logger.warn("Configuration file {} not found", defaultsFile.getAbsolutePath());
			}
			parseResult = cmd.parseArgs(args);

			if (!CommandLine.printHelpIfRequested(parseResult)) {
				logger.info("Starting MongoDB Atlas client");
				exitCode = cmd.execute(args);
				logger.info("MongoDB Atlas client completed with exit code {}", exitCode);
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