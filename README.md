# Atlas Metrics Analyzer

A comprehensive MongoDB Atlas API client for metrics collection, analysis, and reporting. This tool helps you understand your Atlas usage patterns, generate visual reports, and export data for further analysis.

## Key Capabilities

**🔗 Overcome Atlas Data Retention Limits**: Store collected metrics in MongoDB to analyze data beyond Atlas retention periods. For example, collect 1-minute granularity metrics and analyze them for weeks or months, even though Atlas only retains 1-minute data for 48 hours.

**📊 Multi-granularity Analysis**: Collect high-resolution data when available (10-second for M40+ clusters) and store it for long-term analysis at any granularity level.

## Features

- **📊 Metrics Collection**: Collect detailed metrics from Atlas clusters across multiple projects
- **🗄️ MongoDB Storage**: Store metrics data in MongoDB for analysis beyond Atlas retention limits
- **📈 Visual Reporting**: Generate charts and visualizations from collected metrics data
- **📄 CSV Export**: Export metrics data to CSV format for analysis in external tools
- **🌙 Dark Mode Charts**: Support for dark mode chart generation
- **🔧 Flexible Configuration**: Configurable via command line or properties files

## Operating Modes

### 🔄 **Live Collection Mode** (Default)
Connects to Atlas API, collects fresh metrics data, and optionally processes/reports on it.

### 💾 **Collection-Only Mode** 
Collects and stores metrics data without processing - ideal for automated scheduled data gathering.

### 📊 **Analysis Mode**
Processes previously collected data without fetching new data from Atlas - useful for historical analysis.

### 🔍 **Report Generation**
Creates charts, CSV exports, and HTML dashboards from stored data.


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

# Metrics to collect (defaults: SYSTEM_NORMALIZED_CPU_USER,SYSTEM_MEMORY_USED,SYSTEM_MEMORY_FREE,DISK_PARTITION_IOPS_TOTAL)
metrics=CONNECTIONS,OPCOUNTER_QUERY,OPCOUNTER_INSERT,SYSTEM_NORMALIZED_CPU_USER

# MongoDB Storage (optional - enables long-term retention)
mongodbUri=mongodb://localhost:27017
mongodbDatabase=atlas_metrics
mongodbCollection=metrics

# Output settings
exportCsv=true
generateCharts=true
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

## Atlas CLI - Comprehensive Command-Line Interface

This project also includes a comprehensive CLI for all Atlas operations, including alerts management:

### CLI Configuration

The CLI supports loading defaults from a properties file. By default, it looks for `atlas-client.properties` in the current directory.

```properties
# atlas-client.properties
apiPublicKey=your_atlas_public_key
apiPrivateKey=your_atlas_private_key
projectIds=proj1,proj2,proj3
includeProjectNames=Production,Staging
orgId=your_org_id
format=JSON
```

You can specify a different config file with `--config`:
```bash
./atlas-cli --config my-config.properties alerts list
```

### CLI Usage

```bash
# Make the CLI executable (first time only)
chmod +x atlas-cli

# Show all available commands
./atlas-cli --help

# Interactive mode
./atlas-cli --interactive

# Set credentials globally (applies to all commands)
./atlas-cli --apiPublicKey=your_key --apiPrivateKey=your_secret

# Multiple projects support
./atlas-cli --projectIds=proj1,proj2,proj3 alerts list
./atlas-cli --includeProjectNames="Production,Staging" clusters list

# List alerts for a project
./atlas-cli alerts list <project-id>

# Get specific alert details
./atlas-cli alerts get <project-id> <alert-id>

# Acknowledge an alert
./atlas-cli alerts acknowledge <project-id> <alert-id> --permanent --comment "Maintenance window"

# List alert configurations
./atlas-cli alert-configs list <project-id>

# Create a metric-based alert
./atlas-cli alert-configs create-metric <project-id> \
  --metric CONNECTIONS \
  --operator GREATER_THAN \
  --threshold 100 \
  --email admin@company.com

# Cluster management
./atlas-cli clusters list <project-id>
./atlas-cli clusters create my-cluster --size M10 --region US_EAST_1 --provider AWS

# API key management
./atlas-cli api-keys list <org-id>
./atlas-cli api-keys create "Monitoring Key" --roles ORG_READ_ONLY
```

### CLI Features

- **🚨 Alerts Management**: Full CRUD operations for alerts and alert configurations
- **🏗️ Cluster Management**: Create, update, delete, and monitor clusters
- **🔑 API Key Management**: Programmatic API key lifecycle management
- **👥 Database Users**: User account management across projects
- **🌐 Network Access**: IP allowlist and VPC peering management
- **💾 Backups**: Backup policy and restore operations
- **📊 Monitoring**: Metrics collection and analysis
- **📁 Projects**: Project management and configuration
- **🔧 Multi-Project Support**: Work with multiple projects using `--projectIds` or `--includeProjectNames`
- **⚙️ Consistent Configuration**: CamelCase parameters matching AtlasMetricsAnalyzer style

See [ATLAS-CLI-GUIDE.md](ATLAS-CLI-GUIDE.md) for comprehensive CLI documentation.

## Configuration Reference

### Required Configuration

| Option | Description | Example |
|--------|-------------|---------|
| `apiPublicKey` | Atlas API public key | `abcd1234` |
| `apiPrivateKey` | Atlas API private key | `12345678-1234-1234-1234-123456789012` |
| `includeProjectNames` | Comma-separated project names to analyze | `Production,Staging` |

### MongoDB Storage Configuration

| Option | Description | Default | Example |
|--------|-------------|---------|---------|
| `mongodbUri` | MongoDB connection string for storing metrics | None | `mongodb://localhost:27017` |
| `mongodbDatabase` | Database name for metrics storage | `atlas_metrics` | `my_metrics_db` |
| `mongodbCollection` | Collection name for metrics data | `metrics` | `cluster_metrics` |

### Metrics Collection

| Option | Description | Default |
|--------|-------------|---------|
| `metrics` | Comma-separated list of metrics to collect | `SYSTEM_NORMALIZED_CPU_USER,`<br>`SYSTEM_MEMORY_USED,`<br>`SYSTEM_MEMORY_FREE,`<br>`DISK_PARTITION_IOPS_TOTAL` |
| `period` | Time period for metrics collection (ISO 8601) | `PT48H` (48 hours) |
| `granularity` | Metrics granularity (ISO 8601) | `PT1M` (1 minute) |
| `collect` | Only collect and store metrics without processing or reporting | `false` |

**Common metrics examples:**
- System: `SYSTEM_NORMALIZED_CPU_USER`, `SYSTEM_MEMORY_USED`
- Database: `CONNECTIONS`, `OPCOUNTER_QUERY`, `OPCOUNTER_INSERT`
- Network: `NETWORK_BYTES_IN`, `NETWORK_BYTES_OUT`

### Output and Reporting

| Option | Description | Default | Example |
|--------|-------------|---------|---------|
| `exportCsv` | Export metrics to CSV format | `false` | `true` |
| `detailedMetricsCsv` | Export detailed metrics to CSV | `false` | `true` |
| `generateCharts` | Generate visual charts (includes HTML index) | `false` | `true` |
| `chartOutputDir` | Directory for chart output | `.` | `./reports` |
| `darkMode` | Generate charts in dark mode | `false` | `true` |
| `chartWidth` | Chart width in pixels | `300` | `800` |
| `chartHeight` | Chart height in pixels | `150` | `400` |


## Atlas Monitoring Modes & Data Retention

MongoDB Atlas provides different monitoring granularities based on your cluster sizes and retains data for different periods:

#### Premium Monitoring
- **Automatically enabled** when you have at least one **M40 or larger** cluster in your project
- **Applies to all clusters** in the project (even smaller ones)
- Provides **10-second granularity** metrics
- Remains enabled until you downgrade or terminate your last M40+ cluster

#### Standard Monitoring
- Available for clusters **smaller than M40**
- Provides metrics at **1-minute minimum granularity**

#### Data Retention Periods

| **Granularity** | **Retention Period** | **Availability** |
|-----------------|---------------------|------------------|
| `PT10S` (10 seconds) | 8 hours | Premium monitoring only (M40+) |
| `PT1M` (1 minute) | 48 hours | All clusters |
| `PT5M` (5 minutes) | 48 hours | All clusters |
| `PT1H` (1 hour) | 63 days | All clusters |
| `P1D` (1 day) | Forever | All clusters |

> **⚠️ Important**: 10-second granularity (`PT10S`) is only available with premium monitoring (M40+ clusters) and data is retained for only 8 hours.

#### Choosing Period and Granularity

**For Recent Analysis (last 8 hours):**
```properties
# Premium monitoring clusters only
period=PT8H
granularity=PT10S
```

**For Short-term Analysis (last 2 days):**
```properties
# All clusters
period=PT48H
granularity=PT1M
```

**For Long-term Analysis (last 60 days):**
```properties
# All clusters - use hourly data for better performance
period=PT1440H  # 60 days
granularity=PT1H
```

**For Historical Analysis:**
```properties
# All clusters - daily aggregates available indefinitely
period=PT8760H  # 1 year
granularity=P1D
```


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
  --darkMode=true \
  --chartWidth=800 \
  --chartHeight=400
```

### 3. Generate Charts

Create visual charts from metrics data:

```bash
java -jar bin/AtlasClient.jar \
  --config=atlas-client.properties \
  --generateCharts=true
```

### 4. Data Collection Only

Just collect and store data for later analysis:

```bash
java -jar bin/AtlasClient.jar \
  --config=atlas-client.properties \
  --collect=true
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
  --collect                   Only collect and store, don't process
  --generateCharts            Generate visual charts

Output Options:
  --exportCsv                 Export to CSV
  --detailedMetricsCsv        Export detailed CSV
  --generateCharts            Generate chart visualizations with HTML index
  --chartOutputDir=DIR        Chart output directory
  --darkMode                  Dark mode charts
  --chartWidth=PIXELS         Chart width
  --chartHeight=PIXELS        Chart height

General Options:
  --config=FILE               Configuration file
  --debug                     Enable debug logging for troubleshooting
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
  --collect=true

# Weekly reporting script  
#!/bin/bash
java -jar atlas-metrics-analyzer.jar \
  --config=weekly-report.properties \
  --generateCharts=true
```

### 4. Choose Appropriate Granularity
Match granularity to your analysis needs and data retention:
```properties
# For real-time monitoring (M40+ clusters only)
period=PT8H
granularity=PT10S

# For recent troubleshooting (all clusters)
period=PT24H
granularity=PT1M

# For trend analysis (all clusters)
period=PT720H  # 30 days
granularity=PT1H

# For long-term reporting (all clusters)
period=PT8760H  # 1 year
granularity=P1D
```

### 5. Monitor Data Availability
Data availability reports are automatically generated when MongoDB storage is enabled. Check the generated `data-availability-report.csv` file for completeness information.

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

3. **Granularity and Data Retention Issues**
   - **"No data for PT10S"**: 10-second data requires M40+ clusters (premium monitoring)
   - **"Data too old"**: Check retention limits - 10s data kept only 8 hours, 1m data kept 48 hours
   - **"Invalid granularity"**: Use `PT10S`, `PT1M`, `PT5M`, `PT1H`, or `P1D`
   - **Poor performance**: Use coarser granularity (`PT1H` or `P1D`) for longer periods

4. **Premium Monitoring Issues**
   - **Expected 10s data but getting 1m**: Verify you have at least one M40+ cluster in the project
   - **Inconsistent granularity**: Premium monitoring applies to entire project, not individual clusters
   - **Missing recent data**: Check if M40+ cluster was recently created/terminated

5. **Chart Generation Issues**
   - Ensure output directory is writable
   - Check available disk space
   - Verify chart dimensions are reasonable

6. **Memory Issues**
   - For large datasets, increase JVM memory: `java -Xmx4g -jar ...`
   - Consider using smaller time periods or fewer metrics

### Debug Mode

Enable debug logging for troubleshooting:
```bash
java -jar bin/AtlasClient.jar --debug [other options]
```

This provides detailed information about:
- API requests and responses
- Metrics collection per process
- Data point counts per metric
- Storage operations

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