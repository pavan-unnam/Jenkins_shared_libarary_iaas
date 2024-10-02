package com.unnam.libs

import groovy.transform.Field


@Field
def log = new log()

Boolean setStage (String stageStatus) {
    String stageStatusUpperCase = stageStatus.toUpperCase()
    Boolean result = false

    log.debugMessage ("<---- Entering method", common.getCuurentMethodName())
    log.debugMessage ("stageStatus passed : ${stageStatus}")

    if(stageStatusUpperCase.contains('UNSTABLE') || stageStatusUpperCase.contains('FAILURE')) {
        catchError(stageResult: stageStatusUpperCase, buildResult: null) {
            error 'Setting stage to ' + stageStatusUpperCase
            result = true
        }
    }

    log.debugMessage ("<---- leaving method", common.getCuurentMethodName())
}


Boolean setJob ( String buildStatus, String failOnError = 'true') {
    String buildStatusUpperCase = buildStatus.toUpperCase()
    Boolean localFailOnError = (failOnError.toLowerCase() == 'true') ? true : false
    Boolean result = false

    log.debugMessage ("<---- Entering method", common.getCuurentMethodName())

    log.debugMessage ("buildStatus passed : ${buildStatus}, failOnError passed ${failOnError}, " +
            "localFailOnError set to ${localFailOnError}")

    switch (buildStatusUpperCase) {
        case 'UNSTABLE' :
            catchError(buildResult: buildStatusUpperCase, stageResult: null) {
                error 'setting build to ' + buildStatusUpperCase
                result = true
            }
            break
        case 'FAILURE' :
            if(localFailOnError) {
                error 'Failing Build'
                result = true
            }
            break
        default : break
    }
    log.debugMessage ("<---- Leaving method", common.getCuurentMethodName())
}

return this




