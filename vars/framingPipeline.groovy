// Jenkinsfile Usage:
// @Library('central-shared-lib@main')
// framimgPipeline(
//     sections: [
//         [name: 'Overview', path: 'docs/overview.md'],
//         [name: 'Feasibility', path: 'docs/feasibility.md'],
//         ...
//     ],
//     outputPath: 'docs/Framing.md',
//     runNpmCi: true,
//     docsCiCmd: 'npm run docs:ci',
//     pandocOutput: 'docs.Framing.pdf', // optional, if set uses pandoc to compile
//     todoPolicy: 'failOnMainAfterCompile', // 'ignore'|'warn'|'failAfterCompile'|'failOnMainAfterCompile', default 'failOnMainAfterCompile'
// )

def call(Map cfg = [:]) {
    List<Map> sections = (cfg.sections ?: []) as List<Map>
    if (!sections) error("framingPipeline: cfg.sections is required.")

    String outputPath = (cfg.outputPath ?: 'docs/Framing.md') as String
    boolean runNpmCi = (cfg.runNpmCi == null ? true : cfg.runNpmCi) as boolean
    String docsCiCmd = (cfg.docsCiCmd ?: 'npm run docs:ci') as String
    String pandocOutput = (cfg.pandocOutput ?: '') as String
    String agentLabel = (cfg.agentLabel ?: 'pi-agent') as String
    String todoPolicy = (cfg.todoPolicy ?: 'failOnMainAfterCompile') as String
    List<String> todoTokens = (cfg.todoTokens ?: ['TODO', 'TBD', 'FIXME']) as List<String>

    node(agentLabel) {
        timestamps {
            properties([disableConcurrentBuilds()])

            stage('Checkout') {
                checkout scm
            }

            stage('Validate sections exist + non-empty') {
                sections.each { s ->
                    String name = (s.name ?: 'Unnamed') as String
                    String path = (s.path ?: '') as String
                    if (!path.trim()) error("Section '${name}' is missing required 'path' property.")

                    sh(label: "Check ${name}", script: """
                        set -euo pipefail
                        test -f '${escapeSh(path)}' || (echo "Missing required section: ${path}" && exit 1)
                        test -s '${escapeSh(path)}' || (echo "Section is empty: ${path}" && exit 1)
                    """.stripIndent())

                    String content = readFile(file: path) ?: ""
                    if (!content.trim()) error("Section '${name}' is empty: ${path}")
                }
            }

            stage('Docs checks (format/lint/spell)') {
                if (runNpmCi) {
                    sh(label: "npm ci", script: "set -euo pipefail\nnpm ci")
                }
                sh(label: "Run docs CI checks", script: "set -euo pipefail\n${docsCiCmd}")
            }

            stage('Compile') {
                if (pandocOutput?.trim()) {
                    compileWithPandoc(sections.collect {it.path as String}, pandocOutput)
                    archiveArtifacts(artifacts: pandocOutput, fingerprint: true)
                } else {
                    compileByConcatenation(sections.collect {it.path as String}, outputPath)
                    archiveArtifacts(artifacts: outputPath, fingerprint: true)
                }
            }

            stage('Gate: No TODO Placeholders') {
                if (todoPolicy == 'ignore') {
                    echo "TODO Gating: ignore"
                    return
                }

                def hits = scanForPlaceholders(sections.collect {it.path as String}, todoTokens)
                if(hits.isEmpty()) {
                    echo "TODO Gating: No placeholders found."
                    return
                }

                echo "TODO Gating: Found ${hits.size()} placeholder(s) indicating documents are not completed:"
                hits.each { file, lines -> 
                    echo " - ${file}"
                    lines.take(10).each { h -> echo "    L${h.line}: ${h.text}" }
                    if (lines.size() > 10) echo "    ... (${lines.size() - 10} more in this file)"
                }

                boolean isPR = ((env.CHANGE_ID ?: "").trim() != "")
                boolean isMain = ['main', 'master'].contains((env.BRANCH_NAME ?: '').trim())

                switch(todoPolicy) {
                    case 'warn':
                        currentBuild.result = 'UNSTABLE'
                        echo "TODO Gating: Marking build as UNSTABLE due to placeholders."
                        return
                    case 'failAfterCompile':
                        error("TODO Gating: Failing build due to placeholders after compile.")
                        break
                    case 'failOnMainAfterCompile':
                        if (isMain && !isPR) {
                            error("TODO Gating: Failing build due to placeholders on main branch.")
                        } else {
                            currentBuild.result = 'UNSTABLE'
                            echo "TODO Gating: Marking build as UNSTABLE due to placeholders."
                        }
                }
            }
        }
    }
}

def compileByConcatenation(List<String> paths, String outputPath) {
    String outDir = outputPath.contains('/') ? outputPath.substring(0, outputPath.lastIndexOf('/')) : '.'
    sh(script: "mkdir -p '${escapeSh(outDir)}'")

    String files = paths.collect {"'${escapeSh(it)}'"}.join(' ')
    sh(label: "Concat -> ${outputPath}", script: """
        set -euo pipefail
        rm -f '${escapeSh(outputPath)}'
        for f in ${files}; do
            cat "\$f" >> '${escapeSh(outputPath)}'
            echo "\\n\\n---\\n\\n" >> '${escapeSh(outputPath)}'
        done
    """.stripIndent())
}

def compileWithPandoc(List<String> paths, String pandocOutput) {
    String outDir = pandocOutput.contains('/') ? pandocOutput.substring(0, pandocOutput.lastIndexOf('/')) : '.'
    sh(script: "mkdir -p '${escapeSh(outDir)}'")

    String files = paths.collect {"'${escapeSh(it)}'"}.join(' ')
    sh(label: "Pandoc -> ${pandocOutput}", script: """
        set -euo pipefail
        pandoc ${files} -o '${escapeSh(pandocOutput)}'
    """.stripIndent())
}

def scanForPlaceholders(List<String> paths, List<String> tokens) {
    def patterns = tokens.collect { t ->
        java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(t) + "\\b", java.util.regex.Pattern.CASE_INSENSITIVE)
    }

    def results = [:].withDefault { [] }

    paths.each { p -> 
        if (!fileExists(p)) return

        def lines = (readFile(file: p) ?: "").readLines()
        lines.eachWithIndex { line, idx ->
            def trimmed = line?.trim() ?: ""
            if (!trimmed) return

            boolean match = patterns.any { pat -> pat.matcher(line).find() }
            if (match) {
                results[p] << [line: idx + 1, text: trimmed]
            }
        }
    }

    return results
}

String escapeSh(String str) {
    return (str ?: "").replace("'", "'\"'\"'")
}