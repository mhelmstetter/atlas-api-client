# MongoDB Atlas Autoscaler

A daemon process that monitors MongoDB Atlas cluster metrics and automatically scales clusters based on configurable rules and thresholds.

## Features

- **Daemon Process**: Runs continuously monitoring your Atlas clusters
- **Configurable Rules**: Set up custom scaling rules based on CPU, memory, and other metrics
- **Multiple Projects**: Monitor clusters across multiple Atlas projects
- **Cooldown Periods**: Prevent rapid scaling with configurable cooldown periods
- **Dry Run Mode**: Test your configuration without actually scaling clusters
- **Comprehensive Logging**: Detailed logging of all scaling decisions and actions

## Quick Start

### 1. Configuration

Copy the sample configuration file:
```bash
cp autoscaler.properties.example autoscaler.properties
```

Edit `autoscaler.properties` with your settings:
```properties
# Atlas API Credentials
apiPublicKey=your_atlas_public_key
apiPrivateKey=your_atlas_private_key

# Projects to monitor
includeProjectNames=Production,Staging

# Basic scaling configuration
enableCpuScaleUp=true
cpuScaleUpThreshold=90.0
cpuScaleUpDuration=5
```

### 2. Build the Project

```bash
mvn clean package
```

### 3. Run the Autoscaler

```bash
# Run in dry-run mode for testing
./run-autoscaler.sh --dry-run

# Run in production mode
./run-autoscaler.sh
```

## Configuration Reference

### Required Settings

- `apiPublicKey`: Your Atlas API public key
- `apiPrivateKey`: Your Atlas API private key  
- `includeProjectNames`: Comma-separated list of Atlas project names to monitor

### CPU Scaling Rules

| Setting | Description | Default |
|---------|-------------|---------|
| `enableCpuScaleUp` | Enable CPU-based scale up | `true` |
| `cpuScaleUpThreshold` | CPU percentage that triggers scale up (0-100) | `90.0` |
| `cpuScaleUpDuration` | Minutes CPU must exceed threshold | `5` |
| `enableCpuScaleDown` | Enable CPU-based scale down | `false` |
| `cpuScaleDownThreshold` | CPU percentage that triggers scale down | `30.0` |
| `cpuScaleDownDuration` | Minutes CPU must be below threshold | `30` |

### Memory Scaling Rules

| Setting | Description | Default |
|---------|-------------|---------|
| `enableMemoryScaleUp` | Enable memory-based scale up | `false` |
| `memoryScaleUpThreshold` | Memory percentage that triggers scale up | `85.0` |
| `memoryScaleUpDuration` | Minutes memory must exceed threshold | `10` |

### Global Settings

| Setting | Description | Default |
|---------|-------------|---------|
| `monitoringInterval` | Monitoring interval in seconds | `300` |
| `scaleCooldown` | Cooldown period between scaling actions (minutes) | `30` |
| `dryRun` | Enable dry-run mode (no actual scaling) | `false` |

## How It Works

### 1. Monitoring Loop

The autoscaler runs a monitoring loop every `monitoringInterval` seconds:

1. **Fetch Projects**: Gets all specified Atlas projects
2. **Get Clusters**: Retrieves all clusters in each project
3. **Collect Metrics**: Gathers recent metrics data for each cluster
4. **Evaluate Rules**: Checks if any scaling rules are triggered
5. **Execute Scaling**: Scales clusters when rules are met (respecting cooldowns)

### 2. Scaling Rules

Each scaling rule consists of:

- **Metric**: What to monitor (e.g., `SYSTEM_NORMALIZED_CPU_USER`)
- **Condition**: How to evaluate the metric (greater than, less than, etc.)
- **Threshold**: The value that triggers the rule
- **Duration**: How long the condition must persist
- **Direction**: Whether to scale up or down
- **Cooldown**: Minimum time between scaling actions

### 3. Example Rule Evaluation

For a CPU scale-up rule with:
- Threshold: 90%
- Duration: 5 minutes
- Cooldown: 30 minutes

The autoscaler will:
1. Check if any host in the cluster has CPU > 90%
2. Verify this condition has persisted for 5+ minutes
3. Ensure 30+ minutes have passed since the last scaling action
4. Scale the cluster to the next tier (e.g., M40 → M50)

## Supported Instance Tiers

The autoscaler supports scaling between these Atlas instance sizes:

- M0, M2, M5 (Shared clusters)
- M10, M20, M30, M40, M50, M60, M80 (Dedicated clusters)
- M140, M200, M300, M400, M700 (High-memory clusters)

Scaling always moves one tier at a time (e.g., M30 → M40 → M50).

## Command Line Options

```bash
Usage: java -jar atlas-autoscaler.jar [OPTIONS]

Options:
  --apiPublicKey=KEY           Atlas API public key
  --apiPrivateKey=KEY          Atlas API private key
  --includeProjectNames=LIST   Project names to monitor (comma-separated)
  --config=FILE               Configuration file (default: autoscaler.properties)
  --monitoringInterval=SEC     Monitoring interval in seconds (default: 300)
  --dryRun                     Enable dry-run mode
  --enableCpuScaleUp          Enable CPU-based scale up (default: true)
  --cpuScaleUpThreshold=PCT   CPU threshold for scale up (default: 90.0)
  --cpuScaleUpDuration=MIN    Duration for CPU scale up (default: 5)
  --scaleCooldown=MIN         Cooldown between scaling actions (default: 30)
  -h, --help                  Show help message
```

## Logging

The autoscaler provides detailed logging at different levels:

- **INFO**: Normal operations, scaling actions, configuration
- **DEBUG**: Detailed monitoring data, rule evaluations
- **WARN**: Potential issues, dry-run actions
- **ERROR**: Failures, misconfigurations

Configure logging level with:
```bash
java -Dlogging.level.com.mongodb.atlas=DEBUG -jar atlas-autoscaler.jar
```

## Best Practices

### 1. Start with Dry Run

Always test your configuration in dry-run mode first:
```bash
./run-autoscaler.sh --dry-run
```

### 2. Conservative Thresholds

Start with conservative settings and adjust based on your workload:
- CPU threshold: 85-90%
- Duration: 5-10 minutes
- Cooldown: 30-60 minutes

### 3. Monitor Scaling Actions

Watch the logs to understand scaling patterns:
```bash
tail -f autoscaler.log | grep "Scaling"
```

### 4. Scale Down Carefully

Scale-down rules should be:
- More conservative (lower thresholds, longer durations)
- Tested thoroughly in non-production environments
- Disabled initially until you understand scaling patterns

### 5. Cost Monitoring

Monitor your Atlas billing after enabling autoscaling to understand cost impacts.

## Troubleshooting

### Common Issues

1. **No Scaling Actions**: Check that rules are enabled and thresholds are appropriate
2. **Frequent Scaling**: Increase cooldown periods or adjust thresholds
3. **API Errors**: Verify API credentials and project names
4. **High CPU Usage**: The autoscaler itself is lightweight but check monitoring interval

### Debug Steps

1. Enable debug logging: `--log-level DEBUG`
2. Run in dry-run mode to see what would happen
3. Check Atlas API connectivity: verify credentials and project access
4. Review metrics data: ensure the autoscaler is collecting metrics properly

### Getting Help

- Check the logs for detailed error messages
- Verify Atlas API credentials have proper permissions
- Ensure project names match exactly (case-sensitive)
- Test with a single project first before monitoring multiple projects

## Security Considerations

- Store API credentials securely (use environment variables or secure config files)
- Use Atlas API keys with minimal required permissions
- Monitor autoscaler logs for any unauthorized scaling attempts
- Consider network restrictions for where the autoscaler runs

## Limitations

- **Manual PATCH Implementation**: Currently simulates scaling calls (needs Atlas API PATCH support)
- **Tier Progression**: Uses hardcoded tier progression (should use Atlas API)
- **Cost Estimation**: No built-in cost impact analysis
- **Metric Types**: Currently focuses on CPU; memory scaling needs additional work

## Contributing

To extend the autoscaler:

1. Add new metrics in `collectMetricForCluster()`
2. Implement new rule types in `ScalingRule` class
3. Add cost estimation features in `AtlasScalingClient`
4. Improve error handling and resilience

## License

This project is provided as example code for MongoDB Atlas automation.