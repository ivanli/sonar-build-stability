/*
 * Sonar Build TeamCity Plugin
 * Copyright (C) 2015 Ivan Li
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.buildstability;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.Sensor;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.config.Settings;
import org.sonar.api.measures.Measure;
import org.sonar.api.measures.PropertiesBuilder;
import org.sonar.api.resources.Project;
import org.sonar.plugins.buildstability.ci.CiConnector;
import org.sonar.plugins.buildstability.ci.CiFactory;
import org.sonar.plugins.buildstability.ci.MavenCiConfiguration;
import org.sonar.plugins.buildstability.ci.api.Build;

import javax.annotation.Nullable;

import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Evgeny Mandrikov
 */
public class BuildStabilitySensor implements Sensor {
  private static final Logger LOG = LoggerFactory.getLogger(BuildStabilitySensor.class);

  public static final String DAYS_PROPERTY = "sonar.build-stability.days";
  public static final int DAYS_DEFAULT_VALUE = 30;
  public static final String USERNAME_PROPERTY = "sonar.build-stability.username.secured";
  public static final String PASSWORD_PROPERTY = "sonar.build-stability.password.secured";
  public static final String USE_JSECURITYCHECK_PROPERTY = "sonar.build-stability.use_jsecuritycheck";
  public static final boolean USE_JSECURITYCHECK_DEFAULT_VALUE = false;
  public static final String CI_URL_PROPERTY = "sonar.build-stability.url";

  private final Settings settings;
  private final MavenCiConfiguration mavenCiConfiguration;

  public BuildStabilitySensor(Settings settings, @Nullable MavenCiConfiguration mavenCiConfiguration) {
    this.settings = settings;
    this.mavenCiConfiguration = mavenCiConfiguration;
  }

  /**
   * In case we are not in a Maven build this constructor will be called
   */
  public BuildStabilitySensor(Settings settings) {
    this(settings, null /* Not in a Maven build */);
  }

  @Override
  public boolean shouldExecuteOnProject(Project project) {
    return project.isRoot() &&
      StringUtils.isNotEmpty(getCiUrl(project));
  }

  protected String getCiUrl(Project project) {
    String url = settings.getString(CI_URL_PROPERTY);
    if (StringUtils.isNotEmpty(url)) {
      return url;
    }
    if (mavenCiConfiguration != null
      && StringUtils.isNotEmpty(mavenCiConfiguration.getSystem())
      && StringUtils.isNotEmpty(mavenCiConfiguration.getUrl())) {
      return mavenCiConfiguration.getSystem() + ":" + mavenCiConfiguration.getUrl();
    }
    return null;
  }

  @Override
  public void analyse(Project project, SensorContext context) {
    String ciUrl = getCiUrl(project);
    LOG.info("CI URL: {}", ciUrl);
    String username = settings.getString(USERNAME_PROPERTY);
    String password = settings.getString(PASSWORD_PROPERTY);
    boolean useJSecurityCheck = settings.getBoolean(USE_JSECURITYCHECK_PROPERTY);
    List<Build> builds;
    try {
      CiConnector connector = CiFactory.create(ciUrl, username, password, useJSecurityCheck);
      if (connector == null) {
        LOG.warn("Unknown CiManagement system or incorrect URL: {}", ciUrl);
        return;
      }
      int daysToRetrieve = settings.getInt(DAYS_PROPERTY);
      Calendar calendar = Calendar.getInstance();
      calendar.add(Calendar.DAY_OF_MONTH, -daysToRetrieve);
      Date date = calendar.getTime();
      builds = connector.getBuildsSince(date);
      LOG.info("Retrieved {} builds since {}", builds.size(), date);
    } catch (Exception e) {
      LOG.error(e.getMessage(), e);
      return;
    }
    analyseBuilds(builds, context);
  }

  protected void analyseBuilds(List<Build> builds, SensorContext context) {
    Collections.sort(builds, new Comparator<Build>() {
      @Override
      public int compare(Build o1, Build o2) {
        return (int) (o1.getTimestamp() - o2.getTimestamp()); 
      }
    });

    PropertiesBuilder<String, Double> durationsBuilder = new PropertiesBuilder<String, Double>(BuildStabilityMetrics.DURATIONS);
    PropertiesBuilder<String, String> resultsBuilder = new PropertiesBuilder<String, String>(BuildStabilityMetrics.RESULTS);

    double successful = 0;
    double failed = 0;
    double duration = 0;
    double shortest = Double.POSITIVE_INFINITY;
    double longest = Double.NEGATIVE_INFINITY;

    double totalTimeToFix = 0;
    double totalBuildsToFix = 0;
    double longestTimeToFix = Double.NEGATIVE_INFINITY;
    int fixes = 0;
    Build firstFailed = null;

    for (Build build : builds) {
      LOG.debug("Analysing build: {}.", build.toString());

      String buildNumber = build.getNumberAsString();
      double buildDuration = build.getDuration();
      resultsBuilder.add(buildNumber, build.isSuccessful() ? "g" : "r");
      durationsBuilder.add(buildNumber, buildDuration / 1000);
      if (build.isSuccessful()) {
        LOG.debug("  Successful.");
        
        successful++;
        duration += buildDuration;
        shortest = Math.min(shortest, buildDuration);
        longest = Math.max(longest, buildDuration);
        
        if (firstFailed != null) {
          // Change in build state detected. Working out stats related to failure duration.
          LOG.debug("  First success after failure.");
          int buildsToFix = builds.indexOf(build) - builds.indexOf(firstFailed);
          totalBuildsToFix += buildsToFix;
          
          double timeToFix = build.getTimestamp() - firstFailed.getTimestamp();
          totalTimeToFix += timeToFix;
          longestTimeToFix = Math.max(longestTimeToFix, timeToFix);
          
          fixes++;
          firstFailed = null;
        }
      } else {        
        failed++;
        if (firstFailed == null)
          firstFailed = build;
      }
    }

    double count = successful + failed;

    context.saveMeasure(new Measure(BuildStabilityMetrics.BUILDS, count));
    context.saveMeasure(new Measure(BuildStabilityMetrics.FAILED, failed));
    context.saveMeasure(new Measure(BuildStabilityMetrics.SUCCESS_RATE, divide(successful, count) * 100));

    context.saveMeasure(new Measure(BuildStabilityMetrics.AVG_DURATION, divide(duration, successful)));
    context.saveMeasure(new Measure(BuildStabilityMetrics.LONGEST_DURATION, normalize(longest)));
    context.saveMeasure(new Measure(BuildStabilityMetrics.SHORTEST_DURATION, normalize(shortest)));

    context.saveMeasure(new Measure(BuildStabilityMetrics.AVG_TIME_TO_FIX, divide(totalTimeToFix, fixes)));
    context.saveMeasure(new Measure(BuildStabilityMetrics.LONGEST_TIME_TO_FIX, normalize(longestTimeToFix)));
    context.saveMeasure(new Measure(BuildStabilityMetrics.AVG_BUILDS_TO_FIX, divide(totalBuildsToFix, fixes)));

    if (!builds.isEmpty()) {
      context.saveMeasure(durationsBuilder.build());
      context.saveMeasure(resultsBuilder.build());
    }
  }

  private double normalize(double value) {
    return Double.isInfinite(value) ? 0 : value;
  }

  private double divide(double v1, double v2) {
    return Double.doubleToRawLongBits(v2) == 0 ? 0 : v1 / v2;
  }

  @Override
  public String toString() {
    return "Build Stability";
  }
}

// Saving compare algorithm 
//public int compare(Build o1, Build o2) {
//  final int MAJOR_VER_GROUP = 1;
//  final int MINOR_VER_GROUP = 2;
//  final int PATCH_VER_GROUP = 3;
//  
//  LOG.debug("Comparing builds: {} & {}", o1.getNumberAsString(), o2.getNumberAsString());
//  
//  // Use regex to search for X.X.X patterned version numbers
//  Pattern verPattern = Pattern.compile("([^.\\s]+)(?:\\.([^.\\s]+))?(?:\\.([^.\\s]+))?");
//  Matcher o1Matcher = verPattern.matcher(o1.getNumberAsString());
//  Matcher o2Matcher = verPattern.matcher(o2.getNumberAsString());
//  
//  // Check for incompatible patterns. Mismatch between number of sw version groups.
//  if ((o1Matcher.group(MINOR_VER_GROUP).isEmpty() && !o2Matcher.group(MINOR_VER_GROUP).isEmpty()) ||
//      (!o1Matcher.group(MINOR_VER_GROUP).isEmpty() && o2Matcher.group(MINOR_VER_GROUP).isEmpty()) ||
//      (!o1Matcher.group(PATCH_VER_GROUP).isEmpty() && o2Matcher.group(PATCH_VER_GROUP).isEmpty()) ||
//      (!o1Matcher.group(PATCH_VER_GROUP).isEmpty() && o2Matcher.group(PATCH_VER_GROUP).isEmpty()))
//  {
//    throw new NumberFormatException(MessageFormat.format("Build numbers formats do not match between {0} and {1}", o1.getNumberAsString(), o2.getNumberAsString()));
//  }
//  
//  int majorDiff = Integer.valueOf(o1Matcher.group(MAJOR_VER_GROUP)) - Integer.valueOf(o2Matcher.group(MAJOR_VER_GROUP));
//  if (majorDiff != 0 || o1Matcher.group(MINOR_VER_GROUP).isEmpty())
//    return majorDiff;
//  
//  int minorDiff = Integer.valueOf(o1Matcher.group(MINOR_VER_GROUP)) - Integer.valueOf(o2Matcher.group(MINOR_VER_GROUP));
//  if (minorDiff != 0 || o1Matcher.group(PATCH_VER_GROUP).isEmpty())
//    return minorDiff;
//  
//  int patchDiff = Integer.valueOf(o1Matcher.group(PATCH_VER_GROUP)) - Integer.valueOf(o2Matcher.group(PATCH_VER_GROUP));
//  if (patchDiff != 0)
//    return patchDiff;
//  
//  return 0;
//}