package com.unnam.libs.deploy

class Octopus implements OctopusInterface {
    private Script steps
    private Object common
    public static final String[] OCTOPUS_DEPLOY_SERVICES_WITH_SPACES_FEATURE = ['od.us.equifax.com']

    Octopus(Script steps) {
        this.steps = steps
        this.common = steps.common
    }

    @Override
    boolean isSpaceFeatureSupportedByOctopusServer(String url) {
        return null != OCTOPUS_DEPLOY_SERVICES_WITH_SPACES_FEATURE.find{server -> url.contains(server)}
    }

    @Override
    void setDeploymentOptions(Map propertyInfo) {
        propertyInfo.deploymentTimeout = (propertyInfo.deploymentTimeout == null || propertyInfo.deploymentTimeout == '') ? "01:00:00" : propertyInfo.deploymentTimeout.trim()
        propertyInfo.releaseChannel = (propertyInfo.releaseChannel == null || propertyInfo.releaseChannel == '') ? "DEFAULT" : propertyInfo.releaseChannel.trim()
        propertyInfo.octoPath = (propertyInfo.octoPath == null || propertyInfo.octoPath == '') ? "c:/bin/Octo.exe" : propertyInfo.octoPath.trim()
        propertyInfo.octopusCreateReleaseArgs = (propertyInfo.octopusCreateReleaseArgs == null || propertyInfo.octopusCreateReleaseArgs == '') ? '' : propertyInfo.octopusCreateReleaseArgs.trim()
        propertyInfo.octopusDeployReleaseArgs = (propertyInfo.octopusDeployReleaseArgs == null || propertyInfo.octopusDeployReleaseArgs == '') ? '' : propertyInfo.octopusDeployReleaseArgs.trim()
    }

    @Override
    void createRelease(String url, String space, String apiKeyId, String octoPath, String project, String releaseChannel, String appInfoVersion, String extraArguments) {
        String spaceCommand = ''
        if(isSpaceFeatureSupportedByOctopusServer(url)) {
            if(!space) {
                common.logMsg(logLevel: common.LOG_LEVEL.WARNING, msgString: "The Octopus server at ${url} uses the spaces feature" +
                        "\nPlease provide your space with the octopusSpace build_option.\n", stageResult: common.JEKINS_STATUS.UNSTABLE, buildResult: steps.currentBuild.currentResult)
            }else {
                spaceCommand = """--space `"${space}`" """
            }
        }

        steps.withCredentials([steps.string(credentialsId: apiKeyId, variable: 'OCTOPUS_CLI_API_KEY')]) {
            steps.powershell """& ${octoPath} create-release --project `"${project}`" `--channel `"${releaseChannel}`" `--releaseNumber `"${appInfoVersion}`" `--packageversion `"${appInfoVersion}`" `--server ${url} ${spaceCommand}` ${extraArguments} """
        }
    }

    @Override
    void deployRelease(String url, String space, String apiKeyId, String project, String releaseChannel, String appInfoVersion, String defaultDeployToEnvironment, String deploymentTimeout, String octoPath, String extraArguments) {
        String spaceCommand = ''
        if(isSpaceFeatureSupportedByOctopusServer(url)) {
            if(!space) {
                common.logMsg(logLevel: common.LOG_LEVEL.WARNING, msgString: "The Octopus server at ${url} uses the spaces feature" +
                        "\nPlease provide your space with the octopusSpace build_option.\n", stageResult: common.JEKINS_STATUS.UNSTABLE, buildResult: steps.currentBuild.currentResult)
            }else {
                spaceCommand = """--space `"${space}`" """
            }
        }

        steps.withCredentials([steps.string(credentialsId: apiKeyId, variable: 'OCTOPUS_CLI_API_KEY')]) {
            steps.powershell """& ${octoPath} deploy-release --project `"${project}`" `--releaseNumber `"${appInfoVersion}`" `--deployto `"${defaultDeployToEnvironment}`" `--server ${url} --progress --waitfordeployment `--deploymenttimeout=${deploymentTimeout} ${spaceCommand}   ${extraArguments} """
        }
    }
}
