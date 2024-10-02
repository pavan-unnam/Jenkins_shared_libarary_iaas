package com.unnam.libs

import groovy.transform.Field
import jenkins.model.GlobalConfiguration
import org.jenkinsci.plugins.fodupload.FodGlobalDescriptor

@Field
def cmd = new cmd()

@Field
def log = new log()

@Field
def status = new status()

@Field
def image = new image()


def getBaseEnvInfo(Map propertyInfo) {
    log.debugMessage ("<---- Entering method", common.getCuurentMethodName())

    def envInfo = ''
    def envList = ''

    if(isUnix()) {
        envInfo = sh (script: "env || true", returnStdout: true)
    } else {
        envInfo = bat (script :"set || true", returnStdout: true)
    }

    envList = readProperties text: envInfo
    propertyInfo << envList

    log.debugMessage ("<---- leaving method", common.getCuurentMethodName())
}

def getSetGoogleChatOptions(Map propertyInfo) {
    propertyInfo.gChatRoomJenkinsId = (propertyInfo.gChatRoomJenkinsId == null || propertyInfo.gChatRoomJenkinsId == '') ? '' : propertyInfo.gChatRoomJenkinsId;
    propertyInfo.gChatMessage = (propertyInfo.gChatMessage == null || propertyInfo.gChatMessage == '') ? 'BUILD NOTIFICATION:Job - ${BUILD_URL}, status - ${BUILD_STATUS}': propertyInfo.gChatMessage;
    propertyInfo.gChatNotifyAborted = (propertyInfo.gChatNotifyAborted == null || propertyInfo.gChatNotifyAborted == '') ? false : propertyInfo.gChatNotifyAborted;
    propertyInfo.gChatNotifyfailure = (propertyInfo.gChatNotifyfailure == null || propertyInfo.gChatNotifyfailure == '') ? false : propertyInfo.gChatNotifyfailure;
    propertyInfo.gChatNotifyNotBuilt = (propertyInfo.gChatNotifyNotBuilt == null || propertyInfo.gChatNotifyNotBuilt == '') ? false : propertyInfo.gChatNotifyNotBuilt;
    propertyInfo.gChatNotifySuccess = (propertyInfo.gChatNotifySuccess == null || propertyInfo.gChatNotifySuccess == '') ? false : propertyInfo.gChatNotifySuccess;
    propertyInfo.gChatNotifyUnstable = (propertyInfo.gChatNotifyUnstable == null || propertyInfo.gChatNotifyUnstable == '') ? false : propertyInfo.gChatNotifyUnstable;
    propertyInfo.gChatNotifyBackToNormal = (propertyInfo.gChatNotifyBackToNormal == null || propertyInfo.gChatNotifyBackToNormal == '') ? false : propertyInfo.gChatNotifyBackToNormal;
    propertyInfo.gChatSuppressInfoLoggers = (propertyInfo.gChatSuppressInfoLoggers == null || propertyInfo.gChatSuppressInfoLoggers == '') ? false : propertyInfo.gChatSuppressInfoLoggers;
    propertyInfo.gChatSameThreadNotification = (propertyInfo.gChatSameThreadNotification == null || propertyInfo.gChatSameThreadNotification == '') ? false : propertyInfo.gChatSameThreadNotification;
}

def getSetBuildOptions(Map propertyInfo) {
    log.debugMessage ("<---- Entering method", common.getCuurentMethodName())

    propertyInfo.nexusRepoServerName = (propertyInfo.nexusRepoServerName == null || propertyInfo.nexusRepoServerName == '') ?"nrm.us.equifax.com" : propertyInfo.nexusRepoServerName
    propertyInfo.yumReleaseRepo = (propertyInfo.yumReleaseRepo == null || propertyInfo.yumReleaseRepo == '') ? env.yumReleaseRepo : propertyInfo.yumReleaseRepo
    propertyInfo.yumSnapshotRepo = (propertyInfo.yumSnapshotRepo == null || propertyInfo.yumSnapshotRepo == '') ? env.yumSnapshotRepo : propertyInfo.yumSnapshotRepo
    propertyInfo.dockerFileDirectory = (propertyInfo.dockerFileDirectory == null || propertyInfo.dockerFileDirectory == '') ? '.' : propertyInfo.dockerFileDirectory
    propertyInfo.appServiceAccount = (propertyInfo.appServiceAccount == null || propertyInfo.appServiceAccount == '') ? "appSvc" : propertyInfo.appServiceAccount
    propertyInfo.useBranchNameInAppVersion = (propertyInfo.useBranchNameInAppVersion == null || propertyInfo.useBranchNameInAppVersion == '') ? "true" : propertyInfo.useBranchNameInAppVersion
    propertyInfo.useBranchNameInReleaseVersion = (propertyInfo.useBranchNameInReleaseVersion == null || propertyInfo.useBranchNameInReleaseVersion == '') ? "false" : propertyInfo.useBranchNameInReleaseVersion
    propertyInfo.nexusIQ = propertyInfo.nexusIQ
    propertyInfo.nexusIqSvcAccount = (propertyInfo.nexusIqSvcAccount == null || propertyInfo.nexusIqSvcAccount == '') ? "defaultServiceAccount" : propertyInfo.nexusIqSvcAccount
    propertyInfo.nexusIQScanPatterns = (propertyInfo.nexusIQScanPatterns == null || propertyInfo.nexusIQScanPatterns == '') ? "**/*.jar, **/*.war. **/*.ear, **/*.tar.gz, **/*.zip, **/*.exe" : propertyInfo.nexusIQScanPatterns
    propertyInfo.nexusIQModuleExcludes = (propertyInfo.nexusIQModuleExcludes == null || propertyInfo.nexusIQModuleExcludes == '') ? "**/*.mvn/**, **/.m2**" : propertyInfo.nexusIQModuleExcludes
    propertyInfo.releaseBranchPrefix = (propertyInfo.releaseBranchPrefix == null || propertyInfo.releaseBranchPrefix == '') ? "" : propertyInfo.releaseBranchPrefix
    propertyInfo.releaseBranch = (propertyInfo.releaseBranch == null || propertyInfo.releaseBranch == '') ? "master" : propertyInfo.releaseBranch
    propertyInfo.devIntegrationBranch = (propertyInfo.devIntegrationBranch == null || propertyInfo.devIntegrationBranch == '') ? "develop" : propertyInfo.devIntegrationBranch
    propertyInfo.customAppVersion = (propertyInfo.customAppVersion == null || propertyInfo.customAppVersion == '') ? "" : propertyInfo.customAppVersion
    propertyInfo.releaseType = 'patch'
    propertyInfo.enableParallelTestCmd = (propertyInfo.enableParallelTestCmd == null || propertyInfo.enableParallelTestCmd == '') ? "false" : propertyInfo.enableParallelTestCmd

    if(propertyInfo.enableParallelTestCmd.toBoolean()) {
        propertyInfo.stashName = "sources"
        propertyInfo.stashInclude = (propertyInfo.stashInclude == null || propertyInfo.stashInclude == '') ? "**" : propertyInfo.stashInclude
        propertyInfo.stashExclude = (propertyInfo.stashExclude == null || propertyInfo.stashExclude == '') ? "**/.git, **/.git/**" : propertyInfo.stashExclude
        stash   name: propertyInfo.stashName,
                includes: propertyInfo.stashInclude,
                excludes: propertyInfo.stashExclude
    }

    propertyInfo.deployTargetfromFile = (propertyInfo.deployTargets == null || propertyInfo.deployTargets == '') ? "" : propertyInfo.deployTargets
    propertyInfo.deployTargets = (propertyInfo.deployTargets == null || propertyInfo.deployTargets == '') ? propertyInfo.deployTargetfromFile : propertyInfo.deployTargets
    propertyInfo.enableSonar = (propertyInfo.enableSonar == null || propertyInfo.enableSonar == '') ? "true" : propertyInfo.enableSonar
    propertyInfo.enableNexusIQ = (propertyInfo.enableNexusIQ == null || propertyInfo.enableNexusIQ == '') ? "true" : propertyInfo.enableNexusIQ
    propertyInfo.enableSonarSleep = (propertyInfo.enableSonarSleep == null || propertyInfo.enableSonarSleep == '') ? "30" : propertyInfo.enableSonarSleep
    propertyInfo.sonarTimeout = (propertyInfo.sonarTimeout == null || propertyInfo.sonarTimeout == '') ? "300" : propertyInfo.sonarTimeout
    propertyInfo.sonarStatusSleep = (propertyInfo.sonarStatusSleep == null || propertyInfo.sonarStatusSleep == '') ? "30" : propertyInfo.sonarStatusSleep
    propertyInfo.sonarQubeServer = (propertyInfo.sonarQubeServer == null || propertyInfo.sonarQubeServer == '') ? "sonarQube" : propertyInfo.sonarQubeServer

    propertyInfo.sonarPropetiesPath = (propertyInfo.sonarPropetiesPath == null || propertyInfo.sonarPropetiesPath == '') ? "sonar-project.properties" : propertyInfo.sonarPropetiesPath

    propertyInfo.kanikoTimeout = (propertyInfo.kanikoTimeout == null || propertyInfo.kanikoTimeout == '') ? "120" : propertyInfo.kanikoTimeout
    propertyInfo.kanikoOptions = (propertyInfo.kanikoOptions == null || propertyInfo.kanikoOptions == '') ? '' : propertyInfo.kanikoOptions

    propertyInfo.build_agent_label = (propertyInfo.build_agent_label == null || propertyInfo.build_agent_label == '') ? "build-pod" : propertyInfo.build_agent_label
    propertyInfo.build_agent_cloud = (propertyInfo.build_agent_cloud == null || propertyInfo.build_agent_cloud == '') ? "kubernetes-non-prod" : propertyInfo.build_agent_cloud

    propertyInfo.buildContainerName = (propertyInfo.buildContainerName == null || propertyInfo.buildContainerName == '') ? "notSet" : propertyInfo.buildContainerName
    propertyInfo.createImage = (propertyInfo.createImage == null || propertyInfo.createImage == '') ? "true" : propertyInfo.createImage
    propertyInfo.useGoogleArtifactRegistry = (propertyInfo.useGoogleArtifactRegistry ? "true": "false").toBoolean()
    propertyInfo.garHostname = propertyInfo.garHostname ? propertyInfo.garHostname: "us-docker.pkg.dev"
    propertyInfo.centralGarPrefix = image.getCentralGARPrefix(propertyInfo.centralGarPrefix)

    propertyInfo.centralGcrSubPrefix = (propertyInfo.centralGcrSubPrefix == null || propertyInfo.centralGcrSubPrefix == '') ? '' : propertyInfo.centralGcrSubPrefix
    propertyInfo.centralGcrPrefix = (propertyInfo.centralGcrPrefix == null || propertyInfo.centralGcrPrefix == '') ? common.getCentralGcrPrefix(propertyInfo.centralGcrPrefix) : propertyInfo.centralGcrPrefix
    propertyInfo.gcrHostname = (propertyInfo.gcrHostname == null || propertyInfo.gcrHostname == '') ? 'us.gcr.io' : propertyInfo.gcrHostname
    propertyInfo.enableImageAttestation = (propertyInfo.enableImageAttestation == null || propertyInfo.enableImageAttestation == '') ? "false" : propertyInfo.enableImageAttestation
    propertyInfo.attestorProjectId = (propertyInfo.attestorProjectId == null || propertyInfo.attestorProjectId == '') ? env.attestorProjectId : propertyInfo.attestorProjectId
    propertyInfo.gcrAttestor = (propertyInfo.gcrAttestor == null || propertyInfo.gcrAttestor == '') ? env.gcrAttestor : propertyInfo.gcrAttestor
    propertyInfo.secProject = (propertyInfo.secProject == null || propertyInfo.secProject == '') ? env.secProject : propertyInfo.secProject
    propertyInfo.region = (propertyInfo.region == null || propertyInfo.region == '') ? 'us-east1' : propertyInfo.region
    propertyInfo.kmsKeyring = (propertyInfo.kmsKeyring == null || propertyInfo.kmsKeyring == '') ? env.kmsKeyring : propertyInfo.kmsKeyring
    propertyInfo.gcrAttestorkey = (propertyInfo.gcrAttestorkey == null || propertyInfo.gcrAttestorkey == '') ? env.gcrAttestorkey : propertyInfo.gcrAttestorkey

    getSetGoogleChatOptions(propertyInfo)

    propertyInfo.enableFortify = (propertyInfo.enableFortify == null || propertyInfo.enableFortify == '') ? "true" : propertyInfo.enableFortify
    propertyInfo.fortifyAppId = (propertyInfo.fortifyAppId == null || propertyInfo.fortifyAppId == '') ? null : propertyInfo.fortifyAppId
    propertyInfo.fortifyJiraProject = propertyInfo.fortifyJiraProject
    propertyInfo.fortifyJiraCredentials = (propertyInfo.fortifyJiraCredentials == null || propertyInfo.fortifyJiraCredentials == '') ? "defaultFortifyJiraUser" : propertyInfo.fortifyJiraCredentials
    propertyInfo.fortifyDevBaseRelease = (propertyInfo.fortifyDevBaseRelease == null || propertyInfo.fortifyDevBaseRelease == '') ? "master" : propertyInfo.fortifyDevBaseRelease
    propertyInfo.fortifyCron = (propertyInfo.fortifyCron == null || propertyInfo.fortifyCron == '') ? '0 0 31 2 *' : propertyInfo.fortifyCron
    propertyInfo.fortifyReleaseName = propertyInfo.fortifyDevBaseRelease
    propertyInfo.fortifyEnableBugtrackerJob= (propertyInfo.fortifyEnableBugtrackerJob == null || propertyInfo.fortfortifyEnableBugtrackerJobifyCron == '') ? "true" : propertyInfo.fortifyEnableBugtrackerJob
    propertyInfo.fortifyBugtrackerJob= (propertyInfo.fortifyBugtrackerJob == null || propertyInfo.fortifyBugtrackerJob == '') ? '/fortifyBugtracker' : propertyInfo.fortifyBugtrackerJob
    propertyInfo.fortifySrcInclude= (propertyInfo.fortifySrcInclude == null || propertyInfo.fortifySrcInclude == '') ? '**/*' : propertyInfo.fortifySrcInclude.trim()
    propertyInfo.fortifySrcExclude= (propertyInfo.fortifySrcExclude == null || propertyInfo.fortifySrcExclude == '') ? '' : propertyInfo.fortifySrcExclude.trim()
    propertyInfo.fortifyMicroserviceName= (propertyInfo.fortifyMicroserviceName == null || propertyInfo.fortifyMicroserviceName == '') ? '' : propertyInfo.fortifyMicroserviceName
    propertyInfo.fortifyCopyStateWait= (propertyInfo.fortifyCopyStateWait == null || propertyInfo.fortifyCopyStateWait == '') ? '180' : propertyInfo.fortifyCopyStateWait
    propertyInfo.fortifySdlcStatusType= (propertyInfo.fortifySdlcStatusType == null || propertyInfo.fortifySdlcStatusType == '') ? 'production' : propertyInfo.fortifySdlcStatusType
    propertyInfo.fortifyClientId= GlobalConfiguration.all().get(FodGlobalDescriptor.class).clientId
    propertyInfo.fortifyClientSecretId= GlobalConfiguration.all().get(FodGlobalDescriptor.class).clientSecret

    propertyInfo.fortifyFailOnError= (propertyInfo.fortifyFailOnError == null || propertyInfo.fortifyFailOnError == '') ? "true" : propertyInfo.fortifyFailOnError
    propertyInfo.sonarFailOnError= (propertyInfo.sonarFailOnError == null || propertyInfo.sonarFailOnError == '') ? "true" : propertyInfo.sonarFailOnError
    propertyInfo.twistlockFailOnError= (propertyInfo.twistlockFailOnError == null || propertyInfo.twistlockFailOnError == '') ? "true" : propertyInfo.twistlockFailOnError

    propertyInfo.runJobs= (propertyInfo.runJobs == null || propertyInfo.runJobs == '') ? "false" : propertyInfo.runJobs
    propertyInfo.runJobsFromBranch= (propertyInfo.runJobsFromBranch == null || propertyInfo.runJobsFromBranch == '') ? '' : propertyInfo.runJobsFromBranch

    propertyInfo.enableTwistlock= (propertyInfo.enableTwistlock == null || propertyInfo.enableTwistlock == '') ? "true" : propertyInfo.enableTwistlock
    propertyInfo.enableTwistlockResultAnalysis= (propertyInfo.enableTwistlockResultAnalysis == null || propertyInfo.enableTwistlockResultAnalysis == '') ? "false" : propertyInfo.enableTwistlockResultAnalysis
    propertyInfo.twistlockCredId= propertyInfo?.twistlockCredId?.trim() ?: 'defaultTwistlockServiceAccount'
    propertyInfo.dockerTimeout= propertyInfo?.dockerTimeout?.trim() ?: propertyInfo.kanikoTimeout
    propertyInfo.dockerOptions= propertyInfo?.dockerOptions?.trim() ?: propertyInfo.kanikoOptions
    propertyInfo.twistlockCriticalThreshold= propertyInfo?.twistlockCriticalThreshold?.trim() ?: '0'
    propertyInfo.twistlockHighThreshold= propertyInfo?.twistlockHighThreshold?.trim() ?: '0'
    propertyInfo.twistlockMediumThreshold= propertyInfo?.twistlockMediumThreshold?.trim() ?: '0'
    propertyInfo.twistlockLowThreshold= propertyInfo?.twistlockLowThreshold?.trim() ?: '0'

    propertyInfo.jacocoGoal= 'org.jacoco:jacoco-maven-plugin:report'
    propertyInfo.cicdTag= propertyInfo.cicdTag?.trim() ?: 'cicd-0.0.6'
    propertyInfo.jiraCredentialId= propertyInfo.jiraCredentialId?.trim() ?: 'defaultJiraApiToken'
    propertyInfo.cicd= propertyInfo.efxPortableCicdLibarary?.toBoolean() ? cicd.getEfxCicdPortableLib(propertyInfo) : false

    propertyInfo.libraryURL= (propertyInfo.libraryURL == null || propertyInfo.libraryURL == '') ? '' : propertyInfo.libraryURL
    propertyInfo.libraryVersion= (propertyInfo.libraryVersion == null || propertyInfo.libraryVersion == '') ? '' : propertyInfo.libraryVersion
    propertyInfo.libraryCommitID= (propertyInfo.libraryCommitID == null || propertyInfo.libraryCommitID == '') ? '' : propertyInfo.libraryCommitID

    propertyInfo.enableFortifyWaitForResults= (propertyInfo.enableFortifyWaitForResults == null || propertyInfo.enableFortifyWaitForResults == '') ? "false" : propertyInfo.enableFortifyWaitForResults
    propertyInfo.fortifyTimeout= (propertyInfo.fortifyTimeout == null || propertyInfo.fortifyTimeout == '') ? '30': propertyInfo.fortifyTimeout
    propertyInfo.fortifyCriticalThreshold= (propertyInfo.fortifyCriticalThreshold == null || propertyInfo.fortifyCriticalThreshold == '') ? '0': propertyInfo.fortifyCriticalThreshold
    propertyInfo.fortifyHighThreshold= (propertyInfo.fortifyHighThreshold == null || propertyInfo.fortifyHighThreshold == '') ? '0': propertyInfo.fortifyHighThreshold
    propertyInfo.fortifyMediumThreshold= (propertyInfo.fortifyMediumThreshold == null || propertyInfo.fortifyMediumThreshold == '') ? '0': propertyInfo.fortifyMediumThreshold
    propertyInfo.fortifyLowThreshold= (propertyInfo.fortifyLowThreshold == null || propertyInfo.fortifyLowThreshold == '') ? '0': propertyInfo.fortifyLowThreshold
    propertyInfo.nexusIQFailOnError= (propertyInfo.nexusIQFailOnError == null || propertyInfo.nexusIQFailOnError == '') ? "": propertyInfo.nexusIQFailOnError
    propertyInfo.tagCreated= 'false'
    propertyInfo.packApplicationGroup= propertyInfo.packApplicationGroup?.trim() ? propertyInfo.packApplicationGroup : ''
    propertyInfo.enablePackAutomation= propertyInfo.enablePackAutomation? propertyInfo.enablePackAutomation?.toBoolean() : false
    propertyInfo.packAutomationFailOnError= propertyInfo.packAutomationFailOnError? propertyInfo.packAutomationFailOnError?.toBoolean() : true

    common.trimMap(propertyInfo)

    log.debugMessage ("<---- leaving method", common.getCuurentMethodName())
}

def setDefault(Map propertyInfo, String propertyName, String propertyValue) {
    log.debugMessage ("<---- entering method", common.getCuurentMethodName())

    foundKey = false
    propertyInfo.each {key, value ->
        if(key.contains(propertyName)) foundKey = true
    }

    if(!foundKey) {
        println "No '${propertyName}' parameter found, adding default value '${propertyValue}'"
        propertyInfo["${propertyName}"] = "${propertyValue}"
    }

    log.debugMessage ("<---- leaving method", common.getCuurentMethodName())
}

Map buildPropertyInfo (Map propertyInfo, String buildOptionsFile) {
    Map tmpMap =[:]
    Map envMap =[:]
    Map buildOptionsMap = [:]
    Map scmVariables = checkout scm

    log.debugMessage ("<---- entering method", common.getCuurentMethodName())

    envInfo = (isUnix() ) ? sh (script: "env || true", returnStdout: true) : bat (script: "set || true", returnStdout: true)
    envMap << readProperties( text: envInfo)

    if(fileExists(buildOptionsFile)) {
        buildOptionsMap << readProperties(file: buildOptionsFile)
    } else {
        log.message (messageNumber: 'ERROR0060', messageTitle: "Build_options file ${buildOptionsFile} does not exitst")
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE)
    }

    if(params.debug) {
        showPropsFound(envMap, "\n--- Environment variables ---", "\n")
        showPropsFound(scmVariables, "\n--- SCM variables ---", "\n")
        showPropsFound(buildOptionsMap, "\n--- build_options properties ---", "\n")
        showPropsFound(propertyInfo, "\n--- Jenkinsfile properties ---", "\n")
        showPropsFound(params, "\n--- Build Job parameters ---", "\n")
    }

    tmpMap << envMap
    tmpMap << scmVariables
    tmpMap << buildOptionsMap
    tmpMap << propertyInfo
    tmpMap << params

    log.debugMessage ("checking createRelease")
    tmpMap.createRelease = setBuildJobFlag(envMap.createRelease.toString(), buildOptionsMap.runJobs.toString(), propertyInfo.runJobs.toString(), params.runJobs)

    common.trimMap(tmpMap)

    log.debugMessage ("<---- leaving method", common.getCuurentMethodName())
    return tmpMap
}

def showPropsFound(Map propMap, String startString, String endString) {
    String content = startString+ '\n' + propMap.collect {entry-> entry.key + "=" + entry.value}.join('\n') + '\n' +endString
    println content
}

String setBuildJobFlag(String envVariable, String buildOptionsFlag, String jenkinsFileFlag, Boolean jobFlag) {
    log.debugMessage ("<---- entering method", common.getCuurentMethodName())
    StringBuilder msgString = new StringBuilder("Values passed in:\nEnvironment Variable : ${envVariable}\nBuild Options value : ${buildOptionsFlag}\nJenkinsFile value: ${jenkinsFileFlag}\nJob value:${jobFlag}")
    Boolean flagVal = (envVariable == null) ? false : envVariable.toBoolean()
    flagVal = flagVal || ((buildOptionsFlag == null) ? false : buildOptionsFlag.toBoolean())
    flagVal = flagVal || ((jenkinsFileFlag == null) ? false : jenkinsFileFlag.toBoolean())
    flagVal = flagVal || jobFlag

    msgString.append("\nFinal, determined value: ${flagVal}\n\n")
    log.debugMessage (msgString.toString())
    log.debugMessage ("<---- Leaving method", common.getCuurentMethodName())
    return flagVal.toString()
}

return this

