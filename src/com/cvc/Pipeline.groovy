#!/usr/bin/groovy
package com.cvc;

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
