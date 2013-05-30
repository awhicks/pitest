/*
 * Copyright 2011 Henry Coles
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and limitations under the License. 
 */
package org.pitest.mutationtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyCollection;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pitest.classinfo.ClassIdentifier;
import org.pitest.classinfo.ClassInfo;
import org.pitest.classinfo.ClassInfoMother;
import org.pitest.classinfo.ClassName;
import org.pitest.classinfo.CodeSource;
import org.pitest.classinfo.HierarchicalClassId;
import org.pitest.coverage.CoverageDatabase;
import org.pitest.coverage.CoverageGenerator;
import org.pitest.functional.predicate.Predicate;
import org.pitest.help.Help;
import org.pitest.help.PitHelpError;
import org.pitest.internal.ClassByteArraySource;
import org.pitest.mutationtest.engine.Mutater;
import org.pitest.mutationtest.engine.MutationEngine;
import org.pitest.mutationtest.engine.gregor.GregorEngineFactory;
import org.pitest.mutationtest.incremental.HistoryStore;
import org.pitest.mutationtest.report.SourceLocator;
import org.pitest.mutationtest.statistics.MutationStatistics;
import org.pitest.mutationtest.verify.BuildVerifier;
import org.pitest.util.Unchecked;

public class MutationCoverageReportTest {

  private MutationCoverage       testee;

  private ReportOptions          data;

  @Mock
  private ListenerFactory        listenerFactory;

  @Mock
  private MutationResultListener listener;

  @Mock
  private CoverageDatabase       coverageDb;

  @Mock
  private CoverageGenerator      coverage;

  @Mock
  private CodeSource             code;

  @Mock
  private HistoryStore           history;

  @Mock
  private MutationEngineFactory  mutationFactory;

  @Mock
  private BuildVerifier          verifier;

  @Mock
  private MutationEngine         engine;

  @Mock
  private Mutater                mutater;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    this.data = new ReportOptions();
    this.data.setSourceDirs(Collections.<File> emptyList());
    // this.data.setMutators(Mutator.DEFAULTS.asCollection());
    when(this.coverage.calculateCoverage()).thenReturn(this.coverageDb);
    when(
        this.listenerFactory.getListener(any(CoverageDatabase.class),
            anyLong(), any(SourceLocator.class))).thenReturn(this.listener);
    mockMutationEngine();
  }

  @SuppressWarnings("unchecked")
  private void mockMutationEngine() {
    when(
        this.mutationFactory.createEngine(anyBoolean(), any(Predicate.class),
            anyCollection(), anyCollection(), anyBoolean())).thenReturn(
        this.engine);
    when(this.engine.createMutator(any(ClassByteArraySource.class)))
        .thenReturn(this.mutater);
  }

  @Test
  public void shouldReportErrorWhenNoMutationsFoundAndFlagSet() {
    try {
      this.data.setFailWhenNoMutations(true);
      createAndRunTestee();
    } catch (final PitHelpError phe) {
      assertEquals(Help.NO_MUTATIONS_FOUND.toString(), phe.getMessage());
    }
  }

  @Test
  public void shouldNotReportErrorWhenNoMutationsFoundAndFlagNotSet() {
    try {
      this.data.setFailWhenNoMutations(false);
      createAndRunTestee();
    } catch (final PitHelpError phe) {
      fail();
    }
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldRecordClassPath() {

    final HierarchicalClassId fooId = new HierarchicalClassId(
        new ClassIdentifier(0, ClassName.fromString("foo")), "0");
    final ClassInfo foo = ClassInfoMother.make(fooId.getId());

    when(this.code.getCodeUnderTestNames()).thenReturn(
        Collections.singleton(ClassName.fromString("foo")));
    when(this.code.getClassInfo(any(List.class))).thenReturn(
        Collections.singletonList(foo));

    createAndRunTestee();

    verify(this.history).recordClassPath(Arrays.asList(fooId), this.coverageDb);
  }

  @Test
  public void shouldCheckBuildSuitableForMutationTesting() {
    createAndRunTestee();
    verify(this.verifier).verify(any(CodeSource.class));
  }

  @Test
  public void shouldReportNoMutationsFoundWhenNoneDetected() {
    this.data.setFailWhenNoMutations(false);
    final MutationStatistics actual = createAndRunTestee();
    assertEquals(0, actual.getTotalMutations());
  }

  @Test
  public void shouldReportMutationsFoundWhenSomeDetected() {
    this.data.setFailWhenNoMutations(false);
    final ClassName foo = ClassName.fromString("foo");
    when(this.mutater.findMutations(foo)).thenReturn(
        Arrays.asList(MutationDetailsMother.makeMutation()));
    when(this.code.getCodeUnderTestNames()).thenReturn(
        Collections.singleton(foo));
    final MutationStatistics actual = createAndRunTestee();
    assertEquals(1, actual.getTotalMutations());
  }

  private MutationStatistics createAndRunTestee() {
    final MutationStrategies strategies = new MutationStrategies(
        new GregorEngineFactory(), this.history, this.coverage,
        this.listenerFactory).with(this.mutationFactory).with(this.verifier);

    this.testee = new MutationCoverage(strategies, null, this.code, this.data,
        new Timings());
    try {
      return this.testee.runReport();
    } catch (final IOException e) {
      throw Unchecked.translateCheckedException(e);
    }
  }

}
