// License gate, MASTER_SPEC section 11: every runtime dependency of every platform build must declare an allowed
// license in its Maven POM (or a parent POM). Forbidden, LGPL (case by case, needs written justification) and
// undeclared licenses fail the task. Run: ./gradlew checkLicenses --no-configuration-cache (ci/licenses.sh).
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import org.w3c.dom.Element

val checked = mapOf(
    ":androidApp" to listOf("releaseRuntimeClasspath"),
    ":app" to listOf("desktopRuntimeClasspath", "wasmJsRuntimeClasspath"),
    ":core" to listOf("desktopRuntimeClasspath", "wasmJsRuntimeClasspath"),
)

// Allowed (section 11): MPL-2.0, Apache-2.0, BSD, MIT, ISC, zlib, public domain; SIL OFL for fonts.
val allowed = listOf(
    "apache" to Regex("""apache|apache-2\.0|www\.apache\.org/licenses""", RegexOption.IGNORE_CASE),
    "mit" to Regex("""\bmit\b|opensource\.org/licenses/mit""", RegexOption.IGNORE_CASE),
    "bsd" to Regex("""\bbsd\b|bsd-[0-9]-clause""", RegexOption.IGNORE_CASE),
    "isc" to Regex("""\bisc\b""", RegexOption.IGNORE_CASE),
    "zlib" to Regex("""\bzlib\b""", RegexOption.IGNORE_CASE),
    "mpl-2.0" to Regex("""mozilla public license,? (version |v)?2\.0|\bmpl[- ]?2\.0|mozilla\.org/mpl/2\.0""", RegexOption.IGNORE_CASE),
    "ofl" to Regex("""open font license|\bofl\b""", RegexOption.IGNORE_CASE),
    "public domain" to Regex("""public domain|cc0|unlicense""", RegexOption.IGNORE_CASE),
)
val lgpl = Regex("""lesser general public|\blgpl""", RegexOption.IGNORE_CASE)
val forbidden = Regex("""general public license|\bgpl|affero|\bagpl|sspl|server side public|commons clause|business source""", RegexOption.IGNORE_CASE)

fun pomLicenses(pom: File): Pair<List<String>, Triple<String, String, String>?> {
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom)
    val licenses = doc.getElementsByTagName("license").let { nodes ->
        (0 until nodes.length).map { i ->
            val e = nodes.item(i) as Element
            listOf("name", "url").joinToString(" ") { tag -> e.getElementsByTagName(tag).item(0)?.textContent?.trim().orEmpty() }.trim()
        }
    }
    val parent = doc.getElementsByTagName("parent").item(0) as Element?
    val coords = parent?.let { p ->
        fun t(tag: String) = p.getElementsByTagName(tag).item(0)?.textContent?.trim().orEmpty()
        Triple(t("groupId"), t("artifactId"), t("version"))
    }
    return licenses to coords
}

tasks.register("checkLicenses") {
    group = "verification"
    description = "Fails if a runtime dependency has a license outside MASTER_SPEC section 11."
    notCompatibleWithConfigurationCache("resolves POM files of the checked configurations at execution time")
    doLast {
        val ids = checked.flatMap { (path, configs) ->
            configs.flatMap { c ->
                project(path).configurations.getByName(c).incoming.resolutionResult.allComponents
                    .mapNotNull { it.id as? ModuleComponentIdentifier }
            }
        }.distinctBy { it.displayName }.sortedBy { it.displayName }

        val poms = dependencies.createArtifactResolutionQuery()
            .forComponents(ids)
            .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
            .execute().resolvedComponents
            .associate { c -> c.id.displayName to c.getArtifacts(MavenPomArtifact::class.java).firstNotNullOfOrNull { (it as? org.gradle.api.artifacts.result.ResolvedArtifactResult)?.file } }

        fun licensesOf(pom: File?, depth: Int = 0): List<String> {
            if (pom == null || depth > 5) return emptyList()
            val (own, parent) = pomLicenses(pom)
            if (own.isNotEmpty() || parent == null) return own
            val cfg = configurations.detachedConfiguration(dependencies.create("${parent.first}:${parent.second}:${parent.third}@pom"))
            return licensesOf(cfg.resolve().firstOrNull(), depth + 1)
        }

        val counts = sortedMapOf<String, Int>()
        val problems = mutableListOf<String>()
        ids.forEach { id ->
            val lic = licensesOf(poms[id.displayName])
            val kinds = lic.mapNotNull { l -> allowed.firstOrNull { it.second.containsMatchIn(l) }?.first }
            when {
                lic.isEmpty() -> problems += "${id.displayName}: no license declared"
                // A dual-licensed module passes if one of its licenses is allowed (e.g. "MIT or GPL").
                kinds.isNotEmpty() -> kinds.distinct().forEach { k -> counts[k] = (counts[k] ?: 0) + 1 }
                lic.any { lgpl.containsMatchIn(it) } -> problems += "${id.displayName}: LGPL needs a written exception (${lic.joinToString()})"
                lic.any { forbidden.containsMatchIn(it) } -> problems += "${id.displayName}: FORBIDDEN (${lic.joinToString()})"
                else -> problems += "${id.displayName}: unknown license (${lic.joinToString()})"
            }
        }
        println("checkLicenses: ${ids.size} modules; " + counts.entries.joinToString { "${it.key} ${it.value}" })
        if (problems.isNotEmpty()) {
            problems.forEach { println("  $it") }
            throw GradleException("${problems.size} dependencies fail the license policy (MASTER_SPEC section 11)")
        }
    }
}
