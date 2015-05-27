/*
 * Sonar Build Stability Plugin
 * Copyright (C) 2010 SonarSource
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
package org.sonar.plugins.buildstability.ci;

import org.junit.Test;
import org.sonar.plugins.buildstability.ci.teamcity.TeamCityServer;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Julien HENRY
 */
public class CiFactoryTest {

  @Test
  public void testCreateTeamCity() {
    CiConnector connector = CiFactory.create("TeamCity", "http://teamcity:port/viewType.html?buildTypeId=SonarBuildStability_Install", "user", "pwd", false);

    assertThat(connector.getServer().getUsername()).isEqualTo("user");
    assertThat(connector.getServer().getPassword()).isEqualTo("pwd");
    assertThat(connector.getServer().getHost()).isEqualTo("http://teamcity:port");
    assertThat(connector.getServer().getKey()).isEqualTo("SonarBuildStability_Install");
    assertThat(connector.getServer()).isInstanceOf(TeamCityServer.class);
  }
}
