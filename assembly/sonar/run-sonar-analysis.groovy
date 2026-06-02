import groovy.json.JsonSlurper

import java.net.HttpURLConnection
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Properties
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

def qualityGateWait = project.properties.get("sonar.qualitygate.wait")
def qualityGateTimeout = project.properties.get("sonar.qualitygate.timeout")
def shouldWaitForQualityGate = qualityGateWait?.toBoolean() ?: false
def mvnExecutable = System.getProperty("os.name", "")
    .toLowerCase()
    .contains("win") ? "mvn.cmd" : "mvn"

def jsonSlurper = new JsonSlurper()

def readJson = { String url ->
    def connection = (HttpURLConnection) new URL(url).openConnection()
    connection.setRequestProperty("Accept", "application/json")
    connection.setRequestProperty(
            "Authorization",
            "Basic " + Base64.encoder.encodeToString(
                    "${sonarLogin}:".getBytes(StandardCharsets.UTF_8)))
    connection.setConnectTimeout(15_000)
    connection.setReadTimeout(15_000)

    def responseCode = connection.responseCode
    def responseStream = responseCode >= 400
            ? connection.errorStream
            : connection.inputStream
    def responseBody = responseStream?.getText(StandardCharsets.UTF_8.name()) ?: ""
    if (responseCode >= 400) {
        throw new IllegalStateException(
                "[Sonar] API request failed (${responseCode}) for ${url}: ${responseBody}")
    }
    jsonSlurper.parseText(responseBody)
}

def formatConditions = { List conditions ->
    if (!conditions) {
        return "No failing conditions were returned by SonarCloud."
    }
    conditions.collect { condition ->
        def status = condition.status ?: "UNKNOWN"
        def metricKey = condition.metricKey ?: "unknown-metric"
        def actualValue = condition.actualValue ?: "n/a"
        def comparator = condition.comparator ?: ""
        def errorThreshold = condition.errorThreshold ?: "n/a"
        "- ${metricKey}: ${status} (actual=${actualValue} ${comparator} threshold=${errorThreshold})"
    }.join(System.lineSeparator())
}

def loadReportTask = {
    def reportTaskFile = new File(project.build.directory, "sonar/report-task.txt")
    if (!reportTaskFile.isFile()) {
        throw new IllegalStateException(
                "[Sonar] Missing scanner metadata: ${reportTaskFile}")
    }

    def reportTask = new Properties()
    reportTaskFile.withInputStream { reportTask.load(it) }
    reportTask
}

def awaitQualityGate = { Properties reportTask, long timeoutSeconds ->
    def ceTaskUrl = reportTask.getProperty("ceTaskUrl")
    def dashboardUrl = reportTask.getProperty("dashboardUrl")
    def serverUrl = reportTask.getProperty("serverUrl")
    if (!ceTaskUrl || !serverUrl) {
        throw new IllegalStateException(
                "[Sonar] Incomplete scanner metadata in report-task.txt for ${projectKey}")
    }

    def deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
    while (true) {
        def ceTaskResponse = readJson(ceTaskUrl)
        def ceTask = ceTaskResponse.task ?: [:]
        def ceStatus = ceTask.status ?: "UNKNOWN"
        if (ceStatus == "SUCCESS") {
            def analysisId = ceTask.analysisId
            if (!analysisId) {
                throw new IllegalStateException(
                        "[Sonar] Compute Engine task succeeded without analysisId for ${projectKey}")
            }

            def projectStatusUrl = serverUrl + "/api/qualitygates/project_status?analysisId="
                    + URLEncoder.encode(analysisId, StandardCharsets.UTF_8)
            def qualityGateResponse = readJson(projectStatusUrl)
            def projectStatus = qualityGateResponse.projectStatus ?: [:]
            def status = projectStatus.status ?: "UNKNOWN"
            println "[Sonar] Quality Gate status for ${projectKey}: ${status}"
            if (dashboardUrl) {
                println "[Sonar] Dashboard: ${dashboardUrl}"
            }

            if (status == "NONE") {
                println "[Sonar] Quality Gate not computed for ${projectKey}; continuing without failing the build."
                return
            }
            if (status != "OK") {
                def dashboardSuffix = dashboardUrl
                    ? "${System.lineSeparator()}Dashboard: ${dashboardUrl}"
                    : ""
                throw new IllegalStateException(
                    "[Sonar] Quality Gate failed for ${projectKey}: ${status}"
                        + "${System.lineSeparator()}"
                        + "${formatConditions(projectStatus.conditions as List)}"
                        + dashboardSuffix)
            }
            return
        }

        if (ceStatus in ["FAILED", "CANCELED"]) {
                def dashboardSuffix = dashboardUrl
                    ? "${System.lineSeparator()}Dashboard: ${dashboardUrl}"
                    : ""
            throw new IllegalStateException(
                    "[Sonar] Compute Engine task ended with status ${ceStatus} for ${projectKey}"
                        + dashboardSuffix)
        }

        if (System.nanoTime() >= deadline) {
                def dashboardSuffix = dashboardUrl
                    ? "${System.lineSeparator()}Dashboard: ${dashboardUrl}"
                    : ""
            throw new TimeoutException(
                    "[Sonar] Timed out waiting ${timeoutSeconds}s for SonarCloud analysis of ${projectKey}"
                        + dashboardSuffix)
        }
        Thread.sleep(5_000)
    }
}

def mvnCmd = [
    mvnExecutable, "sonar:sonar",
    "-Dsonar.login=${sonarLogin}",
    "-Dsonar.projectKey=${projectKey}"
].collect { it.toString() }

if (shouldWaitForQualityGate) {
    mvnCmd << "-Dsonar.qualitygate.wait=false"
} else if (qualityGateWait != null) {
    mvnCmd << "-Dsonar.qualitygate.wait=${qualityGateWait}".toString()
}
if (qualityGateTimeout != null) {
    mvnCmd << "-Dsonar.qualitygate.timeout=${qualityGateTimeout}".toString()
}

def procBuilder = new ProcessBuilder(mvnCmd)
procBuilder.redirectErrorStream(true)
procBuilder.directory(project.basedir)
def process = procBuilder.start()

def out = new BufferedReader(new InputStreamReader(process.inputStream))
def outputLines = []
out.eachLine {
    outputLines << it
    println it
}

def exitCode = process.waitFor()
if (exitCode != 0) {
    def tailSize = Math.min(60, outputLines.size())
    def tail = outputLines.subList(outputLines.size() - tailSize, outputLines.size())
            .join(System.lineSeparator())
    throw new IllegalStateException(
            "[Sonar] Analysis failed for $projectKey (exit code $exitCode). " +
                "Last scanner output lines:\n$tail")
}

        if (shouldWaitForQualityGate) {
            def timeoutSeconds = qualityGateTimeout
                ? Long.parseLong(qualityGateTimeout.toString())
                : 300L
            awaitQualityGate(loadReportTask(), timeoutSeconds)
        }
