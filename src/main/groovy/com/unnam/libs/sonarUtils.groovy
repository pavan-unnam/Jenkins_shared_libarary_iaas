package com.unnam.libs

import groovy.transform.Field

@Field
def cmd = new cmd()

@Field
def log = new log()

@Field
def status = new status()


def checkSonarStatus(Map propertyInfo) {
    def filePath = cmd.getOutput('set +x; find . -name "report-task.txt" | cut -c3-')
    def sonarReport = readProperties file: filePath
    def url = sonarReport.ceTaskUrl

    def sonarStatus = 'IN_PROGRESS'
    def continueLoop =  true
    def sonarStatusSleep = propertyInfo.sonarStatusSleep as Integer

    if((propertyInfo.sonarTimeout as Integer) <= sonarStatusSleep) {
        sonarStatusSleep = ((propertyInfo.sonarTimeout as Integer)/2) as Integer
        common.toggleCurrentBuildStatus (propertyInfo, "sonarqube", "sonarStatusSleep should be less than sonarTimeout, it has been updated to ${sonarStatusSleep}")
    }

    println " Getting sonarStatus : ${url}"

    while(continueLoop) {
        withSonarQubeEnv (installationName: propertyInfo.sonarQubeServer, envOnly : true) {
            withEnv(["SONAR_TOKEN=" + env.SONAR_AUTH_TOKEN]) {
                response = cmd.getOutput('set +x; curl --silent --user $SONAR_TOKEN --url ' + url + ' -tlsv1.2 --retry-connrefused --retry 3 --retry-delay 20')
            }
            json = readJSON text: response
            sonarStatus = json.task.status

            println " sonarStatus : ${sonarStatus}"
        }
        continueLoop = sonarStatus.contains('PENDING') || sonarStatus.contains('IN_PROGRESS')

        if(continueLoop) {sleep(sonarStatusSleep)}
    }
}

def mavenRunSonar (Map propertyInfo) {
    log.debugMessage ("<---- Entering method", common.getCuurentMethodName())

    def SONAR_PLUGIN_VERSION = "3.9.1.2184"

    def sonarBranch = (propertyInfo.appVersion.contains('-SNAPSHOT') ) ? "${env.BRANCH_NAME}" : "${propertyInfo.releaseBranchPrefix}${propertyInfo.appVersion}"

    log.debugMessage ("Sonar Branch : ${sonarBranch}\nSonarQube installation : ${propertyInfo.sonarQubeServer}")

    try {
        configFileProvider ([configFile(fileId: "${propertyInfo.mvnSettingsXML}", variable : 'MAVEN_SETTINGS')]) {
            cmd.execute("mvn ${propertyInfo.jacocoGoal} -s $MAVEN_SETTINGS")
        }
    } catch (Exception ex) {
        log.message(messageNumber: 'ERROR0017', messageTitle: "Problem running mvn ${propertyInfo.jacocoGoal}")
        println 'Exception message:\n' +ex.getMessage()
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE, propertyInfo.sonarFailOnError)
    }

    try {
        withSonarQubeEnv (propertyInfo.sonarQubeServer) {
            log.debugMessage ("Sonar plugin Version: ${SONAR_PLUGIN_VERSION}")
            cmd.execute "echo '\\n${fortify.FORTIFY_UPLOAD_DIRECTORY}/**' >> .gitignore"
            configFileProvider([configFile(fileId: "${propertyInfo.mvnSettingsXML}", variable: 'MAVEN_SETTINGS')]) {
                if(common.isPR(sonarBranch)) {
                    cmd.execute("""mvn org.sonarsource.scanner.maven:sonar-maven-plugin:${SONAR_PLUGIN_VERSION}:sonar -s $MAVEN_SETTINGS \
                -Dsonar.pullrequest.key=${env.CHANGE_ID} \
                -Dsonar.pullrequest.branch=${env.CHANGE_BRANCH} \
                -Dsonar.pullrequest.base=${env.CHANGE_TARGET} \
                -Dsonar.pullrequest.github.repository=${propertyInfo.repoOrg}/${propertyInfo.repoName}""")
                } else {
                    cmd.execute("""mvn org.sonarsource.scanner.maven:sonar-maven-plugin:${SONAR_PLUGIN_VERSION}:sonar -Dsonar.branch.name=${sonarBranch} -s $MAVEN_SETTINGS""")
                }
            }
        }

        timeout(time: propertyInfo.sonarTimeout as Integer, unit: 'SECONDS') {
            checkSonarStatus(propertyInfo)
            waitForQualityGate abortPipeline: true
        }
    } catch (Exception ex) {
        log.message(messageNumber: 'ERROR0016', messageTitle: "Problem running sonarQube}")
        println 'Exception message:\n' +ex.getMessage()
        status.setStage(common.JEKINS_STATUS.UNSTABLE)
        status.setJob(common.JEKINS_STATUS.FAILURE, propertyInfo.sonarFailOnError)
    }

    log.debugMessage ("<---- Leaving method", common.getCuurentMethodName())
}

def runSonar(Map propertyInfo) {
    log.debugMessage ("<---- Entering method", common.getCuurentMethodName())

    def sonarBranch = (propertyInfo.appVersion.matches("^.*[a-zA-Z].*")) ? "${env.BRANCH_NAME}" : "${propertyInfo.releaseBranchPrefix}${propertyInfo.appVersion}"
    def sonarRunnerInstallDir = '/home/jenkins/agent/tools/hudson.plugins.sonar.SonarRunnerInstallation'
    log.debugMessage ("Sonar Branch : ${sonarBranch}" as String)
    log.debugMessage ("SonarQube installtion : ${propertyInfo.sonarQubeServer}")

    println "Checking for existing Sonar scanner instances ..."
    if(fileExists(sonarRunnerInstallDir)) {
        println "Cleaning up existing sonar scanner instances ..."
        cmd.execute("rm -rf ${sonarRunnerInstallDir}")
    }

    try {
        println "Installing sonar scanner '${propertyInfo.sonarQubeScanner}'..."
        def sonarQubeScanner = tool "${propertyInfo.sonarQubeScanner}"
    } catch (Exception ex) {
        log.message(messageNumber: 'WARNING0008', messageTitle: "Problem installing Scanner ${propertyInfo.sonarQubeScanner}")
        println 'Exception message:\n' +ex.getMessage()
        status.setStage(common.JEKINS_STATUS.UNSTABLE)
        return
    }

    def sourceCertFile = '/home/jenkins/agent/cacerts'
    def targetCertFile = cmd.getOutput("set +x && find ${sonarRunnerInstallDir} -name cacerts")
    def sonarQubeScannerCmd = cmd.getOutput("set +x && find ${sonarRunnerInstallDir} -name sonar-scanner")
    println "Origin cacerts file location: ${sourceCertFile}\nTarget cacerts file : ${targetCertFile}\n runner app: ${sonarQubeScannerCmd}"

    if(fileExists(sourceCertFile)) {
        try {
            withSonarQubeEnv(propertyInfo.sonarQubeServer) {
                cmd.execute"cp ${sourceCertFile} ${targetCertFile}"

                cmd.execute"echo '\\n ${fortify.FORTIFY_UPLOAD_DIRECTORY}/**' >> .gitignore"

                if(common.isPR(env.BRANCH_NAME)) {
                    cmd.execute"""${sonarQubeScannerCmd} \
                -Dsonar.pullrequest.key=${env.CHANGE_ID} \
                -Dsonar.pullrequest.branch=${env.CHANGE_BRANCH} \
                -Dsonar.pullrequest.base=${env.CHANGE_TARGET} \
                -Dsonar.pullrequest.github.repository=${propertyInfo.repoOrg}/${propertyInfo.repoName} \
                -Dproject.settings=./sonar-project.properties
                """
                } else {
                    cmd.execute"""${sonarQubeScannerCmd} -Dsonar.projectVersion=${propertyInfo.appVersion} -Dsonar.projectKey=${propertyInfo.sonarProjectKey} -Dsonar.branch.name=${sonarBranch} -Dporject.settings=${propertyInfo.sonarPropertiesPath}"""
                }
            }

            sleep("${propertyInfo.enableSonarSleep}")
            timeout (time: propertyInfo.sonarTimeout as Integer, unit: 'SECONDS') {
                waitForQualityGate abortPipeline: true
            }
        } catch (Exception ex) {
            log.message(messageNumber: 'ERROR0022', messageTitle: "Problem running scanner ${propertyInfo.sonarQubeScanner}")
            println 'Exception message:\n' +ex.getMessage()
            status.setStage(common.JEKINS_STATUS.UNSTABLE)
            status.setJob(common.JEKINS_STATUS.FAILURE, propertyInfo.sonarFailOnError)
        }
    } else {
        log.message(messageNumber: 'WARNING0008', messageTitle: "Problem Installing scanner ${propertyInfo.sonarQubeScanner}.Cacerts file is missing not found")
        status.setStage(common.JEKINS_STATUS.UNSTABLE)
    }

    log.debugMessage ("<---- Leaving method", common.getCuurentMethodName())
}

def validateSonarProperties(Map propertyInfo) {
    if(propertyInfo.enableSonar.toBoolean()) {
        if(fileExists("${propertyInfo.sonarPropertiesPath}")) {
            def sonarProperties = readProperties file: propertyInfo.sonarPropertiesPath
            propertyInfo.sonarProjectKey = (sonarProperties."sonar.projectKey" == null ||
            sonarProperties."sonar.projectKey" == '') ? propertyInfo.appName : sonarProperties."sonar.projectKey"
        } else {
            log.message(messageNumber: 'ERROR0023', messageTitle: "No sonar-project.properties found")
            status.setStage(common.JEKINS_STATUS.FAILURE)
            status.setJob(common.JEKINS_STATUS.FAILURE)
        }
    }
}

return this