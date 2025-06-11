# Atlas Metrics Analyzer

A comprehensive MongoDB Atlas API client for metrics collection, analysis, and reporting. This tool helps you understand your Atlas usage patterns, generate visual reports, and export data for further analysis.

## Features

- **📊 Metrics Collection**: Collect detailed metrics from Atlas clusters across multiple projects
- **📈 Visual Reporting**: Generate charts and visualizations from collected metrics data
- **📄 CSV Export**: Export metrics data to CSV format for analysis in external tools
- **🗂️ Pattern Analysis**: Analyze usage patterns and trends over time
- **🌙 Dark Mode Charts**: Support for dark mode chart generation
- **🗄️ Data Storage**: Store metrics data locally for historical analysis
- **🔧 Flexible Configuration**: Configurable via command line or properties files

## Quick Start

### Prerequisites

- Java 17 or later
- MongoDB Atlas API credentials (public/private key pair)
- Access to Atlas projects you want to analyze

### Installation

#### Option 1: Download Release JAR

Download the latest `AtlasClient.jar` from the releases section.

#### Option 2: Build from Source

```bash
git clone <repository-url>
cd atlas-api-client
mvn clean package
```

The executable JAR will be created in the `bin/` directory.

### Basic Usage

#### 1. Create Configuration File

Create an `atlas-client.properties` file:

```properties
# Atlas API Credentials
apiPublicKey=your_atlas_public_key
apiPrivateKey=your_atlas_private_key

# Projects to analyze
includeProjectNames=Production,Staging,Development

# Metrics to collect
metrics=CONNECTIONS,OPCOUNTER_QUERY,OPCOUNTER_INSERT,SYSTEM_NORMALIZED_CPU_USER

# Output settings
exportCsv=true
generateCharts=true
generateHtmlIndex=true
```

#### 2. Run the Analyzer

```bash
# Using configuration file
java -jar bin/AtlasClient.jar --config atlas-client.properties

# Or with command line parameters
java -jar bin/AtlasClient.jar \
  --apiPublicKey=your_public_key \
  --apiPrivateKey=your_private_key \
  --includeProjectNames=MyProject \
  --metrics=CONNECTIONS,OPCOUNTER_QUERY \
  --exportCsv=true
```

## Configuration Options

### Atlas API Configuration

| Option | Description | Required |
|--------|-------------|----------|
| `apiPublicKey` | Atlas API public key | ✅ |
| `apiPrivateKey` | Atlas API private key | ✅ |
| `includeProjectNames` | Comma-separated list of project names to analyze | ✅ |

### Metrics Collection

| Option | Description | Default |
|--------|-------------|---------|
| `metrics` | Comma-separated list of metrics to collect | `CONNECTIONS,OPCOUNTER_QUERY,OPCOUNTER_INSERT` |
| `period` | Time period for metrics collection | `PT1H` (1 hour) |
| `granularity` | Metrics granularity | `PT1M` (1 minute) |
| `collectOnly` | Only collect metrics, don't process | `false` |

### Output and Reporting

| Option | Description | Default |
|--------|-------------|---------|
| `exportCsv` | Export metrics to CSV format | `false` |
| `generateCharts` | Generate visual charts | `false` |
| `generateHtmlIndex` | Create HTML index of all charts | `false` |
| `chartOutputDir` | Directory for chart output | `.` (current directory) |
| `darkMode` | Generate charts in dark mode | `false` |
| `chartWidth` | Chart width in pixels | `300` |
| `chartHeight` | Chart height in pixels | `150` |

### Advanced Options

| Option | Description | Default |
|--------|-------------|---------|
| `analyzePatterns` | Perform pattern analysis on collected data | `false` |
| `dataAvailabilityReport` | Generate data availability report | `false` |
| `dataLocation` | Directory for storing collected data | `data` |
| `detailedMetricsCsv` | Export detailed metrics to CSV | `false` |

## Available Metrics

The tool supports collecting various Atlas metrics:

### System Metrics
- `SYSTEM_NORMALIZED_CPU_USER` - CPU usage
- `SYSTEM_MEMORY_USED` - Memory usage
- `DISK_PARTITION_IOPS_TOTAL` - Disk IOPS

### Database Metrics
- `CONNECTIONS` - Active connections
- `OPCOUNTER_QUERY` - Query operations per second
- `OPCOUNTER_INSERT` - Insert operations per second
- `OPCOUNTER_UPDATE` - Update operations per second
- `OPCOUNTER_DELETE` - Delete operations per second

### Network Metrics
- `NETWORK_BYTES_IN` - Network bytes in
- `NETWORK_BYTES_OUT` - Network bytes out

And many more - see Atlas documentation for complete list.

## Usage Examples

### 1. Basic Metrics Collection

Collect basic metrics and export to CSV:

```bash
java -jar bin/AtlasClient.jar \
  --apiPublicKey=abc123 \
  --apiPrivateKey=def456 \
  --includeProjectNames=Production \
  --metrics=CONNECTIONS,OPCOUNTER_QUERY \
  --exportCsv=true
```

### 2. Generate Visual Reports

Create charts and HTML dashboard:

```bash
java -jar bin/AtlasClient.jar \
  --config=atlas-client.properties \
  --generateCharts=true \
  --generateHtmlIndex=true \
  --darkMode=true \
  --chartWidth=800 \
  --chartHeight=400
```

### 3. Historical Analysis

Analyze patterns in stored data:

```bash
java -jar bin/AtlasClient.jar \
  --config=atlas-client.properties \
  --analyzePatterns=true \
  --dataAvailabilityReport=true
```

### 4. Data Collection Only

Just collect and store data for later analysis:

```bash
java -jar bin/AtlasClient.jar \
  --config=atlas-client.properties \
  --collectOnly=true
```

## Output Files

The tool generates several types of output:

### CSV Files
- `atlas-metrics-summary.csv` - Summary metrics for all projects
- `detailed-metrics-{timestamp}.csv` - Detailed metrics data
- `data-availability-report.csv` - Data availability analysis

### Charts and Visualizations
- Individual metric charts (SVG format)
- Combined project charts
- `index.html` - HTML dashboard with all charts

### Data Storage
- `data/` directory - Raw metrics data stored locally
- Organized by project and date for historical analysis

## Command Line Reference

```bash
Usage: AtlasMetricsAnalyzer [OPTIONS]

Atlas API Options:
  --apiPublicKey=KEY          Atlas API public key
  --apiPrivateKey=KEY         Atlas API private key
  --includeProjectNames=LIST  Project names (comma-separated)

Metrics Options:
  --metrics=LIST              Metrics to collect (comma-separated)
  --period=DURATION           Time period (ISO 8601 duration)
  --granularity=DURATION      Metrics granularity (ISO 8601 duration)

Processing Options:
  --collectOnly               Only collect, don't process
  --analyzePatterns           Analyze usage patterns
  --dataAvailabilityReport    Generate availability report

Output Options:
  --exportCsv                 Export to CSV
  --detailedMetricsCsv        Export detailed CSV
  --generateCharts            Generate chart visualizations
  --generateHtmlIndex         Create HTML index
  --chartOutputDir=DIR        Chart output directory
  --darkMode                  Dark mode charts
  --chartWidth=PIXELS         Chart width
  --chartHeight=PIXELS        Chart height

General Options:
  --config=FILE               Configuration file
  --dataLocation=DIR          Data storage directory
  -h, --help                  Show help
  -V, --version               Show version
```

## Best Practices

### 1. Start Small
Begin with a single project and basic metrics:
```bash
java -jar bin/AtlasClient.jar \
  --apiPublicKey=your_key \
  --apiPrivateKey=your_private_key \
  --includeProjectNames=TestProject \
  --metrics=CONNECTIONS \
  --exportCsv=true
```

### 2. Use Configuration Files
For regular analysis, create a configuration file:
```properties
apiPublicKey=your_key
apiPrivateKey=your_private_key
includeProjectNames=Prod,Staging
metrics=CONNECTIONS,OPCOUNTER_QUERY,SYSTEM_NORMALIZED_CPU_USER
exportCsv=true
generateCharts=true
period=PT24H
granularity=PT5M
```

### 3. Automate Collection
Set up regular metrics collection:
```bash
# Daily collection script
#!/bin/bash
java -jar atlas-metrics-analyzer.jar \
  --config=daily-collection.properties \
  --collectOnly=true

# Weekly reporting script  
#!/bin/bash
java -jar atlas-metrics-analyzer.jar \
  --config=weekly-report.properties \
  --generateCharts=true \
  --generateHtmlIndex=true \
  --analyzePatterns=true
```

### 4. Monitor Data Availability
Regularly check data completeness:
```bash
java -jar bin/AtlasClient.jar \
  --config=atlas-client.properties \
  --dataAvailabilityReport=true
```

## Troubleshooting

### Common Issues

1. **Authentication Errors**
   - Verify API keys are correct
   - Check API key permissions in Atlas
   - Ensure project names match exactly (case-sensitive)

2. **No Data Returned**
   - Check time period - ensure it's not too far in the past
   - Verify metrics names are correct
   - Check cluster state (metrics only available for active clusters)

3. **Chart Generation Issues**
   - Ensure output directory is writable
   - Check available disk space
   - Verify chart dimensions are reasonable

4. **Memory Issues**
   - For large datasets, increase JVM memory: `java -Xmx4g -jar ...`
   - Consider using smaller time periods or fewer metrics

### Debug Mode

Enable debug logging:
```bash
java -Dlogging.level.com.mongodb.atlas=DEBUG -jar bin/AtlasClient.jar [options]
```

### Getting Help

- Check log output for detailed error messages
- Verify Atlas API connectivity with simple operations first  
- Test with a single project before processing multiple projects
- Ensure all required dependencies are available

## Dependencies

The tool requires these components to be available:

- MongoDB Atlas API access
- Internet connectivity to Atlas endpoints
- Local storage for data and output files
- Java graphics libraries (for chart generation)

## Security Considerations

- Store API credentials securely
- Use environment variables for sensitive data
- Restrict file permissions on configuration files
- Consider network security when running in production environments

## License

This project is provided as example code for MongoDB Atlas automation and analysis.

## Contributing

To extend the tool's functionality:

1. **Add New Metrics**: Extend the metrics collection in `MetricsCollector`
2. **Custom Reports**: Add new report types in the reporting package
3. **Export Formats**: Add new export formats beyond CSV
4. **Visualization Types**: Extend chart generation capabilities

## Related Tools

- [MongoDB Atlas Autoscaler](../atlas-autoscaler/) - Automated cluster scaling based on metrics
- [Atlas CLI](https://www.mongodb.com/docs/atlas/cli/) - Official Atlas command line interface
- [MongoDB Monitoring](https://docs.mongodb.com/ops-manager/) - Enterprise monitoring solution