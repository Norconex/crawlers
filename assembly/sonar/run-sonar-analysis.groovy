import java.util.concurrent.*

def sonarSkip = project.properties.get("sonar.skip") ?: "false"
if (sonarSkip.toBoolean()) {
    println "[Sonar] Skipping analysis for ${project.artifactId} (sonar.skip=true)"
    return
}

def sonarLogin = System.getenv("SONAR_TOKEN")
if (!sonarLogin) {
    println "[Sonar] Environment variable SONAR_TOKEN not set — skipping analysis"
    return
}

def projectKey = "${project.groupId}:${project.artifactId}"
println "[Sonar] Running analysis for: $projectKey"

def mvnCmd = [
    "mvn", "sonar:sonar",
    "-Dsonar.login=${sonarLogin}",
    "-Dsonar.projectKey=${projectKey}"
].collect { it.toString() }

def procBuilder = new ProcessBuilder(mvnCmd)
procBuilder.redirectErrorStream(true)
procBuilder.directory(project.basedir)
def process = procBuilder.start()

def out = new BufferedReader(new InputStreamReader(process.inputStream))
out.eachLine { println it }

def exitCode = process.waitFor()
if (exitCode != 0) {
    throw new IllegalStateException("[Sonar] Analysis failed for $projectKey (exit code $exitCode)")
}
