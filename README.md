Build Stability TeamCity Plugin
======================

This is a forked from the original: https://github.com/SonarCommunity/sonar-build-stability.

## Description / Features

Generates reports based on information about builds from Continuous Integration System. Changes have been made to support a wider feature set from TeamCity,
including custom version number format, and multiple builds. 

## Requirements

<table>
<tr><td>Plugin</td><td>1.0</td></tr>
<tr><td>SonarQube</td><td>4.5.2+</td></tr>
<tr><td>Bamboo</td><td>:red_circle:</td></tr>
<tr><td>Jenkins</td><td>:red_circle:</td></tr>
<tr><td>Hudson</td><td>:red_circle:</td></tr>
<tr><td>TeamCity</td><td>:white_check_mark:</td></tr>
</table>

## Usage

Specify your Continuous Integration Server Job (URL, credentials, etc.) through the web interface: at project level, go to Configuration > Settings > Build Stability.

Alternatively, pass it into SonarQube via it's config files:

```
sonar.build-stability.url=Bamboo:${BAMBOO_URL}/browse/${PROJECT_KEY}
sonar.build-stability.url=Jenkins:${JENKINS_URL}/job/${JOB_NAME}
sonar.build-stability.url=TeamCity:${TEAMCITY_URL}/viewType.html?buildTypeId=${PROJECT_KEY}
```

Run a new quality analysis and the metrics will be fed.

