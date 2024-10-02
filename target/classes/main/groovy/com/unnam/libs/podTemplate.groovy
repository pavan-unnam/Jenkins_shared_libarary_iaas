package com.unnam.libs

import groovy.transform.Field
import hudson.model.Label
import jenkins.model.Jenkins


@Field
def log = new log()

@Field
def status = new status()

def getInjectedYaml(Map propertyInfo) {
    def podTemplate, podTemplateYaml = ''
    String label = propertyInfo.build_agent_label, yaml = ''
    Boolean repackEnabled = propertyInfo.enablePackAutomation

    label = label == null || label == '' ? "build-pod" : label
    repackEnabled = repackEnabled ? repackEnabled.toBoolean() : false

    def cloud = Jenkins.get().clouds.find {cloud ->
        podTemplate = cloud.getTemplate(Label.get(label))
    }

    propertyInfo.cloud = cloud?.name
    cloud = null

    if(podTemplate) {
        try {
            podTemplateYaml = readYaml text : podTemplate.getYaml()
        }catch (Exception ex) {
            log.message(messageNumber: 'ERROR0074', messageTitle: "Yaml file is not read")
            println 'Exception message:\n' +ex.getMessage()
            status.setStage(common.JEKINS_STATUS.FAILURE)
            status.setJob(common.JEKINS_STATUS.FAILURE)
        }
    } else {
        log.message(messageNumber: 'ERROR0075', messageTitle: "Pod template not found")
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE)
    }

    if(repackEnabled) {
        podTemplateYaml.metadata << [labels : [repack : "true"]]
        podTemplateYaml.metadata << [annotations : ['traffic.sidecar.istio.io/excludeOutboundIPRanges' : "0.0.0.0/0"]]
        log.debugMessage ("Repack enabled. Istio injection will occur for pod template.", common.getCuurentMethodName())
    } else{
        log.debugMessage ("Repack disabled. No istio-proxy injection will occur.", common.getCuurentMethodName())
    }

    try {
        yaml = writeYaml data: podTemplateYaml, returnText: true
    }catch (Exception ex) {
        log.message(messageNumber: 'ERROR0074', messageTitle: "Yaml file is not read")
        println 'Exception message:\n' +ex.getMessage()
        status.setStage(common.JEKINS_STATUS.FAILURE)
        status.setJob(common.JEKINS_STATUS.FAILURE)
    }

    return yaml
}

return this