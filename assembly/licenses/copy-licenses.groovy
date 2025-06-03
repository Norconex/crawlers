import groovy.json.JsonSlurper
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.regex.Pattern

// Input and output paths
def thirdPartyFile = project.basedir.toPath().resolve(
    "target/generated-sources/license/THIRD-PARTY.txt")
def licenseSrcDir = project.parent.basedir.toPath().resolve(
    "assembly/licenses/texts")
def licenseTargetDir = project.basedir.toPath().resolve(
    "target/generated-resources/licenses")
def mappingFile = project.parent.basedir.toPath().resolve(
    "assembly/licenses/license_mappings.json")
// Ensure target directory exists
Files.createDirectories(licenseTargetDir)

// Read license mappings from JSON
def jsonSlurper = new JsonSlurper()
def licenseMappings = jsonSlurper.parse(mappingFile.toFile()).licenses

// Helper function to parse licenses with nested parentheses
def parseLicenses(String text) {
    def licenses = []
    def current = new StringBuilder()
    def nestLevel = 0
    def inLicense = false

    text.eachWithIndex { ch, i ->
        if (ch == '(' && !inLicense) {
            inLicense = true
            nestLevel++
        } else if (inLicense) {
            if (ch == '(') {
                nestLevel++
                current << ch
            } else if (ch == ')') {
                nestLevel--
                if (nestLevel == 0) {
                    inLicense = false
                    licenses << current.toString().trim()
                    current = new StringBuilder()
                } else {
                    current << ch
                }
            } else {
                current << ch
            }
        }
    }
    licenses
}

// Pattern to match consecutive licenses with nested parentheses
def consecutiveLicensePattern = ~/^(\([^()]+(?:\([^()]*\)[^()]*)*\)\s*)+/

// Pattern to match plus-separated licenses with nested parentheses
def plusLicensePattern = ~/^\([^()]+(?:\([^()]*\)[^()]*)*\)/

// Track copied licenses to avoid duplicates
def copiedSpdxIds = new HashSet<String>()

// Collect normalized lines and intro lines
def normalizedLines = []
def introLines = []

// Read THIRD-PARTY.txt lines
Files.readAllLines(thirdPartyFile).each { line ->
    def trimmedLine = line.trim()
    if (!trimmedLine) {
        introLines << line // Preserve empty lines in intro
        return
    }

    def licenses = []
    def dependencyInfo = trimmedLine

    // Try matching consecutive parenthetical licenses
    def consecutiveMatcher = consecutiveLicensePattern.matcher(trimmedLine)
    if (consecutiveMatcher.find()) {
        def matchedText = consecutiveMatcher.group(0)
        licenses = parseLicenses(matchedText)
        dependencyInfo = trimmedLine.replace(matchedText, '').trim()
    }
    // Try matching plus-separated licenses
    else if (trimmedLine =~ plusLicensePattern) {
        def plusMatcher = plusLicensePattern.matcher(trimmedLine)
        if (plusMatcher.find()) {
            def licenseString = plusMatcher.group(0)[1..-2] // Remove outer parentheses
            licenses = licenseString.split(/\+/).collect { it.trim() }
            dependencyInfo = trimmedLine.replace(plusMatcher.group(0), '').trim()
        }
    }
    // If no license pattern matches, treat as intro line
    else {
        introLines << line
        return
    }

    // Normalize licenses
    def normalizedLicenses = licenses.collect { license ->
        license = license.trim()
        def result = licenseMappings.find { mapping ->
            // Match "<spdx-id>:" first
            if (license.startsWith("${mapping.spdx_id}:")) {
                return mapping;
            }
            // Then other patterns
            mapping.patterns.any { pattern ->
                def regex = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
                regex.matcher(license).find()
            } ? mapping : null
        }
        if (!result) {
            throw new RuntimeException("No mapping found for license \"${license}\" in line: \"${trimmedLine}\"")
        }
        [spdx_id: result.spdx_id, full_name: result.full_name]
    }

    // Format normalized license string
    def licenseString = normalizedLicenses.collect { lic ->
        "(${lic.spdx_id}: ${lic.full_name})"
    }.join(' ')

    // Copy license files
    normalizedLicenses.each { lic ->
        if (copiedSpdxIds.add(lic.spdx_id)) {
            def sourceFile = licenseSrcDir.resolve("${lic.spdx_id}.txt")
            def targetFile = licenseTargetDir.resolve("${lic.spdx_id}.txt")
            if (Files.exists(sourceFile)) {
                Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING)
                println "Copied license: ${lic.spdx_id}"
            } else {
                throw new RuntimeException("License file for \"${lic.spdx_id}\" not found in: ${sourceFile}")
            }
        }
    }

    // Store normalized line
    normalizedLines << [license: licenseString, dependency: dependencyInfo]
}

// Sort lines by artifact name (dependency)
normalizedLines.sort { a, b -> a.dependency <=> b.dependency }

// Write normalized THIRD-PARTY.txt, preserving intro lines
def outputFile = thirdPartyFile
def outputContent = []
if (introLines) {
    outputContent.addAll(introLines)
    outputContent << '' // Add a blank line after intro
}
outputContent.addAll(normalizedLines.collect { "${it.license} ${it.dependency}" })
Files.writeString(outputFile, outputContent.join('\n'))
println "Rewrote THIRD-PARTY.txt with normalized licenses, sorted by artifact name"