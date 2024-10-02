package com.unnam.libs.buildTechs

import com.unnam.libs.cmd
import com.unnam.libs.getProperties
import com.unnam.libs.image
import com.unnam.libs.log
import com.unnam.libs.release
import com.unnam.libs.status
import groovy.transform.Field
import org.json.JSONException

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
def TECH_SPECIFIC_FILE_EXCLUSION = '**/dist/**, *tgz'

@Field
def FORTIFY_TECH_STACK = 'JS/TS/HTML'


def getAppInfo(Map propertyInfo) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())

    try {
        def configFile = readFile (propertyInfo.buildFile)
        log.debugMessage("Build file used: ${propertyInfo.buildFile}")
        propertyInfo.appVersion = propertyInfo.customAppVersion ? propertyInfo.customAppVersion : configFile.version
        propertyInfo.appName = configFile.name

        if((propertyInfo.appVersion == null) || propertyInfo.appName == null) {
            log.message(messageNumber: 'ERROR0063', messageTitle: "Problem reading version and/or name value from build file  ${propertyInfo.buildFile}")
            status.setStage(common.JEKINS_STATUS.FAILURE)
            status.setJob(common.JEKINS_STATUS.FAILURE)
        }
    } catch (IOException ex) {
        log.message(messageNumber: 'ERROR0061', messageTitle: "Problem reading build file  ${propertyInfo.buildFile} does it exist ?")
        println 'Exception message:\n' +ex.getMessage()
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE)
    }catch (JSONException ex) {
        log.message(messageNumber: 'ERROR0061', messageTitle: "Problem parsing json data in  ${propertyInfo.buildFile} contents is this JSON ? ?")
        println 'Exception message:\n' +ex.getMessage()
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE)
    }catch (Exception ex) {
        log.message(messageNumber: 'ERROR0034', messageTitle: "Problem processing ${propertyInfo.buildFile} contents")
        println 'Exception message:\n' +ex.getMessage()
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE)
    }
    log.debugMessage("<------- leaving method", common.getCurrentMethodName())
}

def setAppInfo(Map propertyInfo) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())

    if(propertyInfo.useBranchNameInAppVersion.toBoolean() && (propertyInfo.useBranchNameInReleaseVersion.toBoolean() || !propertyInfo.tagCreated.toBoolean()))  {
        def branchName = "${env.BRANCH_NAME}".replaceAll('/|_','-')
        propertyInfo.appVersion = propertyInfo.appVersion +"-" +branchName
    }

    try {
        cmd.execute("npm --no-git-tag-version --allow-same-version version ${propertyInfo.appVersion}")
    }catch (Exception ex) {
        log.message(messageNumber: 'ERROR0035', messageTitle: "Problem updating build file ${propertyInfo.buildFile} with new app version")
        println 'Exception message:\n' +ex.getMessage()
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE)
    }

    println "Updated ${propertyInfo.buildFile} with new app version ${propertyInfo.appVersion}"

    propertyInfo.defaultDockerOptions = ''
    if(!common.isPR(env.BRANCH_NAME)) {
        propertyInfo.defaultDockerOptions = "--build-arg PROJECT_ID=${image.CENTRAL_GCR} --build-arg CENTRAL_GCR_PREFIX=${propertyInfo.centralGcrPrefix} --build-arg GIT_COMMIT=${env.GIT_COMMIT}" +
                " --build-arg GIT_URL=${env.GIT_URL} --build-arg GIT_BRANCH=${env.GIT_BRANCH} --build-arg BUILD_NUMBER=${env.BUILD_NUMBER} --build-arg BUILD_URL=${env.BUILD_URL} --build-arg APP_NAME=${propertyInfo.appName}"
    }

    log.debugMessage("<------- leaving method", common.getCurrentMethodName())
}

def createStage(Map propertyInfo, String cmdPrefix) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())

    def cmdList = [:] as TreeMap

    cmd.getList(propertyInfo, cmdPrefix, cmdList)

    cmdList.each {k, v->
        try {
            switch (v) {
                case ~"npm.*" :
                    def targetRepo = propertyInfo.groupRepo
                    if(v.contains('publish')) {
                        targetRepo = propertyInfo.publishRepo
                    }
                    stage(k) {
                       configureNpmRepository(propertyInfo.nexusRepoServerName, targetRepo, propertyInfo.npmAuthToken)
                        cmd.execute(v)
                    }
                    break
                default:
                    stage(k) {
                        cmd.execute(v)
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
    log.debugMessage("<------- leaving method", common.getCurrentMethodName())
}

def getSetBuildOptions(Map propertyInfo) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())

    propertyInfo.publishRepo = propertyInfo.snapshotRepo

    propertyInfo.buildFile = (propertyInfo.buildFile == null || propertyInfo.buildFile == '') ? "package.json" : propertyInfo.buildFile

    propertyInfo.npmAuthToken = (propertyInfo.npmAuthToken == null || propertyInfo.npmAuthToken == '') ? "defaultNpmAuthToken" : propertyInfo.npmAuthToken

    propertyInfo.sonarQubeScanner = (propertyInfo.sonarQubeScanner == null || propertyInfo.sonarQubeScanner == '') ? "defaultSonarQubeScanner" : propertyInfo.sonarQubeScanner


    propertyInfo.fortifyCleanUpCmd = (propertyInfo.fortifyCleanUpCmd == null || propertyInfo.fortifyCleanUpCmd == '') ? 'npm prune --omit=dev' : propertyInfo.fortifyCleanUpCmd


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
                    case ~"npm.*":
                        def targetRepo = propertyInfo.groupRepo
                        if(v.contains('publish')) {
                            targetRepo = propertyInfo.publishRepo
                        }
                        stage(k) {
                            node(propertyInfo.build_agent_lable) {
                                container(propertyInfo.buildConainerName) {
                                    echo "Fetching source code from stash: '${propertyInfo.stashName}'"

                                    unstash propertyInfo.stashName
                                    configureNpmRepository(propertyInfo.nexusRepoServerName, targetRepo, propertyInfo.npmAuthToken)
                                    cmd.execute(v)

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

                                    cmd.execute( v )

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
            sh "ls -l *txt"
        }
    }
    log.debugMessage("<------- leaving method", common.getCurrentMethodName())
}

def publishBuildTechTests(Map propertyInfo ) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())
    println 'TODO - identify, implement test tools for Java script'
    log.debugMessage("<------- leaving method", common.getCurrentMethodName())
}

Boolean isFortifyOtherSrcFilesCopied(Map propertyInfo) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())
    println 'Running fortifyCleanUpCmd "' +propertyInfo.fortifyCleanUpCmd + '" on fortify upload directory'
    dir(fortify.FORTIFY_UPLOAD_DIRECTORY) {
        try {
            cmd.execute(propertyInfo.fortifyCleanUpCmd)
            log.debugMessage("${propertyInfo.fortifyCleanUpCmd} run")
            println 'Fortify upload Directory contents :'
            cmd.execute("find .")
        }catch (Exception ex) {
            log.message(messageNumber: 'ERROR0038', messageTitle: "Problem running command '"  + propertyInfo.fortifyCleanUpCmd + "'. Fortify run will be skipped")
            println 'Exception message:\n' +ex.getMessage()
            status.setStage(common.JEKINS_STATUS.FAILURE)
            status.setJob(common.JEKINS_STATUS.FAILURE, propertyInfo.fortifyFailOnError)
            return false
        }
    }
    log.debugMessage("<------- leaving method", common.getCurrentMethodName())

    return true
}

void configureNpmRepository(String nexusRepoServerName, String targetRepo, String npmAuthToken) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())
    cmd.execute("npm config set registry https://${nexusRepoServerName}/repository/${targetRepo}")

    String npmVersion = cmd.getOutput("npm --version")
    int npmMajorVersion = npmVersion.split("\\.")[0] as int
    log.debugMessage("detected npm version ${npmVersion}", common.getCurrentMethodName())

    withCredentials([string(credentialsId: npmAuthToken, variable: 'TOKEN')]) {
        if(npmMajorVersion < 9) {
            log.debugMessage("detected npm version < 9, using global auth setup")
            cmd.execute("npm config set _auth \$TOKEN")
        } else {
            log.debugMessage("detected npm version >= 9, using scoped repository auth setup")
            cmd.execute("npm config set //${nexusRepoServerName}/repository/${targetRepo}/:_auth \$TOKEN")
        }
    }

    log.debugMessage("<------- leaving method", common.getCurrentMethodName())
}

Map parseGitTagListForVersionAndPreReleaseTag(def matcher) {
    Map map = [:]
    def versionNumbers = matcher.group('versionNumbers')

    map.versionNumbers = versionNumbers.take(versionNumbers.lastIndexOf('.'))
    map.preReleaseTag = matcher.group('preReleaseTag')
    return map
}

void createTag(Map propertyInfo) {
    Map tagInfo = release.defineGitTagWildCardPattern(propertyInfo, this)
    release.createTag(propertyInfo, tagInfo)

    propertyInfo.publishRepo = propertyInfo.releaseRepo
}