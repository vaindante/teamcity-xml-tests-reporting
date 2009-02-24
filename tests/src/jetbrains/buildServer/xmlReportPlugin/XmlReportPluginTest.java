/*
 * Copyright 2008 JetBrains s.r.o.
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

package jetbrains.buildServer.xmlReportPlugin;

import jetbrains.buildServer.agent.AgentLifeCycleListener;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BaseServerLoggerFacade;
import jetbrains.buildServer.agent.BuildFinishedStatus;
import jetbrains.buildServer.agent.inspections.InspectionReporter;
import jetbrains.buildServer.util.EventDispatcher;
import static jetbrains.buildServer.xmlReportPlugin.TestUtil.ANT_JUNIT_REPORT_TYPE;
import junit.framework.Assert;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@RunWith(JMock.class)
public class XmlReportPluginTest {
  private static final String WORKING_DIR = "workingDir";

  private XmlReportPlugin myPlugin;
  private Map<String, String> myRunParams;
  private File myWorkingDir;
  private BaseServerLoggerFacade myLogger;
  private EventDispatcher<AgentLifeCycleListener> myEventDispatcher;

  private Mockery myContext;
  private Sequence mySequence;
  private InspectionReporter myInspectionReporter;

  private InspectionReporter createInspectionReporter() {
    return myContext.mock(InspectionReporter.class);
  }

//  private BuildParametersMap createBuildParametersMap(final Map<String, String> systemProperties) {
//    final BuildParametersMap params = myContext.mock(BuildParametersMap.class);
//    myContext.checking(new Expectations() {
//      {
//        oneOf(params).getSystemProperties();
//        will(returnValue(systemProperties));
//      }
//    });
//    return params;
//  }

  private AgentRunningBuild createAgentRunningBuild(final Map<String, String> runParams,
                                                    final File workingDirFile) {
    final AgentRunningBuild runningBuild = myContext.mock(AgentRunningBuild.class);
    myContext.checking(new Expectations() {
      {
        allowing(runningBuild).getWorkingDirectory();
        will(returnValue(workingDirFile));
        inSequence(mySequence);
        allowing(runningBuild).getBuildTempDirectory();
        inSequence(mySequence);
        allowing(runningBuild).getRunnerParameters();
        will(returnValue(runParams));
        inSequence(mySequence);
        allowing(runningBuild).getBuildLogger();
        will(returnValue(myLogger));
        inSequence(mySequence);
      }
    });
    return runningBuild;
  }

  private BaseServerLoggerFacade createBaseServerLoggerFacade() {
    return myContext.mock(BaseServerLoggerFacade.class);
  }

  @Before
  public void setUp() {
    myContext = new JUnit4Mockery() {
      {
        setImposteriser(ClassImposteriser.INSTANCE);
      }
    };
    mySequence = myContext.sequence("Log Sequence");
    myEventDispatcher = EventDispatcher.create(AgentLifeCycleListener.class);
    myRunParams = new HashMap<String, String>();
    myWorkingDir = new File(WORKING_DIR);
    myLogger = createBaseServerLoggerFacade();
    myInspectionReporter = createInspectionReporter();
    myContext.checking(new Expectations() {
      {
        ignoring(myInspectionReporter);
      }
    });
  }

  private void isSilentWhenDisabled(BuildFinishedStatus status) {
    XmlReportPluginUtil.enableXmlReportParsing(myRunParams, ANT_JUNIT_REPORT_TYPE);

    final AgentRunningBuild runningBuild = createAgentRunningBuild(myRunParams, myWorkingDir);
    myPlugin = new XmlReportPlugin(myEventDispatcher, myInspectionReporter);

    myEventDispatcher.getMulticaster().buildStarted(runningBuild);
    myEventDispatcher.getMulticaster().beforeRunnerStart(runningBuild);
    myEventDispatcher.getMulticaster().beforeBuildFinish(status);
    myContext.assertIsSatisfied();
  }

  @Test
  public void testIsSilentWhenDisabledNormalFinish() {
    isSilentWhenDisabled(BuildFinishedStatus.FINISHED_SUCCESS);
  }


  @Test
  public void testIsSilentWhenDisabledInterruptedFinish() {
    isSilentWhenDisabled(BuildFinishedStatus.INTERRUPTED);
  }

  @Test
  public void testIsSilentWhenDisabledDoesNotExistFinish() {
    isSilentWhenDisabled(BuildFinishedStatus.FINISHED_FAILED);
  }

  private void warningWhenZeroReportDirsSize() {
    final AgentRunningBuild runningBuild = createAgentRunningBuild(myRunParams, myWorkingDir);
    myContext.checking(new Expectations() {
      {
        oneOf(myLogger).warning(with(any(String.class)));
        ignoring(myLogger);
        inSequence(mySequence);
      }
    });
    myPlugin = new XmlReportPlugin(myEventDispatcher, myInspectionReporter);

    myEventDispatcher.getMulticaster().buildStarted(runningBuild);
    myEventDispatcher.getMulticaster().beforeRunnerStart(runningBuild);
    myContext.assertIsSatisfied();
  }

  @Test
  public void testWarningWhenReportDirsNull() {
    XmlReportPluginUtil.enableXmlReportParsing(myRunParams, ANT_JUNIT_REPORT_TYPE);
    XmlReportPluginUtil.setVerboseOutput(myRunParams, true);

    warningWhenZeroReportDirsSize();
  }

  @Test
  public void testIsStoppedWhenDisabled() {
    XmlReportPluginUtil.enableXmlReportParsing(myRunParams, ANT_JUNIT_REPORT_TYPE);

    final AgentRunningBuild runningBuild = createAgentRunningBuild(myRunParams, myWorkingDir);
    myPlugin = new XmlReportPlugin(myEventDispatcher, myInspectionReporter);

    myEventDispatcher.getMulticaster().buildStarted(runningBuild);
    myEventDispatcher.getMulticaster().beforeRunnerStart(runningBuild);
    myEventDispatcher.getMulticaster().beforeBuildFinish(BuildFinishedStatus.FINISHED_SUCCESS);
    myContext.assertIsSatisfied();

    Assert.assertTrue("Plugin must be stopped", myPlugin.isStopped());
  }

  private void isStoppedWhenZeroReportDirsSize() {
    final AgentRunningBuild runningBuild = createAgentRunningBuild(myRunParams, myWorkingDir);
    myContext.checking(new Expectations() {
      {
        ignoring(runningBuild);
        ignoring(myLogger);
      }
    });
    myPlugin = new XmlReportPlugin(myEventDispatcher, myInspectionReporter);

    myEventDispatcher.getMulticaster().buildStarted(runningBuild);
    myEventDispatcher.getMulticaster().beforeRunnerStart(runningBuild);
    myEventDispatcher.getMulticaster().beforeBuildFinish(BuildFinishedStatus.FINISHED_SUCCESS);
    myContext.assertIsSatisfied();

    Assert.assertTrue("Plugin must be stopped", myPlugin.isStopped());
  }

  @Test
  public void testIsStoppedWhenReportDirsEmpty() {
    XmlReportPluginUtil.enableXmlReportParsing(myRunParams, ANT_JUNIT_REPORT_TYPE);

    isStoppedWhenZeroReportDirsSize();
  }

  @Test
  public void testIsStoppedWhenReportDirsNull() {
    XmlReportPluginUtil.enableXmlReportParsing(myRunParams, ANT_JUNIT_REPORT_TYPE);

    isStoppedWhenZeroReportDirsSize();
  }

  //TODO: add tests for failure - must finish work!!!
}