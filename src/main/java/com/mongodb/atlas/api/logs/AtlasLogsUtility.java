package com.mongodb.atlas.api.logs;

import java.io.File;
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

@Command(name = "MongoDBAtlasClient", mixinStandardHelpOptions = true, description = "MongoDB Atlas Logs Utility", defaultValueProvider = PropertiesDefaultProvider.class)
public class AtlasLogsUtility implements Callable<Integer> {

	private static final Logger logger = LoggerFactory.getLogger(AtlasLogsUtility.class);

	@Option(names = { "--apiPublicKey" }, description = "Atlas API public key", required = false)
	private String apiPublicKey;

	@Option(names = { "--apiPrivateKey" }, description = "Atlas API private key", required = false)
	private String apiPrivateKey;

	@Option(names = {"--includeProjectNames" }, description = "project names to be processed", required = false, split = ",")
	private Set<String> includeProjectNames;

	@Option(names = { "--config", "-c" }, description = "config file", required = false, defaultValue = "atlas-client.properties")
	private File configFile;

	private AtlasApiClient apiClient;
	
    @Override
    public Integer call() throws Exception {
    	this.apiClient = new AtlasApiClient(apiPublicKey, apiPrivateKey, 0);
    	return 0;
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
                exitCode = client.call();
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
