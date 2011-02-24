/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.xmlReportPlugin.parsers.pmd;

import java.util.Arrays;
import java.util.List;
import jetbrains.buildServer.util.XmlXppAbstractParser;
import jetbrains.buildServer.xmlReportPlugin.inspections.InspectionResult;
import jetbrains.buildServer.xmlReportPlugin.inspections.InspectionTypeResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: vbedrosova
 * Date: 17.02.11
 * Time: 13:29
 */
public class PmdXmlReportParser extends XmlXppAbstractParser {
  @NotNull
  private final Callback myCallback;

  public PmdXmlReportParser(@NotNull Callback callback) {
    myCallback = callback;
  }

  @Override
  protected List<XmlHandler> getRootHandlers() {
    return Arrays.asList(elementsPath(new Handler() {
      public XmlReturn processElement(@NotNull final XmlElementInfo reader) {
        return reader.visitChildren(elementsPath(new Handler() {
          public XmlReturn processElement(@NotNull final XmlElementInfo reader) {
            final String file = reader.getAttribute("name");

            return reader.visitChildren(elementsPath(new Handler() {
              public XmlReturn processElement(@NotNull final XmlElementInfo reader) {
                myCallback.reportInspectionType(
                  new InspectionTypeResult(reader.getAttribute("rule"), reader.getAttribute("rule"),
                                         reader.getAttribute("ruleset"), reader.getAttribute("ruleset")));

                return reader.visitText(new TextHandler() {
                  public void setText(@NotNull final String text) {
                    myCallback.reportInspection(
                      new InspectionResult(file, reader.getAttribute("rule"), text.trim(),
                                         getInt(reader.getAttribute("beginline")),
                                         getInt(reader.getAttribute("priority"))));
                  }
                });
              }
            }, "violation"));
          }
        }, "file"));
      }
    }, "pmd"));
  }

  private static int getInt(@Nullable String val) {
    try {
      return val == null ? 0 : Integer.parseInt(val);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  public static interface Callback {
    void reportInspection(@NotNull InspectionResult inspection);
    void reportInspectionType(@NotNull InspectionTypeResult inspectionType);
  }
}