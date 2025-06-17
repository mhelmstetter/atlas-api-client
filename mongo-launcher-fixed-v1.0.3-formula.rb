class MongoLauncher < Formula
  desc "MongoDB Cluster Management Tool with Interactive CLI"
  homepage "https://github.com/mhelmstetter/mongo-launcher"
  url "https://github.com/mhelmstetter/mongo-launcher/releases/download/v1.0.3/mongo-launcher-1.0.0-universal.tar.gz"
  sha256 "bf7006eb1ea99bc921a78c80a7cd9a170d39480099021df6ff46ca48af371052"
  license "Apache-2.0"
  version "1.0.3"
  
  depends_on "openjdk@17"

  def install
    # Install JAR file - it's extracted to mongo-launcher-1.0.0-universal/ directory
    libexec.install "mongo-launcher.jar"
    
    # Create wrapper script
    (bin/"mongo-launcher").write <<~EOS
      #!/bin/bash
      exec "#{Formula["openjdk@17"].opt_bin}/java" -jar "#{libexec}/mongo-launcher.jar" "$@"
    EOS
    
    # Make wrapper executable
    chmod 0755, bin/"mongo-launcher"
  end

  test do
    # Test that the application runs and shows version
    assert_match "1.0", shell_output("#{bin}/mongo-launcher --version")
  end

  def caveats
    <<~EOS
      MongoLauncher has been installed!
      
      Configuration directory: ~/.mongo-launcher
      
      Getting started:
        mongo-launcher config show      # Show current configuration
        mongo-launcher config set defaultAtlasProjectId "your-project-id"
        mongo-launcher launch --help    # Get help for launching clusters
      
      For interactive mode (default):
        mongo-launcher launch
      
      For non-interactive mode:
        mongo-launcher launch --type local --name my-cluster --non-interactive
    EOS
  end
end