package com.unnam.libs.fortify

import com.unnam.libs.log
import com.unnam.libs.status
import groovy.transform.Field
import jenkins.model.GlobalConfiguration
import org.jenkinsci.plugins.fodupload.FodGlobalDescriptor

@Field
def log = new log ()

@Field
def status = new status ()


def getBearerToken(Map propertyInfo) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())

    def clientId = propertyInfo.fortifyClientId
    def credentialId = propertyInfo.fortifyClientSecretId
    String bearerToken = ''

    withCredentials([string(credentialsId: credentialId, variable: 'FORTIFY_CLIENT_SECRET')]) {
        String body = 'scope=api-tenant&grant_type=client_credentials&client_id='.concat(clientId).concat('&client_secret=').concat(FORTIFY_CLIENT_SECRET)
        def tokenResponse = httpRequest contentType: 'APPLICATION_FORM', httpMode: 'POST', requestBody: body, url: "${fortify.FORTIFY_API_BASE}/oauth/token"
        def tokenJson = readJSON text: tokenResponse.content
        bearerToken = tokenJson.access_token
        log.debugMessage("Got Fortify Bearer token")
    }

    log.debugMessage("<------- Leaving method", common.getCurrentMethodName())

    return bearerToken
}
-
void pollResults(Map propertyInfo) {
    try {
        timeout(time: propertyInfo.fortifyTimeout, unit: 'MINUTES') {
            fodPollResults policyFailureBuildResultPreference : 1, pollingInterval:1, releaseId: propertyInfo.fortifyReleaseId
        }
    } catch (Exception ex) {
        log.message(messageNumber: 'ERROR0010', messageTitle: "Fortify polling timed out; timeout period is ${propertyInfo.fortifyTimeout} ${MINUTES}")
        println 'Exception message:\n' +ex.getMessage()
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE, propertyInfo.fortifyFailOnError)
    }
}


def parseConsoleLog(Map results) {
    def lines = manager.build.logFile.text
    ArrayList ConsoleLog = lines.tokenize('\n')

    ConsoleLog.find { line->
        if(line.matches("^.*Critical:.*")) {
            results.issueCounterCritical = line.split("Critical:")[1].trim() as Integer
        }
        if(line.matches("^.*High:.*")) {
            results.issueCounterCritical = line.split("High:")[1].trim() as Integer
        }
        if(line.matches("^.*Medium:.*")) {
            results.issueCounterCritical = line.split("Medium:")[1].trim() as Integer
        }
        if(line.matches("^.*Low:.*")) {
            results.issueCounterCritical = line.split("Low:")[1].trim() as Integer
        }
    }
}

 Map setResultsData(Map propertyInfo, Map results) {
    if((propertyInfo.fortifyCriticalThreshold as Integer) < results.issueCounterCritical) {
        results.issueCountCriticalResult="FAIL"
        results.fortifyThresholdFailed='true'
        results.msgString = results.msgString + " critical "
    }

    if((propertyInfo.fortifyHighThreshold as Integer) < results.issueCounterHigh) {
        results.issueCountHighResult="FAIL"
        results.fortifyThresholdFailed='true'
        results.msgString = results.msgString + " high "
    }

    if((propertyInfo.fortifyMediumThreshold as Integer) < results.issueCounterMedium) {
        results.issueCountMediumResult="FAIL"
        results.fortifyThresholdFailed='true'
        results.msgString = results.msgString + " medium "
    }

    if((propertyInfo.fortifyLowThreshold as Integer) < results.issueCounterLow) {
        results.issueCountLowResult="FAIL"
        results.fortifyThresholdFailed='true'
        results.msgString = results.msgString + " low "
    }

    return results
}

Boolean checkWaitForResults(Map propertyInfo) {
    log.debugMessage("<------- entering method", common.getCurrentMethodName())

    boolean fortifyThresholdFailed = false, scanTimeout = false
    Map results = ['issueCountCritical'        : 0,
                   'issueCountHigh'            : 0,
                   'issueCountMedium'          : 0,
                   'issueCountLow'             : 0,
                   'issueCountCriticalResult': 'PASS',
                   'issueCountHighResult'    : 'PASS',
                   'issueCountMediumResult'  : 'PASS',
                   'issueCountLowResult'     : 'PASS',
                   'msgString'                 : 'Scan results exceeded',
                   'fortifyThresholdFailed'    : '']
    if(propertyInfo.enableFortifyWaitForResults.toBoolean()) {
        pollResults(propertyInfo)
        parseConsoleLog(results)
        results = setResultsData(propertyInfo,results)
        fortifyThresholdFailed = results.fortifyThresholdFailed.toBoolean()

        String logtable = "\n\n${common.ANSI_COLOR.YELLOW}Fortify Vulnerability details${common.ANSI_COLOR.BLACK}\nThresholdName\t\tThresholdValue\tActualFound\tResults${common.ANSI_COLOR.OFF}"
        logtable += "\nCriticalThreshold\t${propertyInfo.fortifyCriticalThreshold}\t\t${results.issueCountCritical}\t\t${results.issueCountCriticalResult}"
        logtable += "\nHighThreshold\t${propertyInfo.fortifyHighThreshold}\t\t${results.issueCountHigh}\t\t${results.issueCountHighResult}"
        logtable += "\nMediumThreshold\t${propertyInfo.fortifyMediumThreshold}\t\t${results.issueCountMedium}\t\t${results.issueCountMediumResult}"
        logtable += "\nLowThreshold\t${propertyInfo.fortifyLowThreshold}\t\t${results.issueCountLow}\t\t${results.issueCountLowlResult}"
        println logtable
    }

    log.debugMessage("<------- Leaving method", common.getCurrentMethodName())

    return fortifyThresholdFailed
}

void checkInvalidPluginParameters(Map propertyInfo) {
    if(!propertyInfo.enableFortify.toBoolean()) {
        log.debugMessage("Skipping check of fortify plugin configuration due to fortify being disabled", common.getCurrentMethodName())
        return
    }
    log.debugMessage("<------- entering method", common.getCurrentMethodName())

    boolean valid = true

    String globalAuthType = GlobalConfiguration.all().get(FodGlobalDescriptor.class).globalAuthType

    log.debugMessage("fortifyClientId: ${propertyInfo.fortifyClientId}\nfortifyClientSecretId: ${propertyInfo.fortifyClientSecretId}\nfortifyAuthenticationType: ${globalAuthType}", common.getCurrentMethodName())

    if(!propertyInfo.fortifyClientId || propertyInfo.fortifyClientId != fortify.FORTIFY_API_CLIENT_ID) {
        log.debugMessage("fortifyClientId is not set or has an unexpected value. Expected value: ${fortify.FORTIFY_API_CLIENT_ID}, current value: ${propertyInfo.fortifyClientId}", common.getCurrentMethodName())
    }

    if(!propertyInfo.fortifyClientSecretId || propertyInfo.fortifyClientSecretId != fortify.FORTIFY_API_CLIENT_SECRET_ID) {
        log.debugMessage("fortifyClientSecretId is not set or has an unexpected value. Expected value: ${fortify.FORTIFY_API_CLIENT_SECRET_ID}, current value: ${propertyInfo.fortifyClientSecretId}", common.getCurrentMethodName())
    }

    if(!globalAuthType || globalAuthType != fortify.FORTIFY_AUTHENTICATION_TYPE) {
        log.debugMessage("fortifyAuthenticationType is not set or has an unexpected value. Expected value: ${fortify.FORTIFY_AUTHENTICATION_TYPE}, current value: ${globalAuthType}", common.getCurrentMethodName())
    }

    if(!valid) {
        log.message(messageNumber: 'ERROR0039', messageTitle: "Fortify plugin misconfiguration")
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE, propertyInfo.fortifyFailOnError)
    }

    log.debugMessage("<------- Leaving method", common.getCurrentMethodName())
}