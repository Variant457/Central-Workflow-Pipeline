def call(Map cfg = [:]) {
  pipeline {
    agent any

    // TODO: parameters/options

    stages {
      stage('File Gate') {
        steps {
          // TODO: call a helper from src/ to validate extensions under docs/
        }
      }

      stage('Completeness Gate') {
        steps {
          // TODO: parse framing manifest + docs; block vs warn based on status
        }
      }

      stage('Lint / Format') {
        steps {
          // TODO: run lint check
          // TODO: if fix applied, stash diff as artifact and mark UNSTABLE
        }
      }

      stage('Spell Check') {
        steps {
          // TODO: run spell check
          // TODO: produce patch artifact if auto-fix attempted
        }
      }

      stage('Human Signoff') {
        steps {
          // TODO: input step (or require PR approvals outside Jenkins)
        }
      }

      stage('Compile Framing Doc') {
        steps {
          // TODO: compile overview+requirements+slices+risks+feasibility into one artifact
        }
      }
    }
  }
}
