package com.unnam.libs.deploy

interface OctopusInterface {

    boolean isSpaceFeatureSupportedByOctopusServer(String url)

    void setDeploymentOptions(Map propertyInfo)

    void createRelease(String url, String space, String apiKeyId, String octoPath, String project, String releaseChannel, String appInfoVersion, String extraArguments)

    void deployRelease(String url, String space, String apiKeyId, String project, String releaseChannel, String appInfoVersion, String defaultDeployToEnvironment, String deploymentTimeout, String octoPath, String extraArguments)

}