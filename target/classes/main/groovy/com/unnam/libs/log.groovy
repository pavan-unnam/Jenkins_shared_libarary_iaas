package  com.unnam.libs

def readData() {
    def infoJson, info = libraryResource 'com/unnam/cicd/data/info.json'
    def warnings = libraryResource 'com/unnam/cicd/data/warnings.json'
    def error = libraryResource 'com/unnam/cicd/data/error.json'

    infoJson = readJSON text:info
    warningsJson = readJSON text:warnings
    errorJson = readJSON text:error

    infoJson +=warningsJson
    infoJson +=errorJson

    return infoJson
}

def message (Map messageArgs) {
    def startColor
    if(!messageArgs.containsKey('messageTitle')) {
        messageArgs.messageTitle = "Information"
    }

    if(messageArgs.containsKey('messageNumber')) {
        if(LogData.instance.jsData.containsKey(messageArgs.messageNumber)) {
            writeLogMsg(messageArgs.messageNumber, messageArgs.messageTitle)
            debugMessage('Adding' + messageArgs.messageNumber + 'to message summary list')
            LogData.instance.messageUsedInPipeline.add(messageArgs.messageNumber)
        } else {
            ansiColor('xterm') {
                println "${common.ANSI_COLOR.YELLOW} *** WARNING **** \n message + ${messageArgs.messageNumber} not found;" +
                        "please refer this to the CICD Pipeline Squad ${common.ANSI_COLOR.OFF} \n"
            }
        }
    } else {
        ansiColor('xterm') {
            println "${common.ANSI_COLOR.YELLOW} *** WARNING **** \n no message number in pipeline logging system " +
                    "please refer this to the CICD Pipeline Squad ${common.ANSI_COLOR.OFF} \n"
        }
    }
}

void writeLogMsg(String messageNumber, String messageTitle) {
    switch (messageNumber) {
        case ~"INFO.*" :
            startColor = common.ANSI_COLOR.BLUE
            break
        case ~"WARNING.*" :
            startColor = common.ANSI_COLOR.YELLOW
            break
        case ~"ERROR.*" :
            startColor = common.ANSI_COLOR.RED
            break
    }

    StringBuilder msgString = new StringBuilder("\n ${common.ANSI_COLOR.RED} review the build log above for the actual error message." +
            "the following message is for context and to help assist in troubleshootng. ${common.ANSI_COLOR.OFF} " +
            "\n\n${startColor}**** ${messageTitle} **** ${common.ANSI_COLOR.OFF} \n Pipeline message number ${messageNumber}")

    def messageJson = LogData.instance.jsData[messageNumber]['messages']

    messageJson.each {key, value ->
        msgString.append('\n')
        msgString.append((key == 'Why is it being shown?') ? startColor +key + common.ANSI_COLOR.OFF : key)
        value.each {i, j ->
            msgString.append('\n\t' +((key == 'Why is it being shown?') ? startColor +i + ' ' + j + common.ANSI_COLOR.OFF : i + ' ' + j))
        }
    }

    if(params.debug) {
        def internalJson = LogData.instance.jsData[messageNumber]['internal']
        msgString.append('\n internal Data')
        internalJson.each {key, value ->
            msgString.append('\n\t' + key +': ' + value)
        }
    }

    msgString.append('\n\n')

    ansiColor('xterm') {
        println msgString
    }
}

void debugMessage (String message, String header = null, String color = 'BLUE') {
    if(params.debug) {
        String debugTags = "${common.ANSI_COLOR.OFF}[${common.ANSI_COLOR.EFXRED}EFX${common.ANSI_COLOR.BLACK}]" +
                "[${common.ANSI_COLOR.YELLOW}DEBUG${common.ANSI_COLOR.BLACK}]{common.ANSI_COLOR.OFF}"
        String customHeader = ' '
        if(header) {
            customHeader = "${common.ANSI_COLOR.BLACK}[${common.ANSI_COLOR."$color"}$header${common.ANSI_COLOR.BLACK}]" +
                    "]{common.ANSI_COLOR.OFF}"
        }

        debugTags += customHeader
        def output = []

        message.split('\n').each {msg ->
            output += "$debugTags$msg"
        }

        ansiColor('xterm') {
            println output.join('n')
        }
    }
}

void summarizeMessageProblemsFound () {
    if (LogData.instance.messageUsedInPipeline.isEmpty ()) {
        return
    }

    debugMessage("<---- Entering method", common.getCurrentMethodName())

    StringBuilder msgString = new StringBuilder("\n\n${common.ANSI_COLOR.RED}***the following warning & error" +
            "message were displayed in the build log ****${common.ANSI_COLOR.OFF}\n")
    msgString.append('You are strongly recommended to review the build log output immediately before & after each message\n')
    msgString.append('You can use the warning & error message numbers to search the build log for where the message are displayed\n')
    msgString.append('Review, research & attempt to fix errors in the build output before contacting the cicd team\n')

    LogData.instance.messageUsedInPipeline.each { m ->
        LogData.instance.jsData[m]['messages']['what is the problem ?'].eachWithIndex{ k, v, i ->
            if(i.equals(0)) {
                msgString.append(sprintf("%-11s :%s", m, k))
            } else {
                msgString.append(sprintf("%-11s :%s",' ', k))
            }

            msgString.append('\n')
        }
        msgString.append('\n')
    }

    ansiColor('xterm') {
        println msgString
    }

    debugMessage("<---- leaving method", common.getCurrentMethodName())

}

return this

