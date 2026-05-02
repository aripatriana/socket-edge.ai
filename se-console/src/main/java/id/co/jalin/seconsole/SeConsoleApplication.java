package id.co.jalin.seconsole;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SE-Console — Socket Edge Console.
 *
 * Web-based operations console for the Jalin ISO Load Balancer engine.
 * Runs as a separate JVM on the same host as the engine and communicates
 * with it via JMX (metrics/control), the file system (config), and
 * whitelisted shell scripts (legacy ops).
 *
 * @see <a href="docs/architecture.md">Architecture overview</a>
 */
@SpringBootApplication
@EnableScheduling
public class SeConsoleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeConsoleApplication.class, args);
    }
}
