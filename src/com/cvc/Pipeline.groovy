#!/usr/bin/groovy
package com.cvc;

def consul(config, keyName) {
  def consulURL = config.consul[env.JOB_BASE_NAME]['url']
  def consulPrefix = config.consul.prefix
  def ssh = config.app[env.JOB_BASE_NAME]['sshJenkinsLab']

  def consulData = sh(
      script: "${ssh} curl -s http://${consulURL}/v1/kv/${consulPrefix}/${keyName} | jq '.[0].Value' | tr -d '\"'",
      returnStdout: true
  )

  def decodedData = sh(
      script: "echo '${consulData}' | base64 --decode",
      returnStdout: true
  )

  return decodedData
}

def notifyBuild(String message, String channel, String baseUrl, String tokenCredentialId, String buildStatus = 'STARTED') {
    buildStatus = buildStatus ?: 'SUCCESSFUL'
    def colorName = 'RED'
    def colorCode = '#FF0000'
    def subject = "${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'"
    def summary = "${subject} (${env.JOB_URL})"

    // Override default values based on build status
    if (buildStatus == 'STARTED') {
        colorCode = '#FFFF00'
    } else if (buildStatus == 'SUCCESSFUL' || buildStatus == 'SUCCESS') {
        colorCode = '#00FF00'
    } else {
        colorCode = '#FF0000'
    }

    slackSend(channel: channel,
              baseUrl: baseUrl,
              tokenCredentialId: tokenCredentialId,
              color:"${colorCode}",
              message: "${summary} ${message}")
}

def getContainerTags(config, Map tags = [:]) {
    println "getting list of tags for container"
    def String commit_tag
    def String version_tag

    // commit tag
    try {
        // if branch available, use as prefix, otherwise only commit hash
        if (env.BRANCH_NAME) {
            commit_tag = env.BRANCH_NAME + '-' + env.GIT_COMMIT_ID.substring(0, 7)
        } else {
            commit_tag = env.GIT_COMMIT_ID.substring(0, 7)
        }
        tags << ['commit': commit_tag]
    } catch (Exception e) {
        println "WARNING: commit unavailable from env. ${e}"
    }

    // build tag only if none of the above are available
    if (!tags) {
        try {
            tags << ['build': env.BUILD_TAG]
        } catch (Exception e) {
            println "WARNING: build tag unavailable from config.project. ${e}"
        }
    }

    return tags
}
