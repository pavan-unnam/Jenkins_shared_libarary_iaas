package com.unnam.libs

import groovy.text.GStringTemplateEngine
import groovy.transform.Field
import org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException

import java.time.ZonedDateTime

@Field
def cmd = new cmd()

@Field
def log = new log()

@Field
def status = new status()

@Field
def CENTRAL_GCR = 'iaas-gcr-reg-prd-ad3d'

@Field
def CENTRAL_GAR = 'corpsvc-cicd-poc2-dev-npe-1d6b'

@Field
def DOCKER_BASE_IMAGE_URI = ''

@Field
def DOCKER_IN_DOCKER_CONTAINER_NAME = 'docker-in-docker'

@Field
def TWISTLOCK_SCAN_RESULTS_FILE = 'twistlock-scan-results.json'



def build(Map pInfo) {
    log.debugMessage ("<---- Entering method", common.getCuurentMethodName())

    String containerRegistryHostName = pInfo.gcrHostName
    String containerRegistryProjectId = CENTRAL_GCR
    String containerRegistryPrefIx = pInfo.centralGcrPrefix
    String containerRegistryAuthCommand = "docker-credential-gcr configure-docker"

    if(pInfo.useGoogleArtifactRegistry) {
        containerRegistryHostName = pInfo.garHostName
        containerRegistryProjectId = CENTRAL_GAR
        containerRegistryPrefIx = pInfo.centralGarPrefix
        containerRepository = "${pInfo.garHostName}/${CENTRAL_GAR}"
        containerRegistryAuthCommand = "${containerRegistryAuthCommand} --registries=asia.gcr.io,eu.gcr.io,us.gcr.io,${containerRegistryHostName}"
    }

    pInfo.imageAppName = "${containerRegistryHostName}/${containerRegistryProjectId}${containerRegistryPrefIx}/${pInfo.appName}"
    pInfo.imageName = "${pInfo.imageAppName} : ${pInfo.appVersion}"
    FORTIFY_LABEL = (pInfo.fortifyRelelaseId) ? "--label \"FORTIFY_APP_ID =${pInfo.fortifyAppId}\" --label \"FORTIFY_RELEASE_ID =${pInfo.fortifyReleaseId}\"" : "--label \" FORTIFY_APP_ID=HOME\" --label \"FORTIFY_RELEASE_ID=HOME\""
    cuurentDateTime = ZonedDateTime.now()
    PROVENANCE_LABEL =
            "--label \"com.unnam.cicd.git.commits =${GIT_COMMIT}\" "+
                    "--label \"com.unnam.cicd.git.url =${GIT_URL}\" "+
                    "--label \"com.unnam.cicd.git.branch =${GIT_BRANCH}\" "+
                    "--label \"com.unnam.cicd.jenkins.build-numbers =${BUILD_NUMBER}\" "+
                    "--label \"com.unnam.cicd.jenkins.build-url =${BUILD_URL}\" "+
                    "--label \"com.unnam.cicd.pipeline-version=${pInfo.libraryVersion}\" "+
                    "--label \"com.unnam.cicd.created =${cuurentDateTime}\""

    if(pInfo.dockerOptions > '') {
        HashMap bindingMap = new HashMap()
        bindingMap.put('propertyInfo', pInfo)
        bindingMap.put('env', env)
        bindingMap.putAll( binding.variables)
        pInfo.dockerOptions = new GStringTemplateEngine().createTemplate(pInfo.dockerOptions).make(bindingMap).toString()
    }

    pInfo.dockerBuildCmd = "docker build ${pInfo.defaultDockerOptions} "+
            "${pInfo.dockerOptions} ${FORTIFY_LABEL} ${PROVENANCE_LABEL} -t ${pInfo.imageName} -f Dockerfile ."

    try{
        timeout (time: pInfo.dockerTimeout, unit: "SECONDS") {
            cmd.execute(containerRegistryAuthCommand)
            cmd.execute(pInfo.dockerBuildCmd)
        }
    } catch(Exception ex) {
        if(ex.getMessage().contains(DOCKER_IN_DOCKER_CONTAINER_NAME)) {
            log.message(messageNumber: 'ERROR0018', messageTitle: "Problem building Docker image")
            println 'Exception message:\n' +ex.getMessage()
            status.setStage(common.JEKINS_STATUS.UNSTABLE)
            status.setJob(common.JEKINS_STATUS.FAILURE, pInfo.twistlockFailOnError)
        } else {
            log.message(messageNumber: 'ERROR0018', messageTitle: "Problem building Docker image")
            println 'Exception message:\n' +ex.getMessage()
            status.setStage(common.JEKINS_STATUS.FAILURE)
            status.setJob(common.JEKINS_STATUS.FAILURE)
        }
    } catch(Exception ex) {
            log.message(messageNumber: 'ERROR0018', messageTitle: "Problem building Docker image")
            println 'Exception message:\n' +ex.getMessage()
            status.setStage(common.JEKINS_STATUS.FAILURE)
            status.setJob(common.JEKINS_STATUS.FAILURE)
    }

    log.debugMessage ("<---- Leaving method", common.getCuurentMethodName())
}

def checkDockerFile () {
    log.debugMessage ("<---- Entering method", common.getCuurentMethodName())

    def dockerFileStr = ''
    echo "Checking use of central GCR in Dockerfile ..."

    try {
        dockerFileStr = readFile('Dockerfile')
    } catch(Exception ex) {
        log.message(messageNumber: 'ERROR0020', messageTitle: "Failed to read Dockerfile, please check if Dockerfile exists or build_options/dockerFileDirectory parameter is set correctly")
        println 'Exception message:\n' +ex.getMessage()
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE)
    }
    lines = dockerFileStr.readLines()
    for(line in lines) {
        if((line.matches("^.*FROM.*")) && ( !line.matches("^.*#.*FROM.*"))) {
            if((line.matches(".*/${CENTRAL_GCR}/.*")) || line.matches(".*/${CENTRAL_GAR}/.*")) {
                println 'Found valid FROM statement ' + line
                DOCKER_BASE_IMAGE_URI = ((String)line.split(" ")[1])
                println "DOCKER_BASE_IMAGE_URI ${ DOCKER_BASE_IMAGE_URI}"
            } else {
                log.message(messageNumber: 'ERROR0021', messageTitle: "Problem with Dockerfile 'FROM' statement - '${line}'")
                status.setStage(common.JEKINS_STATUS.FAILURE)
                status.setJob(common.JEKINS_STATUS.FAILURE)
                break
            }
        }
    }
    log.debugMessage ("<---- Leaving method", common.getCuurentMethodName())
}

Boolean getTwistlockCLI (Map pInfo) {
    log.debugMessage ("<---- Entering method", common.getCuurentMethodName())
    Boolean retVal = false
    try {
        log.debugMessage ("Trying httpRequest with credential ${pInfo.twistlockCredId}")
        def r = httpRequest url : "https://"+common.TL_CONSOLE + common.TL_API_URI,
                            authentication:  pInfo.twistlockCredId,
                            validResponseCodes: '100:399',
                            quiet: false,
                            timeout: 180,
                            outputFile: common.TL_CLI,
                            contentType: 'APPLICATION_TAR',
                            responseHandle: 'NONE'

        retVal = true
    } catch(IOException ex) {
        log.message(messageNumber: 'WARNING0007', messageTitle: "Failed to read Dockerfile, possible problem downloading the twistlock CLI")
        println 'Exception message:\n' +ex.printStackTrace()
        status.setStage(common.JEKINS_STATUS.UNSTABLE)
        status.setJob(common.JEKINS_STATUS.FAILURE, pInfo.twistlockFailOnError)
    } catch(Exception ex) {
        log.message(messageNumber: 'ERROR0019', messageTitle: "F problem downloading the twistlock CLI")
        println 'Exception message:\n' +ex.printStackTrace()
        status.setStage(common.JEKINS_STATUS.UNSTABLE)
        status.setJob(common.JEKINS_STATUS.FAILURE, pInfo.twistlockFailOnError)
    }

    println "Download Twistlock CLI to ${env.WORKSPACE}/${common.TL_CLI}"
    log.debugMessage ("<---- Leaving method", common.getCuurentMethodName())
    return retVal
}

def scan (Map pInfo) {
    log.debugMessage ("<---- Entering method", common.getCuurentMethodName())
    String failureMessage = ''
    try {
        withCredentials([usernamePassword(credentialsId: pInfo.twistlockCredId, passwordVariable: 'TL_PASS', usernameVariable: 'TL_USER')]) {
            ansiColor('xterm') {
                cmd.execute('chmod +x ' + common.TL_CLI)
                scanResult = cmd.getStatus('./' +common.TL_CLI + ' image scan --address https://' +common.TL_CONSOLE+' --user $TL_USER --password $TL_PASS --details --output-file '+TWISTLOCK_SCAN_RESULTS_FILE+' '+pInfo.imageName)
                cmd.execute("chmod 755 ${TWISTLOCK_SCAN_RESULTS_FILE}")
            }

            if(scanResult !=0) {
                failureMessage = "Problem scanning the image, twistcli exited with ${scanResult} exit code"
            } else {
                if(pInfo.enableTwistlockResultAnalysis?.toBoolean()) {
                    def scanResults = readJSON file: TWISTLOCK_SCAN_RESULTS_FILE, returnPojo: true
                    Map vulnerabilityDistribution = scanResults.results[0].vulnerabilityDistribution
                    List twistlockVulnerabilityServerityFailures =[]

                    if(vulnerabilityDistribution.critical > (pInfo.twistlockCriticalThreshold as Integer)) {
                        twistlockVulnerabilityServerityFailures.add("critical")
                    }

                    if(vulnerabilityDistribution.high > (pInfo.twistlockHighThreshold as Integer)) {
                        twistlockVulnerabilityServerityFailures.add("high")
                    }

                    if(vulnerabilityDistribution.medium > (pInfo.twistlockMediumThreshold as Integer)) {
                        twistlockVulnerabilityServerityFailures.add("medium")
                    }

                    if(vulnerabilityDistribution.low > (pInfo.twistlockLowThreshold as Integer)) {
                        twistlockVulnerabilityServerityFailures.add("low")
                    }

                    if(twistlockVulnerabilityServerityFailures) {
                        def failedThresholds = twistlockVulnerabilityServerityFailures.join(", ")
                        failureMessage = "Twistlock Scan results exceeded ${failedThresholds} threshold(s)."
                    }
                }
            }

        }
    } catch (CredentialNotFoundException ex) {
        log.message(messageNumber: 'ERROR0028', messageTitle: "Check for correct Jenkins credential ID")
        println 'Exception message:\n' +ex.printStackTrace()
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE, pInfo.twistlockFailOnError)
    }catch (Exception ex) {
        log.message(messageNumber: 'ERROR0029', messageTitle: "Problem scanning image")
        println 'Exception message:\n' +ex.getMessage()
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE, pInfo.twistlockFailOnError)
    }

    if(failureMessage) {
        log.message(messageNumber: 'ERROR0030', messageTitle: failureMessage)
        status.setStage(common.JEKINS_STATUS.UNSTABLE)
        status.setJob(common.JEKINS_STATUS.FAILURE, pInfo.twistlockFailOnError)
    }

    log.debugMessage ("<---- leaving method", common.getCuurentMethodName())
}


def push (String imageAppName) {
    log.debugMessage ("<---- Entering method", common.getCuurentMethodName())

    try {
        cmd.execute('docker push '+imageAppName + ' --all-tags')
    } catch (Exception ex) {
        log.message(messageNumber: 'ERROR0024', messageTitle: "Problem pushing image to central GCR")
        println 'Exception message:\n' +ex.getMessage()
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE)
    }

    log.debugMessage ("<---- leaving method", common.getCuurentMethodName())
}

def attest(Map pInfo) {
    log.debugMessage ("<---- Entering method", common.getCuurentMethodName())

    imageSha = cmd.getOutput("gcloud container images describe ${pInfo.gcrHostname}/${CENTRAL_GCR}/${pInfo.centralGcrPrefix}/${pInfo.appName}:${pInfo.appVersion} --format='value(image_summary.fully_qualified_digest)'")
    println "imageSha:${imageSha}"

    try{
        cmd.execute("gcloud alpha conatainer binauthz attestations sign-and-create \
            --project=${pInfo.attestorProjectId} \
            --artifact-url=${imageSha} \
            --attestor=${pInfo.gcrAttestor} \
            --attestor-project=${pInfo.attestorProjectId} \
            --keyversion-project=${pInfo.secProject} \
            --keyversion-loction=${pInfo.region} \
            --keyversion-keyring=${pInfo.kmskeyring} \
            --keyversion-key=${pInfo.gcrAttestorkey} \
            --keyversion=1 \
            --public-key-id-override=projects/${pInfo.secProject}/locations/${pInfo.region}/keyRings/${pInfo.kmskeyring}/cryptoKeys/${pInfo.gcrAttestorkey}")
    } catch (Exception ex) {
        common.logError("Problem running gcloud command", ex.getMessage())
    }
}

String getCentralGARPrefix(String centralGarPrefix) {
    log.debugMessage ("<---- Entering method", common.getCuurentMethodName())
    if(!centralGarPrefix) {
        String jenkinsInstance = env.JENKINS_URL.split("/")[-1]
        centralGarPrefix = jenkinsInstance.substring(0,jenkinsInstance.lastIndexOf("-"))
    }
    log.debugMessage ("<---- Leaving method", common.getCuurentMethodName())
    return centralGarPrefix
}

return this


