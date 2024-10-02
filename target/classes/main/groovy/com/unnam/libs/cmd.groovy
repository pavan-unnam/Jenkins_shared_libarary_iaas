package com.unnam.libs

def execute (String cmdString) {
    if(isUnix())
        sh cmdString
    else
        bat cmdString
}

def getOutput( String cmdString) {
    String retVal
    retVal = (isUnix())?sh(script: cmdString, returnStdout: true) : bat (script: cmdString, returnStdout: true)
    return retVal.trim()
}

def getStatus(String cmdString) {
    def retVal
    retVal = (isUnix())?sh(script: cmdString, returnStatus: true) : bat (script: cmdString, returnStatus: true)
    return retVal
}


def getList(Map propertyInfo, String cmdPrefix, Map cmdList) {
    propertyInfo.each {k, v ->
        if(k.contains("${cmdPrefix}")) {
            cmdList.put(k, v)
        }
    }

    propertyInfo.each {pInfoKey, pInfoValue ->
        cmdList.each { cmdKey, cmdValue ->
            if(cmdValue.contains('${' +pInfoKey+ '}')) {
                cmdList [cmdKey] = cmdValue.replaceAll('\\$\\{'+pInfoKey + '\\}', pInfoValue)
            }
        }
    }
}

return this