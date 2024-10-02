package com.unnam.libs.buildTechs

import com.unnam.libs.cmd
import com.unnam.libs.getProperties
import com.unnam.libs.image
import com.unnam.libs.log
import com.unnam.libs.release
import com.unnam.libs.status
import groovy.transform.Field


@Field
def cmd = new cmd()

@Field
def image = new image ()

@Field
def log = new log ()

@Field
def status = new status ()

@Field
def getProperties = new getProperties ()

@Field
def release = new release ()


@Field
def TECH_SPECIFIC_FILE_EXCLUSION = ''

@Field
def FORTIFY_TECH_STACK = 'Go'

def getAppVersion(Map propertyInfo) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())

    def lines, tmpVal

    try {
        def configFile = readFile (propertyInfo.buildFile)
        log.debugMessage("Build file used: ${propertyInfo.buildFile}")
        lines = configFile.readLines()
    } catch (Exception ex) {
        log.message(messageNumber: 'ERROR0036', messageTitle: "Problem reading build file ${propertyInfo.buildFile}")
        println 'Exception message:\n' +ex.getMessage()
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE)
    }

    for (line in lines) {
        if(line.matches('.*current_version.*=.*')) {
            tmpArr = line.split("\\=")
            tmpVal = tmpArr.last().trim().replaceAll('"|,', '')
            propertyInfo.appVersion = tmpVal
        }
    }
    if(!propertyInfo.containsKey('appVersion')) {
        log.message(messageNumber: 'ERROR0037', messageTitle: "Error retrieving current_version in  ${propertyInfo.buildFile}")
        println 'Exception message:\n' +ex.getMessage()
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE)
    }
    log.debugMessage("<------- leaving method", common.getCurrentMethodName())
}

def getAppInfo(Map propertyInfo) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())

    propertyInfo.appName = propertyInfo.buildExecutable.toLowerCase()

    if(propertyInfo.customAppVersion) {
        propertyInfo.appVersion = propertyInfo.customAppVersion
    } else {
        getAppVersion(propertyInfo)
    }
    log.debugMessage("<------- leaving method", common.getCurrentMethodName())
}

def setAppInfo(Map propertyInfo) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())
    if(propertyInfo.customAppVersion) {
        propertyInfo.appVersion = propertyInfo.customAppVersion
    } else {
        getAppVersion(propertyInfo)
    }

    propertyInfo.defaultDockerOptions = ''
    if(!common.isPR(env.BRANCH_NAME)) {
        propertyInfo.defaultDockerOptions = "--build-arg PROJECT_ID=${image.CENTRAL_GCR} --build-arg BUILD_EXECUTABLE=${propertyInfo.buildExecutable} --build-arg CENTRAL_GCR_PREFIX=${propertyInfo.centralGcrPrefix} --build-arg GIT_COMMIT=${GIT_COMMIT}" +
                " --build-arg GIT_URL=${GIT_URL} --build-arg GIT_BRANCH=${GIT_BRANCH} --build-arg BUILD_NUMBER=${BUILD_NUMBER} --build-arg BUILD_URL=${BUILD_URL}"
    }

    log.debugMessage("<------- leaving method", common.getCurrentMethodName())
}

def createGoCmd(Map propertyInfo, String v) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())
    withEnv(['GOPRIVATE=github.com/pavan-unnam', "GOPROXY=https://${propertyInfo.nexusRepoServerName}/respository/${propertyInfo.goRegistryRepo},direct"]) {
        withCredentials([gitUsernamePassword(credentialsId: propertyInfo.githubAppCredentialId)]) {
            cmd.execute(v)
        }
    }
    log.debugMessage("<------- leaving method", common.getCurrentMethodName())
}

def createCurlCmd(Map propertyInfo, String v) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())
    withCredentials([usernamePassword(credentialsId: propertyInfo.repoCredentials, passwordVariable: 'repoPassword', usernameVariable: 'repoUsername')]) {
        cmd.execute(v + " -u ${repoUsername}:${repoPassword}")
    }
    log.debugMessage("<------- entering method", common.getCurrentMethodName())
}


def createStage(Map propertyInfo, String cmdPrefix) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())

    def cmdList = [:] as TreeMap

    cmd.getList(propertyInfo, cmdPrefix, cmdList)

    cmdList.each {k, v->
        try {
            switch (v) {
                case ~"curl.*" :
                    stage(k) {
                       createCurlCmd(propertyInfo, v)
                    }
                    break
                default:
                    stage(k) {
                        createGoCmd(propertyInfo, v)
                    }
                    break
            }
        }catch (Exception ex) {
            log.message(messageNumber: 'ERROR0006', messageTitle: "Problem creating/running stage ${k}, command ${v}")
            println 'Exception message:\n' +ex.getMessage()
            status.setStage(common.JEKINS_STATUS.FAILURE)
            status.setJob(common.JEKINS_STATUS.FAILURE)
        }
    }

    log.debugMessage("<------- entering method", common.getCurrentMethodName())
}

def getSetBuildOptions(Map propertyInfo) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())
    propertyInfo.buildFile = '.bumpversion.cfg'
    propertyInfo.releaseBranchPrefix = 'v'
    propertyInfo.publishRepo = propertyInfo.snapshotRepo

    propertyInfo.repoCredentials = (propertyInfo.repoCredentials == null || propertyInfo.repoCredentials == '') ? "nexusAuthToken" : propertyInfo.repoCredentials

    propertyInfo.sonarQubeScanner = (propertyInfo.sonarQubeScanner == null || propertyInfo.sonarQubeScanner == '') ? "defaultSonarQubeScanner" : propertyInfo.sonarQubeScanner


    propertyInfo.buildExecutable = (propertyInfo.buildExecutable == null || propertyInfo.buildExecutable == '') ? propertyInfo.repoName : propertyInfo.buildExecutable

    propertyInfo.goRegistryRepo = (propertyInfo.groupRepo == null || propertyInfo.groupRepo == '') ? 'efxgo' : propertyInfo.groupRepo

    getProperties.setDefault(propertyInfo, common.CMD.BUILD_CMD, "echo 'Add a command to build your app -e.g. go build'")
    getProperties.setDefault(propertyInfo, common.CMD.TEST_CMD, "echo 'Add a command to test your app -e.g. go test'")

    getProperties.setDefault(propertyInfo, common.CMD.UPLOAD_CMD, "echo 'Add a command to upload your app to Nexus -e.g. curl'")

    log.debugMessage("<------- entering method", common.getCurrentMethodName())
}

def createParallelStages(Map propertyInfo, String cmdPrefix ) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())
    def cmdList =[:] as TreeMap
    def stageMap = [:] as LinkedHashMap

    println "Parallel execution of testCmd's are enabled"

    cmd.getList(propertyInfo, cmdPrefix, cmdList)

    cmdList.each {k, v ->
        stageMap["${k}-run"] = {
            try {
                switch (v) {
                    case ~"curl.*":
                        stage(k) {
                            node(propertyInfo.build_agent_lable) {
                                container(propertyInfo.buildConainerName) {
                                    echo "Fetching source code from stash: '${propertyInfo.stashName}'"

                                    unstash propertyInfo.stashName
                                    createCurlCmd(propertyInfo, v)

                                    if (params.debug) {
                                        log.debugMessage "Testing stash creation by adding text files"
                                        log.debugMessage "Stash params:\nName: ${k}-run\nIncludes: ${propertyInfo.stashInclude}\nExcludes: ${propertyInfo.stashExclude}"

                                        tmpCmd = "echo ${k}-run > ${k}-run.txt"
                                        cmd.execute(tmpCmd)
                                        cmd.execute("cat ${k}-run.txt")
                                    }

                                    stash (name: "${k}-run",
                                            includes: propertyInfo.stastInclude,
                                            excludes: propertyInfo.stastExclude)
                                }
                            }
                        }

                        break
                    default:
                        stage(k) {
                            node(propertyInfo.build_agent_lable) {
                                container(propertyInfo.buildConainerName) {
                                    echo "Fetching source code from stash: '${propertyInfo.stashName}'"

                                    unstash propertyInfo.stashName

                                    createGoCmd(propertyInfo, v)

                                    if (params.debug) {
                                        log.debugMessage "Testing stash creation by adding text files"
                                        log.debugMessage "Stash params:\nName: ${k}-run\nIncludes: ${propertyInfo.stashInclude}\nExcludes: ${propertyInfo.stashExclude}"

                                        tmpCmd = "echo ${k}-run > ${k}-run.txt"
                                        cmd.execute(tmpCmd)
                                        cmd.execute("cat ${k}-run.txt")
                                    }

                                    stash (name: "${k}-run",
                                            includes: propertyInfo.stastInclude,
                                            excludes: propertyInfo.stastExclude)
                                }
                            }
                        }
                        break
                }
            } catch (Exception ex) {
                log.message(messageNumber: 'ERROR0006', messageTitle: "Problem creating/running Parallel stages ${k}-run, command ${v}")
                println 'Exception message:\n' +ex.getMessage()
                status.setStage(common.JEKINS_STATUS.FAILURE)
                status.setJob(common.JEKINS_STATUS.FAILURE)
            }
        }
    }

    parallel stageMap

    cmdList.each {k,v ->
        println "Unstashing ${k}-run on main node"
        unstash "${k}-run"

        if(params.debug) {
            cmd.execute("ls -l *txt")
        }
    }
    log.debugMessage("<------- leaving method", common.getCurrentMethodName())
}

def publishBuildTechTests(Map propertyInfo ) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())
    println 'TODO - identify, implement test tools for Go'
    log.debugMessage("<------- leaving method", common.getCurrentMethodName())
}

Boolean isFortifyOtherSrcFilesCopied(Map propertyInfo) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())

    Boolean retVal = true
    log.debugMessage("<------- leaving method", common.getCurrentMethodName())

    return retVal
}

Map parseGitTagListForVersionAndPreReleaseTag(def matcher) {
    Map map = [:]
    def versionNumbers = matcher.group('versionNumbers')

    map.versionNumbers = versionNumbers.take(versionNumbers.lastIndexOf('.'))
    map.preReleaseTag = matcher.group('preReleaseTag')
    return map
}


void createTag(Map propertyInfo) {
    def targetVersion, appVersion, latestReleaseTag = null

    if(propertyInfo.customAppVersion) {
        targetVersion = '--new-version'
        appVersion = propertyInfo.appVersion
    } else {
        release.defineGitTagWildCardPattern(propertyInfo, this)
        latestReleaseTag = release.getLatestTag(propertyInfo) ? latestReleaseTag.substring(1) : propertyInfo.appVersion
        targetVersion = '--current-version'
        appVersion = latestReleaseTag
    }

    log.debugMessage("creating tag ${latestReleaseTag} with bump2version")
    cmd.execute("bump2version --allow-dirty ${targetVersion} ${appVersion} ${propertyInfo.releaseType}")
    propertyInfo.publishRepo = propertyInfo.releaseRepo
    propertyInfo.tagCreated = 'true'
}