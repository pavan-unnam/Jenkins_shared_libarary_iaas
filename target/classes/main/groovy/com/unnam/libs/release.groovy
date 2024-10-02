package com.unnam.libs

import groovy.transform.Field


@Field
def cmd = new cmd()

@Field
def log = new log()

@Field
def status = new status()

boolean branchMatchesReleaseBranch( String releaseBranch) {
    boolean branchMatches = (releaseBranch.matches("${env.BRANCH_NAME}")) ? true : false

    if(!branchMatches) {
        log.message(messageNumber: 'ERROROOO3', messageTitle: "Release creation attempted from branch " + env.BRANCH_NAME
        + " . Release branch is set to ${releaseBranch}")
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE)
    }
    return branchMatches
}

int setGitConfig () {
    int state = 1
    try {
        state = cmd.getStatus("git config --global user.email 'Jenkins@ERS.equifax.com'") as int
        state = cmd.getStatus("git config --global user.name 'Jenkins'") as int
    } catch (Exception) {
        log.message(messageNumber: 'ERROROOO4', messageTitle: "Problem setting git configs for ${env.GIT_URL}")
        println "Exception message" + e.getMessage()
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE)
    }
    return state
}

void fetchGitTags(def githubAppCredentialId) {
    try {
        withCredentials([gitUsernamePassword(credentialsId: githubAppCredentialId)]) {
            cmd.execute("git fetch --prune --prune-tags --all --tags")
        }
    } catch (Exception e) {
        log.message(messageNumber: 'ERROROOO4', messageTitle: "Problem retrieving tag infromation from repo ${env.GIT_URL}")
        println "Exception message" + e.getMessage()
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE)
    }
}

    Boolean isReleaseTagPresent(Map propertyInfo) {
        def releaseTags = cmd.getOutput("git tag --sort=v:refname").split('\n')
        Boolean present = false

        if(releaseTags.contains(propertyInfo.releaseBranchPrefix+propertyInfo.appVersion)) {
            log.message(messageNumber: 'ERROROO25', messageTitle: "${propertyInfo.releaseBranchPrefix}${propertyInfo.appVersion}")
            status.setStage(common.JEKINS_STATUS.FAILURE)
            status.setJob(common.JEKINS_STATUS.FAILURE)
            present = true
        } else {
            println "creating Release tag ${propertyInfo.releaseBranchPrefix}${propertyInfo.appVersion}"
        }
        return present
    }

void createTag(Map propertyInfo, Map tagInfo) {
    log.debugMessage ("<---- Entering method", common.getCuurentMethodName())
    log.debugMessage ("Github credential used : ${propertyInfo.githubAppCredentialId}")

    def patchVersionNumber = ''
    fetchGitTags(propertyInfo.githubAppCredentialId)

    if(!propertyInfo.customAppVersion) {
        patchVersionNumber = getPatchVersionNumber(propertyInfo)
        propertyInfo.appVersion = "${tagInfo.versionNumber}.${patchVersionNumber}"
    }
    if(setGitConfig() ==0) {
        isReleaseTagPresent(propertyInfo)
        cmd.execute("git tag -a ${propertyInfo.releaseBranchPrefix}${propertyInfo.appVersion} " +
                "-m \"Auto : created release tag ${propertyInfo.releaseBranchPrefix}${propertyInfo.appVersion}\"")
        propertyInfo.tagCreated = 'true'
    } else{
        log.message(messageNumber: 'ERROR0004', messageTitle: "Problem setting git configs for ${env.GIT_URL}")
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE)
    }

    log.debugMessage ("<---- leaving method", common.getCuurentMethodName())
}

void pushTag(Map pInfo) {
    try {
        withCredentials([gitUsernamePassword(credentialsId: pInfo.githubAppCredentialId)]) {
            cmd.execute("git push --tags")
        }
    } catch (Exception e) {
        log.message(messageNumber: 'ERROROO26', messageTitle: "Problem pushing tag")
        println "Exception message" + e.getMessage()
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE)
    }
}

Map defineGitTagWildCardPattern(Map propertyInfo, def buildUtils) {
    log.debugMessage("<---- entering method", common.getCuurentMethodName())
    def matcherPattern = /^(?<versionNumbers>\d++(?:\.++\d++)*)(?<preReleaseTag>.*)$/,
            matcher = propertyInfo.appVersion =~ matcherPattern
    Map map = [:]
    if (matcher.matches()) {
        map = buildUtils.parseGitTagListForVersionAndPreReleaseTag(matcher)
        propertyInfo.gitTagWildCardPattern = "${propertyInfo.releaseBranchPrefix}${map.versionNumbers}.[0-9*"
        matcher = null

        log.debugMessage("appVersion : ${propertyInfo.appVersion}\nmatcherPattern: ${matcherPattern}" +
                "\nversionNumbers: ${map.versionNumbers}\npreReleaseTag: ${map.preReleaseTag}" +
                "\ngitTagWildCardPattern: ${propertyInfo.gitTagWildCardPattern}")
    } else {
        matcher = null
        log.debugMessage("propertyInfo.appVersion : ${propertyInfo.appVersion}\nmatcherPattern: ${matcherPattern}")
        log.message(messageNumber: 'ERROROO73', messageTitle: "Problem deriving tag name from existing appVersion ${propertyInfo.appVersion}")
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE)
    }
    log.debugMessage("<---- leaving method", common.getCuurentMethodName())
    return map
}

String getLatestTage(Map pInfo) {
    log.debugMessage("<---- entering method", common.getCuurentMethodName())
    String latestReleaseTag = '', releaseTags = cmd.getOutput("git tag --list \" ${pInfo.gitTagWildCardPattern}\" --sort=v:refname")
    if (releaseTags) {
        latestReleaseTag = releaseTags.split('\n')[-1]
        log.debugMessage("found latest release tag ${latestReleaseTag}")
    } else {
        log.debugMessage("no release tag matching ${pInfo.gitTagWildCardPattern} found")
    }
    log.debugMessage("<---- leaving method", common.getCuurentMethodName())
    return latestReleaseTag
}

String getPatchVersionNumber(Map pInfo ) {
    log.debugMessage("<---- entering method", common.getCuurentMethodName())
    def matcher = '', matcherPattern= '', initialGitTagSegment = ''
    String latestReleaseTag = getLatestTage(pInfo), patchVersionNumber = 0
    if(latestReleaseTag) {
        matcherPattern = /^(?<initialGitTagSegment>((.*?)+\.*+)+)(?<patchVersionNumber>\d+)$/
        matcher = latestReleaseTag =~ matcherPattern
        if(matcher.matches()) {
            initialGitTagSegment = matcher.group('initialGitTagSegment')
            patchVersionNumber = matcher.group('patchVersionNumber').toInteger() + 1
            matcher = null
            log.debugMessage("matcherPattern: ${matcherPattern}" +
                    "\ninitialGitTagSegment: ${initialGitTagSegment}" +
                    "\npatchVersionNumber: ${patchVersionNumber}")
        } else {
            log.debugMessage("matcherPattern: ${matcherPattern}")
        }
    }
    log.debugMessage("<---- leaving method", common.getCuurentMethodName())
    return patchVersionNumber
}

void setProps( Map propertyInfo) {
    propertyInfo.iqStage = 'release'
}

return this






