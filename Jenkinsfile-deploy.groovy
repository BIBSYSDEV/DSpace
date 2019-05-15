pipeline {
    agent any

    tools {
        maven 'Maven 3.6.1'
    }


//    parameters {
//        choice(name: 'Destination', choices: ['brage-utvikle.bibsys.no', 'brage-test.bibsys.no']
//                , description: 'Where do you want to deploy to?')
//    }

    environment {
        VERSION = "${env.BRANCH_NAME}".replaceAll('/', '_').toLowerCase()
		DEVSTEP = "utvikle"
        // TARGET_HOST = "${params.Destination}"
        TARGET_HOST = "brage-utvikle.bibsys.no"
        CUSTOMZ = "customizations"
    }


    stages {

        stage('Select development phase') {
            steps {
				script {
					def institusjoner = readYaml file: "${env.WORKSPACE}/ansible/institusjoner.yml"
					def kunder = []
					institusjoner.properties.each {
						prop, val ->
							kunder << prop
					}
	                try {
	                    timeout(activity: true, time: 120, unit: 'SECONDS') {
	                        input(id: 'phaseInput', message: 'Velg utviklingsfase', parameters: [
	                                choice(choices: ["utvikle", "test", "produksjon"], name: 'DEVSTEP'),
									choice(choices: kunder, name: 'KUNDE')
	                        ])
	                    }
	                } catch (err) {
	                    println("Release aborted")
	                    throw err
	                }
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
							fase: $DEVSTEP,
							jenkins_workspace: "${env.WORKSPACE}",
							kunde: $KUNDE
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
                            input(message: "Deploy branch: $VERSION to phase: $DEVSTEP?")
                        }
                    } catch (err) {
                        println("Release aborted")
                        throw err
                    }
                }
                println("Deploying branch $VERSION to server: $TARGET_HOST")
            }
        }

		stage('Checkout Brage6 customizations') {
            steps {

//                println("Running build #${env.BUILD_ID} of job ${env.JOB_NAME}, git branch: ${env.BRANCH_NAME}" as java.lang.Object)
                script {
                    brageVars = checkout scm
                    dir("${CUSTOMZ}") {
                        //configVars = checkout scm
                        git url: 'git@git.bibsys.no:team-rosa/brage6-customizations.git'
                    }
                }
            }
        }

/*
        stage('Select Institution') {
            steps {
                script {
                    env.WORKSPACE = pwd();
                    def file = readFile "${ENV_FOLDER}/env/inst.txt"
                    try {
                        timeout(activity: true, time: 120, unit: 'SECONDS') {
                            input(id: 'kundeInput', message: 'Choose institution', parameters: [
                                    choice(choices: file,name: 'kunde')
                            ])
                        }
                    } catch (err) {
                        println("Release aborted")
                        throw err
                    }
                }
            }
        }
*/

/*
        stage('Pre-build scripts') {
            steps {
                echo "Fetching environmentvariables for build from : ${ENV_FOLDER}/env/env.properties"
                load "${ENV_FOLDER}/env/env.properties"
                echo "TARGET_FOLDER: ${TARGET_FOLDER}"
                echo "COMPRESSED_INSTALLER_FILE: ${COMPRESSED_INSTALLER_FILE}"
                echo "INSTALLER_SCRIPT: ${INSTALLER_SCRIPT}"

                echo "copying local.cfg from environment-project in brage6"
                sh "cp ${ENV_FOLDER}/env/local_UTVIKLE.cfg dspace/config/local.cfg"

                echo "copying feide.properties from environment-project in brage6"
                sh "cp ${ENV_FOLDER}/env/feide_UTVIKLE.properties dspace/config/feide.properties"

                echo "copying handleserver.properties from environment-project in brage6"
                sh "cp ${ENV_FOLDER}/env/handleserver_UTVIKLE.properties dspace/config/handleserver.properties"

                sh "echo jenkins.url = ${JENKINS_URL} >> dspace/config/local.cfg"
                sh "echo jenkins.tag = ${BUILD_TAG} >> dspace/config/local.cfg"
                sh "echo git.branch = ${GIT_BRANCH} >> dspace/config/local.cfg"
                sh "echo git.commit = ${GIT_COMMIT} >> dspace/config/local.cfg"
            }
        }
*/
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
							fase: 'utvikle',
							jenkins_workspace: "${env.WORKSPACE}",
							kunde: 'unit'
						]
					)
				}
			}
		}

/*
        stage('Copy files to brage-server, running installer-script') {
            steps {
                echo "Destination environment: ${TARGET_HOST}"
                sh "ssh ${TARGET_HOST} mkdir -p ${TARGET_FOLDER}"

                echo "Compressing dspace/target/dspace-installer to ${COMPRESSED_INSTALLER_FILE}"
                sh "tar -zcf ${COMPRESSED_INSTALLER_FILE} -C dspace/target dspace-installer"

                echo "Transferring ${COMPRESSED_INSTALLER_FILE} to ${TARGET_HOST}:${TARGET_FOLDER}"
                sh "scp ${COMPRESSED_INSTALLER_FILE} ${TARGET_HOST}:${TARGET_FOLDER}/"

                echo "Transferring deployscripts to ${TARGET_HOST}:${TARGET_FOLDER}"
                sh "scp -r deployscripts ${TARGET_HOST}:${TARGET_FOLDER}/"
                sh "scp -r deployscripts/${INSTALLER_SCRIPT} ${TARGET_HOST}:${TARGET_FOLDER}/"

                echo "Executing ${TARGET_FOLDER}/installer.sh on ${TARGET_HOST}"
                sh "ssh ${TARGET_HOST} 'source ~/.profile; sh ${TARGET_FOLDER}/${INSTALLER_SCRIPT} unit;'"
            }
        }
*/
        stage('Cleanup') {
            steps {
                cleanWs()
            }
        }
    }

//    post {
//        failure {
//            script {
//                def message = "${currentBuild.fullDisplayName} - Failure after ${currentBuild.durationString.replaceFirst(" and counting", "")}"
//                emailext(
//                        subject: "FAILURE: ${currentBuild.fullDisplayName}",
//                        body: "${message}\n Open: ${env.BUILD_URL}",
//                        to: 'teamrosa@bibsys.no',
//                        attachlog: true,
//                        compresslog: true,
//                        recipientProviders: [[$class: 'CulpritsRecipientProvider']]
//                )
//
//            }
//        }
//    }
}

