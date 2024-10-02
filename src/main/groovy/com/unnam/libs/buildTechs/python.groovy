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
def TECH_SPECIFIC_FILE_EXCLUSION = 'dist/**,build/**,**egg-info/**'

@Field
def FORTIFY_TECH_STACK = 'PYTHON'


def getAppVersion(Map propertyInfo) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())

    def lines

    try {
        def configFile = readFile (propertyInfo.buildFile)
        log.debugMessage("Build file used: ${propertyInfo.buildFile}")
        lines = configFile.readLines()
    } catch (Exception ex) {
        log.message(messageNumber: 'ERROR0031', messageTitle: "Problem reading build file ${propertyInfo.buildFile}")
        println 'Exception message:\n' +ex.getMessage()
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE)
    }

    for (line in lines) {
        if(!line.matches('^.*#.*')) {
            if(!propertyInfo.customAppVersion) {
                if(line.matches(".*version.*=.*")) {
                    propertyInfo.appVersion = line.split("\\=").last().trim().replaceAll(/[,\'\"]/,'')
                }  else {
                    propertyInfo.appVersion = propertyInfo.customAppVersion
                }

                if(ine.matches(".*name.*=.*")) {
                    propertyInfo.appName = line.split("\\=").last().trim().replaceAll(/[,\'\"]/,'')
                }
            }
        }
    }

    def foundKeys = propertyInfo.containsKey("appVersion") && propertyInfo.containsKey("appName")
    if(!foundKeys) {
        log.message(messageNumber: 'ERROR0032', messageTitle: "Error retrieving app version & name fields in  in  ${propertyInfo.buildFile}")
        println 'Exception message:\n' +ex.getMessage()
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE)
    }
    log.debugMessage("<------- leaving method", common.getCurrentMethodName())
}


def setAppInfo(Map propertyInfo) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())

    sanitizeAppVersion(propertyInfo)
    try {
        def file = readFile file :propertyInfo.buildFile
        log.debugMessage("org file contains : ${file}")
        def newContents
        if(propertyInfo.buildFile.trim().matches('setup.cfg')) {
            newContents = file.replaceAll("version\\s*=(|'|\").*('|\"|)", "version = ${propertyInfo.appVersion}")
        } else{
            newContents = file.replaceAll("version\\s*=(|'|\").*('|\"|)", "version = \"${propertyInfo.appVersion}\"")
        }

        log.debugMessage("replace file contains : ${newContents}")
        writeFile file: propertyInfo.buildFile, text: newContents
        validateAppVersionUpdateInBuildFile(propertyInfo)
    } catch (Exception ex) {
        log.message(messageNumber: 'ERROR0033', messageTitle: "Problem updating build file ${propertyInfo.buildFile} with new app version")
        println 'Exception message:\n' +ex.getMessage()
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE)
    }

    propertyInfo.defaultDockerOptions = ''
    if(!common.isPR(env.BRANCH_NAME)) {
        propertyInfo.defaultDockerOptions = "--build-arg PROJECT_ID=${image.CENTRAL_GCR} --build-arg CENTRAL_GCR_PREFIX=${propertyInfo.centralGcrPrefix} --build-arg GIT_COMMIT=${GIT_COMMIT}" +
                "--build-arg GIT_URL=${GIT_URL} --build-arg GIT_BRANCH=${GIT_BRANCH} --build-arg BUILD_NUMBER=${BUILD_NUMBER} --build-arg BUILD_URL=${BUILD_URL} --build-arg APP_FILE=${propertyInfo.appVersion}.tar.gz" +
                "-build-arg REPO_NAME=${propertyInfo.groupRepo}"
    }

    log.debugMessage("<------- leaving method", common.getCurrentMethodName())
}

def sanitizeAppVersion(Map propertyInfo) {
    String appVersion = propertyInfo.appVersion.toString()
    propertyInfo.appVersion = appVersion.toLowerCase().contains('-snapshot') ? appVersion.replaceAll('(?i)-SNAPSHOT','.dev0') : propertyInfo.appVersion
    if(propertyInfo.useBranchNameInAppVersion.toBoolean()) {
        log.message(messageNumber: 'WARNING0032', messageTitle: "this flag is disabled for python due to PEP 440 & Container naming/versioning limitations")
        status.setStage(common.JEKINS_STATUS.FAILURE)
    }
}

def validateAppVersionUpdateInBuildFile(Map propertyInfo) {
    String buildFileContents = readFile(propertyInfo.buildFile).readLines().join('\n')
    if(buildFileContents.contains("version = ${propertyInfo.appVersion}")|| buildFileContents.contains("version = \"${propertyInfo.appVersion}\"")) {
        println "[validateAppVersionUpdateInBuildFile] validated ${propertyInfo.buildFile} with new app version  ${propertyInfo.appVersion}."
    }else {
        throw new Exception("[validateAppVersionUpdateInBuildFile] new app version: ${propertyInfo.appVersion} is not updated in buildFile ${propertyInfo.buildFile}.")
    }
}


def createStage(Map propertyInfo, String cmdPrefix) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())

    def cmdList = [:] as TreeMap

    cmd.getList(propertyInfo, cmdPrefix, cmdList)

    cmdList.each {k, v->
        try {
            switch (v) {
                case ~"python.*" :
                case ~"pip.*" :
                case ~"pyb.*" :
                    def targetRepo = propertyInfo.groupRepo + "/simple"
                    stage(k) {
                        withEnv(["PIP_INDEX_URL=https://${propertyInfo.nexusRepoServerName}/repository/${targetRepo}"]) {
                            cmd.execute(v)
                        }
                    }
                    break
                case ~"twine.*" :
                    def targetRepo = propertyInfo.publishRepo + "/"
                    stage(k) {
                        withCredentials([usernamePassword(credentialsId: propertyInfo.repoCredentials, passwordVariable: 'repoPassword', usernameVariable: 'repoUsername')]) {
                            cmd.execute(v +" -u ${repoUsername} -p ${repoPassword} --repository-url https://${propertyInfo.nexusRepoServerName}/repository/${targetRepo}")
                        }
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

    propertyInfo.buildFile = (propertyInfo.buildFile == null || propertyInfo.buildFile == '') ? "setup.py" : propertyInfo.buildFile

    propertyInfo.repoCredentials = (propertyInfo.repoCredentials == null || propertyInfo.repoCredentials == '') ? "nexusAuthToken" : propertyInfo.repoCredentials

    propertyInfo.sonarQubeScanner = (propertyInfo.sonarQubeScanner == null || propertyInfo.sonarQubeScanner == '') ? "defaultSonarQubeScanner" : propertyInfo.sonarQubeScanner


    propertyInfo.fortifyLangLevel = (propertyInfo.fortifyLangLevel == null || propertyInfo.fortifyLangLevel == '') ? '2' : propertyInfo.fortifyLangLevel


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
                    case ~"python.*" :
                    case ~"pip.*" :
                    case ~"pyb.*" :
                        def targetRepo = propertyInfo.groupRepo + "/simple"
                        stage(k) {
                            node(propertyInfo.build_agent_lable) {
                                container(propertyInfo.buildConainerName) {
                                    echo "Fetching source code from stash: '${propertyInfo.stashName}'"

                                    unstash propertyInfo.stashName
                                    withEnv(["PIP_INDEX_URL=https://${propertyInfo.nexusRepoServerName}/repository/${targetRepo}"]) {
                                        cmd.execute(v)
                                    }

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
                    case ~"twine.*" :
                        def targetRepo = propertyInfo.publishRepo + "/"
                        stage(k) {
                            node(propertyInfo.build_agent_lable) {
                                container(propertyInfo.buildConainerName) {
                                    echo "Fetching source code from stash: '${propertyInfo.stashName}'"

                                    unstash propertyInfo.stashName
                                    withCredentials([usernamePassword(credentialsId: propertyInfo.repoCredentials, passwordVariable: 'repoPassword', usernameVariable: 'repoUsername')]) {
                                        cmd.execute(v +" -u ${repoUsername} -p ${repoPassword} --repository-url https://${propertyInfo.nexusRepoServerName}/repository/${targetRepo}")
                                    }

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
            cmd.execute( "ls -l *txt")
        }
    }
    log.debugMessage("<------- leaving method", common.getCurrentMethodName())
}

def publishBuildTechTests(Map propertyInfo ) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())
    println 'TODO - identify, implement test tools for python'
    log.debugMessage("<------- leaving method", common.getCurrentMethodName())
}

Boolean isFortifyOtherSrcFilesCopied(Map propertyInfo) {
    return true
}

Map parseGitTagListForVersionAndPreReleaseTag(def matcher) {
    Map map = [:]
    def versionNumbers = matcher.group('versionNumbers')
    map.preReleaseTag = matcher.group('preReleaseTag')
    return map
}

void createTag(Map propertyInfo) {
    Map tagInfo = release.defineGitTagWildCardPattern(propertyInfo, this)
    release.createTag(propertyInfo, tagInfo)

    propertyInfo.publishRepo = propertyInfo.releaseRepo
}
