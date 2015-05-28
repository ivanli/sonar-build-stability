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
package org.sonar.plugins.buildstability.ci.teamcity;

import org.dom4j.Element;
import org.dom4j.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.plugins.buildstability.ci.api.Build;
import org.sonar.plugins.buildstability.ci.api.Unmarshaller;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Alexei Guevara <alguevara@kijiji.ca>
 */
public class TeamCityBuildUnmarshaller implements Unmarshaller<Build> {

  private static final Logger LOG = LoggerFactory.getLogger(TeamCityBuildUnmarshaller.class);

  /**
   * TeamCity date-time format. Example: 20131124T053500+0000
   */
  private static final String DATE_TIME_FORMAT = "yyyyMMdd'T'HHmmssZ";

  @Override
  public Build toModel(Element domElement) {
    Build build = new Build();

    String result = domElement.attributeValue("status");

    LOG.debug("Parsing build detail: number: {}", domElement.attributeValue("number"));
    
    build.setNumber(domElement.attributeValue("number"));
    build.setTimestamp(getTimeStamp(domElement.elementText("startDate")));
    build.setResult(result);
    build.setDuration(calculateDuration(domElement.elementText("startDate"), domElement.elementText("finishDate")));
    build.setSuccessful("SUCCESS".equalsIgnoreCase(result));

    return build;
  }

  @Override
  public List<Build> toManyModel(Element domElement) {
    List<Build> builds = new ArrayList<Build>();
    
    List<? extends Element> buildElements = domElement.elements();
    for (Element e : buildElements) {      
      LOG.debug("Parsing build summary: number: {}", e.attributeValue("number"));
      
      Build build = new Build();
      build.setNumber(e.attributeValue("number"));
      builds.add(build);
    }
    
    return builds;
  }

  private long getTimeStamp(String startDate) {
    Date start = parseDate(startDate);
    return start == null ? 0 : start.getTime();
  }

  private double calculateDuration(String startDate, String endDate) {
    Date start = parseDate(startDate);
    Date end = parseDate(endDate);

    if (start == null || end == null) {
      return 0;
    } else {
      return end.getTime() - start.getTime();
    }
  }

  private Date parseDate(String date) {
    SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_TIME_FORMAT);
    try {
      return dateFormat.parse(date);
    } catch (ParseException e) {
      LOG.warn("Unable to parse date {}. Expected format is {}", date, DATE_TIME_FORMAT);
      return null;
    }
  }


}
