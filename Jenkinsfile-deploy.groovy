pipeline {
    agent any

    tools {
        maven 'Maven 3.6.1'
    }

    environment {
        VERSION = "${env.BRANCH_NAME}".replaceAll('/', '_').toLowerCase()
		DEVSTEP = ""
		KUNDE = ""
        CUSTOMZ = "customizations"
    }


    stages {

        stage('Provide input parameters') {
            steps {
				script {
					def institusjoner = readYaml file: "ansible/institusjoner.yml"
					def kunder = []

					institusjoner.each { prop, val ->
						if (prop in ["metaClass","class"]) return
						kunder << prop
					}

	                try {
	                    timeout(activity: true, time: 120, unit: 'SECONDS') {
	                        inputResult = input(id: 'phaseInput', message: 'Velg parametre', parameters: [
	                                choice(choices: ["utvikle", "test", "produksjon"], name: 'devstep', description: 'Utviklingsfase:'),
									choice(choices: kunder, name: 'kunde', description: "Kunde:")
	                        ])
	                    }
	                } catch (err) {
	                    echo "Release aborted"
	                    throw err
	                }
					
					env.DEVSTEP = inputResult["devstep"]
					env.KUNDE = inputResult["kunde"]
				}
            }
        }

		stage('Bootstrap workspace') {
			steps {
				dir("${env.WORKSPACE}/ansible") {
					ansiblePlaybook(
					playbook: 'pre-build.yml',
					inventory: 'localhost,',
					extraVars: [
							fase: inputResult.devstep,
							jenkins_workspace: env.WORKSPACE,
							kunde: inputResult.kunde
						]
					)
				}
			}
		}

        stage('Confirm deploy') {
            steps {
                script {
                    try {
                        timeout(activity: true, time: 120, unit: 'SECONDS') {
                            input(message: "Deploy branch $VERSION for ${env.KUNDE} to phase ${env.DEVSTEP}?")
                        }
                    } catch (err) {
                        println("Release aborted")
                        throw err
                    }
                }
                println("Deploying branch $VERSION for ${KUNDE} to ${DEVSTEP}")
            }
        }

		stage('Checkout Brage6 customizations') {
            steps {
                script {
                    brageVars = checkout scm
                    dir("${CUSTOMZ}") {
                        //configVars = checkout scm
                        git url: 'git@git.bibsys.no:team-rosa/brage6-customizations.git'
                    }
                }
            }
        }

        stage('Maven Build') {
            steps {
                echo "Building with maven"
                sh 'mvn package -Dmirage2.on=true -P !dspace-lni,!dspace-sword,!dspace-jspui,!dspace-rdf'
            }
        }

		stage('Deploy Brage') {
			steps {
				dir("${env.WORKSPACE}/ansible") {
					ansiblePlaybook(
					playbook: 'deploy-brage.yml',
					inventory: 'hosts',
					extraVars: [
							fase: env.DEVSTEP,
							jenkins_workspace: env.WORKSPACE,
							kunde: env.KUNDE
						]
					)
				}
			}
		}

        stage('Cleanup') {
            steps {
                cleanWs()
            }
        }
    }

/*
    post {
        failure {
            script {
                def message = "${currentBuild.fullDisplayName} - Failure after ${currentBuild.durationString.replaceFirst(" and counting", "")}"
                emailext(
                        subject: "FAILURE: ${currentBuild.fullDisplayName}",
                        body: "${message}\n Open: ${env.BUILD_URL}",
                        to: 'teamrosa@bibsys.no',
                        attachlog: true,
                        compresslog: true,
                        recipientProviders: [[$class: 'CulpritsRecipientProvider']]
                )

            }
        }
    }
*/
}

