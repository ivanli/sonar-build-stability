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
package org.sonar.plugins.buildstability.ci.api;

import java.util.List;

import org.dom4j.Element;

/**
 * @author Evgeny Mandrikov
 */
public interface Unmarshaller<MODEL extends Model> {
  MODEL toModel(Element domElement);
  List<MODEL> toManyModel(Element domElement);
}
