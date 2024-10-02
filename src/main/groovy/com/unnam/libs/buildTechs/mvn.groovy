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
def TECH_SPECIFIC_FILE_EXCLUSION = '**/target/**'

@Field
def FORTIFY_TECH_STACK = 'JAVA/J2EE/Kotlin'

@Field
def SONAR_PLUGIN_VERSION = '3.9.1.2184'


def getAppInfo(Map propertyInfo) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())

    try {
        if(!fieldExists(propertyInfo.dockerFileDirectory))
            throw new Exception ("Directory : "+ propertyInfo.dockerFileDirectory + "/ not found")
        else
            configFileProvider([configFile(fileId : "${propertyInfo.mvnSettingsXML}", variable : "MAVEN_SETTINGS")]) {
                def (groupId, artifactId, _, version) = cmd.getOutput("mvn help:evaluate -s $MAVEN_SETTINGS " +
                "-Dexpression=project.id -q -DforceStdout -f " + propertyInfo.dockerFileDirectory + "/pom.xml").split(":")
                propertyInfo.appVesrion = version
                propertyInfo.appGroupId = groupId
                propertyInfo.appName = artifactId
            }

        log.debugMessage("App version: ${propertyInfo.appVersion}\n GroupID: ${propertyInfo.appGroupId}\n App Name set: ${propertyInfo.appName}")
        setJacocoGoal(propertyInfo)
    } catch (Exception ex) {
        log.message(messageNumber: 'ERROR0002', messageTitle: "Problem reading ${propertyInfo.dockerFileDirectory}/pom.xml")
        println 'Exception message:\n' +ex.getMessage()
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE)
    }
}

def setAppInfo(Map propertyInfo) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())

    def branchName, tempVersion

    if((propertyInfo.useBranchNameInAppVersion.toBoolean()) && (propertyInfo.appVersion.matches(".*[a-zA-Z].*"))) {
        branchName = "${env.BRANCH_NAME}".replaceAll('/|-','-')
        tempVersion = branchName+"-"+propertyInfo.appVersion
        propertyInfo.appVersion = propertyInfo.customAppVersion ? propertyInfo.customAppVersion : tempVersion
    }

    try {
        configFileProvider([configFile(fileId: propertyInfo.mvnSettingsXML, variable: 'MAVEN_SETTINGS')]) {
            cmd.execute("mvn version:set -DnewVersion=${propertyInfo.appVersion} -DgenerateBackupPoms=false -DprocessAllModules -s $MAVEN_SETTINGS")
        }
    } catch (Exception ex) {
        log.message(messageNumber: 'ERROR0005', messageTitle: "Problem updating pom file with new app version")
        println 'Exception message:\n' +ex.getMessage()
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE)
    }

    println "Updated POM files with new app version ${propertyInfo.appVersion}"

    propertyInfo.defaultDockerOptions = ''
    if(!common.isPR(env.BRANCH_NAME)) {
        propertyInfo.defaultDockerOtions = "--build-arg PROJECT_ID=${image.CENTRAL_GCR} " +
                "--build-arg CENTRAL_GCR_PREFIX=${propertyInfo.centralGcrPrefix} -" +
                "-build-arg GIT_COMMIT=${GIT_COMMIT} " +
                "--build-arg GIT_URL=${GIT_URL} " +
                "--build-arg GIT_BRANCH=${GIT_BRANCH} " +
                "--build-arg BUILD_NUMBER=${BUILD_NUMBER} " +
                "--build-arg BUILD_URL=${BUILD_URL} " +
                "--build-arg JAR_FILE=${propertyInfo.appName}-${propertyInfo.appVersion}.${propertyInfo.artifactExtension}"
    }
}


def createStage(Map propertyInfo, String cmdPrefix) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())

    def cmdList = [:] as TreeMap

    cmd.getList(propertyInfo, cmdPrefix, cmdList)

    cmdList.each {k, v->
        try {
            switch (v) {
                case ~"mvn.*" :
                    stage(k) {
                        configFileProvider([configFile(fileId: propertyInfo.mvnSettingsXML, variable: 'MAVEN_SETTINGS')]) {
                            cmd.execute(v + " -s $MAVEN_SETTINGS")
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
            log.message(messageNumber: 'ERROR0006', messageTitle: "Problem creating/running stage")
            println 'Exception message:\n' +ex.getMessage()
            status.setStage(common.JEKINS_STATUS.FAILURE)
            status.setJob(common.JEKINS_STATUS.FAILURE)
        }
    }

    log.debugMessage("<------- leaving method", common.getCurrentMethodName())
}

def getSetBuildOptions(Map propertyInfo) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())

    propertyInfo.mvnSettingsXML = (propertyInfo.mvnSettingsXML == null || propertyInfo.mvnSettingsXML == '') ? "defaultMVNSettings" : propertyInfo.mvnSettingsXML

    propertyInfo.useLocalMvnRepo = (propertyInfo.useLocalMvnRepo == null || propertyInfo.useLocalMvnRepo == '') ? "false" : propertyInfo.useLocalMvnRepo

    propertyInfo.enableJacoco = (propertyInfo.enableJacoco == null || propertyInfo.enableJacoco == '') ? "false" : propertyInfo.enableJacoco

    propertyInfo.enableJunit = (propertyInfo.enableJunit == null || propertyInfo.enableJunit == '') ? "false" : propertyInfo.enableJunit

    propertyInfo.releaseRepo = (propertyInfo.releaseRepo == null || propertyInfo.releaseRepo == '') ? env.releaseRepo : propertyInfo.releaseRepo
    propertyInfo.snapshotRepo = (propertyInfo.snapshotRepo == null || propertyInfo.snapshotRepo == '') ? env.releaseRepo : propertyInfo.snapshotRepo

    propertyInfo.fortifyLangLevel = (propertyInfo.fortifyLangLevel == null || propertyInfo.fortifyLangLevel == '') ? '1.8' : propertyInfo.fortifyLangLevel

    propertyInfo.artifactExtension = (propertyInfo.artifactExtension == null || propertyInfo.artifactExtension == '') ? "jar" : propertyInfo.artifactExtension

    getProperties.setDefault (propertyInfo, common.CMD.BUILD_CMD, "echo 'Add a Maven command to build your app - e.g mvn compile -Dmaven.test.skip'")
    getProperties.setDefault (propertyInfo, common.CMD.TEST_CMD, "echo 'Add a Maven command to unit test your app - e.g mvn test'")
    getProperties.setDefault (propertyInfo, common.CMD.UPLOAD_CMD, "echo 'Add a Maven command to upload your app to Nexus - e.g mvn deploy -Dmaven.test.skip'")

    if(propertyInfo.useLocalMvnRepo.toBoolean ()) {
        log.debugMessage("useLocalMvnRepo set, updating build, uploadCmd's to use local workspace")

        localMvnRepoString = ' -D maven.repo.local=${WORKSPACE}/.m2/repository'
        propertyInfo.each {k, v ->
            if(k.contains( common.CMD.BUILD_CMD) && v.contains("mvn")) {
                println "Adding local repo string ${localMvnRepoString} to ${k}"
                propertyInfo."${k}" +=localMvnRepoString
            }
        }

        propertyInfo.each {k, v ->
            if(k.contains( common.CMD.TEST_CMD) && v.contains("mvn")) {
                println "Adding local repo string ${localMvnRepoString} to ${k}"
                propertyInfo."${k}" +=localMvnRepoString
            }
        }

        propertyInfo.each {k, v ->
            if(k.contains( common.CMD.UPLOAD_CMD) && v.contains("mvn")) {
                println "Adding local repo string ${localMvnRepoString} to ${k}"
                propertyInfo."${k}" +=localMvnRepoString
            }
        }

    }
    log.debugMessage("<------- leaving method", common.getCurrentMethodName())
}

def createParallelStages (Map propertyInfo, String cmdPrefix) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())
    def cmdList =[:] as TreeMap
    def stageMap = [:] as LinkedHashMap

    println "Parallel execution of testCmd's are enabled"

    cmd.getList(propertyInfo, cmdPrefix, cmdList)

    cmdList.each {k, v ->
        stageMap["${k}-run"] = {
            try {
                switch (v) {
                    case ~"mvn.*":
                        stage(k) {
                            node(propertyInfo.build_agent_lable) {
                                container(propertyInfo.buildConainerName) {
                                    echo "Fetching source code from stash: '${propertyInfo.stashName}'"

                                    unstash propertyInfo.stashName
                                    configFileProvider([configFile(fileId: "${propertyInfo.mvnSettingsXML}", variable: 'MAVEN_SETTINGS')]) {
                                        cmd.execute(v +" -s $MAVEN_SETTINGS")
                                    }

                                    if (propertyInfo.debug) {
                                        common.debugMessage "Testing stash creation by adding text files"
                                        common.debugMessage "Stash params:\nName: ${k}-run\nIncludes: ${propertyInfo.stashInclude}\nExcludes: ${propertyInfo.stashExclude}"

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

                                    cmd.execute(v)

                                    if (propertyInfo.debug) {
                                        common.debugMessage "Testing stash creation by adding text files"
                                        common.debugMessage "Stash params:\nName: ${k}-run\nIncludes: ${propertyInfo.stashInclude}\nExcludes: ${propertyInfo.stashExclude}"

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
                log.message(messageNumber: 'ERROR0006', messageTitle: "Problem creating/running Parallel stages")
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

def publishBuildTechTests (Map propertyInfo) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())

    if( propertyInfo.enableJacoco.toBoolean()) {
        jacoco( execPattern: 'target/**/*.exec, **/target/**/*.exec',
        classPattren: 'target/classes,**/target/classes',
        sourcePattern : 'src/main/java,**/src/main/java',
        exclusionPattern: 'src/test*,**/src/test*')
    }

    if(propertyInfo.enableJunit.toBoolean()) {
        junit allowEmptyResults: true, testResults:"**/target/surefire-reports/*.xml"
    }

    log.debugMessage("<------- leaving method", common.getCurrentMethodName())
}


def setJacocoGoal (Map propertyInfo) {
    def isMultiModule = false

    def rootPomInfo = readMavenPom file: "pom.xml"

    if(rootPomInfo.modules) {
        isMultiModule = true
    }

    if(isMultiModule) {
        propertyInfo.jacocoGoal = propertyInfo.jacocoGoal.replaceAll("report", "report-aggregate")
        println "Detected multi-module project\njacocoGoal has been set to ${propertyInfo.jacocoGoal}"
    }
}

Boolean isFortifyOtherSrcFilesCopied(Map propertyInfo) {
    boolean state = false
    def subModulePom = propertyInfo.containsKey("fortifyProcessSubModule") ? propertyInfo.fortifyProcessSubModule +
            '/pom.xml' : 'pom.xml'
    if(fileExists(subModulePom)) {
        try {
            configFileProvider([configFile(fileId: "${propertyInfo.mvnSettingsXML}", variable: 'MAVEN_SETTINGS')]) {
                cmd.execute("mvn -DOutputDirectory=${env.WORKSPACE}/${fortify.FORTIFY_UPLOAD_DIRECTORY}/subModuleDependencies dependency:copy-dependencies -DincludeScope=runtime" -f ${subModulePom} -s $MAVEN_SETTINGS)
            }
            println "Updated Fortify Upload directory with ${subModulePom} dependencies:"
            cmd.execute("find " + fortify.FORTIFY_UPLOAD_DIRECTORY)
            state = true
        } catch (Exception ex) {
            println "Problem with Adding Maven sub-module ${subModulePom} dependecies to upload directory\n"+ ex.getMessage()
        }
    } else {
        println "Fortify Upload directory was not updated with ${subModulePom} dependecies"
    }

    return state
}


Map parseGitTagListForVersionAndPreReleaseTag(def matcher) {
    Map map = [:]
    map.versionNumbers = matcher.group('versionNumbers')
    map.preReleaseTag = matcher.group('preReleaseTag')
    return map
}

void createTag(Map propertyInfo) {
    Map tagInfo = release.defineGitTagWildCardPattern(propertyInfo, this)
    release.createTag(propertyInfo, tagInfo)
}





