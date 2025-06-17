class MongoLauncher < Formula
  desc "MongoDB Cluster Management Tool with Interactive CLI"
  homepage "https://github.com/mhelmstetter/mongo-launcher"
  url "https://github.com/mhelmstetter/mongo-launcher/archive/refs/tags/v1.0.0.tar.gz"
  sha256 "0e090743b8293ce902e35fc2df722d2144508e1a092a332c3789fb97eaef99fb"
  license "Apache-2.0"
  
  depends_on "openjdk@17"
  depends_on "maven" => :build

  def install
    # Build the project
    system "mvn", "clean", "package", "-DskipTests"
    
    # Install JAR file
    libexec.install "bin/mongo-launcher.jar"
    
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
    assert_match "1.0.0", shell_output("#{bin}/mongo-launcher --version")
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