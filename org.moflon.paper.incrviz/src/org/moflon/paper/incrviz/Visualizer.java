package org.moflon.paper.incrviz;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.util.Pair;
import org.apache.log4j.Logger;
import org.eclipse.core.resources.IResource;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.moflon.core.utilities.eMoflonEMFUtil;
import org.moflon.ide.visualisation.dot.language.EMoflonDiagramTextProvider;
import org.moflon.ide.visualisation.dot.language.EMoflonDiagramTextProvider.Statistics;
import org.moflon.ide.visualization.dot.sdm.SDMDiagramTextProvider;
import org.moflon.ide.visualization.dot.sdmpatterns.StoryPatternDiagramTextProvider;
import org.moflon.ide.visualization.dot.tgg.TGGRuleDiagramTextProvider;
import org.moflon.ide.visualization.dot.tgg.runtime.DotTGGRuntimeDiagramTextProvider;
import org.moflon.ide.visualization.dot.tgg.runtimepatterns.DotTGGRuntimePatternsDiagramTextProvider;
import org.moflon.tgg.language.TGGRule;

import SDMLanguage.patterns.ObjectVariable;

public class Visualizer
{
   private static final String TRIPLE_MATCH_TYPE = "tripleMatch";

   private static final String PRECEDENCE_GRAPH_TYPE = "pg";

   private static final String STORY_PATTERN_TYPE = "storyPattern";

   private static final String TGG_RULE_TYPE = "tggRule";

   private static final String SDM_TYPE = "sdm";

   private final Logger logger = Logger.getLogger(Visualizer.class);

   private static final Visualizer INSTANCE = new Visualizer();

   private static final String CSV_SEP = ";";

   private static final String VALUE_FOR_NON_AVAILABLE_CELLS = "NA";

   private final File targetFile;

   private final File rootFolder;

   private final SDMDiagramTextProvider sdmDiagramTextProvider = new SDMDiagramTextProvider();

   private final StoryPatternDiagramTextProvider storyPatternDiagramTextProvider = new StoryPatternDiagramTextProvider();

   private final TGGRuleDiagramTextProvider tggRuleDiagramTextProvider = new TGGRuleDiagramTextProvider();

   private final DotTGGRuntimeDiagramTextProvider precendenceGraphTextProvider = new DotTGGRuntimeDiagramTextProvider();

   private final DotTGGRuntimePatternsDiagramTextProvider tripleMatchTextProvider = new DotTGGRuntimePatternsDiagramTextProvider();

   private int repetitionCountOfBatchTransformation;

   private int repetitionCountOfIncrementalTransformation;

   private List<String> headerOfResultsFile;

   private int lengthOfHeaderWithRQ1;

   private int visualizedItemCounter;

   private Visualizer()
   {
      this.rootFolder = determineRootFolder();

      this.logger.info("Using root folder: " + this.rootFolder);

      this.targetFile = new File(rootFolder, "incrviz_results.csv");
      this.repetitionCountOfBatchTransformation = 5;
      this.repetitionCountOfIncrementalTransformation = 5;
      this.headerOfResultsFile = getHeaderOfResultsFile();
      this.visualizedItemCounter = 0;
   }

   public void visualize(final EObject element, final IResource resource) throws IOException
   {
      createRootFolderIfNecessary();
      addTitlesIfNecessary();
      final EMoflonDiagramTextProvider diagramTextProvider = determineDiagramTextProvider(element);

      if (diagramTextProvider == null)
      {
         // Ignore elements that we cannot handle
         return;
      }

      ++this.visualizedItemCounter;

      final String type = determineTypeOfModel(element);

      /*
       * *** General statistics
       */
      final String objectId = calculateNormalizedIdentifier(element);
      final int objectNodeCount = eMoflonEMFUtil.getNodeCount(element);
      final int objectEdgeCount = eMoflonEMFUtil.getEdgeCount(element);
      final int objectSize = objectEdgeCount + objectNodeCount;
      final String formattedDate = new SimpleDateFormat("yyyy-MM-dd+HH-mm-ss").format(new Date());
      final List<Object> columns = new ArrayList<>();
      final String pathToResource = String.format("[Project=%s,Path=%s]", resource.getProject().getName(), resource.getProjectRelativePath())
            .replaceAll(CSV_SEP, "_");
      columns.addAll(Arrays.asList(formattedDate, pathToResource, type, objectId, objectNodeCount, objectEdgeCount, objectSize));

      /*
       * *** RQ1: Initial batch transformation
       */
      final DescriptiveStatistics initialBatchExecutionStatistics = new DescriptiveStatistics();

      Statistics statistics = null;
      for (int i = 0; i < this.repetitionCountOfBatchTransformation; ++i)
      {
         diagramTextProvider.clearCache();
         diagramTextProvider.modelToDot(element); // Batch transformation
         statistics = diagramTextProvider.getStatisticsOfLastRun();
         initialBatchExecutionStatistics.addValue(statistics.durationInMillis);
      }

      // Add columns for initial batch execution
      final String meanBatchExecutionTime = formatTimeInMillis(initialBatchExecutionStatistics.getMean());
      final String medianBatchExecutionTime = formatTimeInMillis(median(initialBatchExecutionStatistics));
      appendValuesOfStatisticsToColumns(columns, initialBatchExecutionStatistics);
      columns.add(meanBatchExecutionTime);
      columns.add(medianBatchExecutionTime);

      /*
       * *** RQ2: Synchronize changes
       */
      if (element instanceof TGGRule)
      {
         this.performModificationsForRQ2(diagramTextProvider, columns, (TGGRule) element);

         if (columns.size() != getLengthOfHeaderForRQ1andRQ2())
            throw new IllegalStateException("Invalid column count: " + columns.size() + ". Header (for RQ1+RQ2): " + headerOfResultsFile.size());
      } else
      {
         if (columns.size() != getLengthOfHeaderForRQ1Only())
            throw new IllegalStateException("Invalid column count: " + columns.size() + ". Header (for RQ1): " + lengthOfHeaderWithRQ1);
         padColumnsToLengthOfFullHeader(columns);
      }

      logger.info("#" + String.format("%05d", this.visualizedItemCounter) + " " + type + " : " + pathToResource + "::" + objectId + " - E: " + objectEdgeCount
            + " - V: " + objectNodeCount + " - median t: " + medianBatchExecutionTime + " - mean t: " + meanBatchExecutionTime);

      FileUtils.writeLines(this.targetFile, Arrays.asList(joinWithCsvSeparator(columns)), true);
   }

   private void padColumnsToLengthOfFullHeader(List<Object> columns)
   {
      final int differenceInLength = getLengthOfHeaderForRQ1andRQ2() - getLengthOfHeaderForRQ1Only();
      for (int i = 0; i < differenceInLength; ++i)
      {
         columns.add(VALUE_FOR_NON_AVAILABLE_CELLS);
      }
      assert columns.size() == getLengthOfHeaderForRQ1andRQ2();
   }

   private void performModificationsForRQ2(final EMoflonDiagramTextProvider diagramTextProvider, final List<Object> columns, final TGGRule originalRule)
   {
      final DescriptiveStatistics addedObjectVariablesIncrementalStatistics = new DescriptiveStatistics();
      final DescriptiveStatistics addedObjectVariablesBatchStatistics = new DescriptiveStatistics();
      final DescriptiveStatistics renamedObjectVariablesIncrementalStatistics = new DescriptiveStatistics();
      final DescriptiveStatistics renamedObjectVariablesBatchStatistics = new DescriptiveStatistics();
      final DescriptiveStatistics removedObjectVariablesIncrementalStatistics = new DescriptiveStatistics();
      final DescriptiveStatistics removedObjectVariablesBatchStatistics = new DescriptiveStatistics();
      final DescriptiveStatistics changeSequenceIncrementalStatistics = new DescriptiveStatistics();
      final DescriptiveStatistics changeSequenceBatchStatistics = new DescriptiveStatistics();

      for (int i = 0; i < this.repetitionCountOfIncrementalTransformation; ++i)
      {
         final EvaluationTGGRuleModifier evaluationTGGRuleModifier = new EvaluationTGGRuleModifier(new Random(1));
         final TGGRule tggRule = createDeepCopy(originalRule);

         diagramTextProvider.modelToDot(tggRule);

         {
            // Change (i)
            List<ObjectVariable> addedObjectVariables = evaluationTGGRuleModifier.performChangeAddObjectVariables(tggRule, 3);
            try
            {
               // diagramTextProvider.getSynchronizationHelperForObject(tggRule).saveSrc("C:\\tmp\\change1-srcBefore.xmi");
               // diagramTextProvider.getSynchronizationHelperForObject(tggRule).saveTrg("C:\\tmp\\change1-trgBefore.xmi");
            } catch (Exception e)
            {
               logger.error("[Pre-Change 1] Problem while saving model " + eMoflonEMFUtil.getIdentifier(tggRule), e);
            }
            diagramTextProvider.modelToDot(tggRule); // Incremental
            try
            {
               // diagramTextProvider.getSynchronizationHelperForObject(tggRule).saveSrc("C:\\tmp\\change1-srcAfter.xmi");
               // diagramTextProvider.getSynchronizationHelperForObject(tggRule).saveTrg("C:\\tmp\\change1-trgAfter.xmi");
            } catch (Exception e)
            {
               logger.error("[Post-Change 1] Problem while saving model " + eMoflonEMFUtil.getIdentifier(tggRule), e);
            }
            final double change1TimeIncremental = diagramTextProvider.getStatisticsOfLastRun().durationInMillis;
            diagramTextProvider.clearCache();
            diagramTextProvider.modelToDot(tggRule); // Batch
            final double change1TimeBatch = diagramTextProvider.getStatisticsOfLastRun().durationInMillis;
            addedObjectVariablesIncrementalStatistics.addValue(change1TimeIncremental);
            addedObjectVariablesBatchStatistics.addValue(change1TimeBatch);

            // Change (ii)
            ObjectVariable renamedObjectVariable = evaluationTGGRuleModifier.performChangeObjectVariableNameChange(tggRule);
            try
            {
               // diagramTextProvider.getSynchronizationHelperForObject(tggRule).saveSrc("C:\\tmp\\change2-srcBefore.xmi");
               // diagramTextProvider.getSynchronizationHelperForObject(tggRule).saveTrg("C:\\tmp\\change2-trgBefore.xmi");
            } catch (Exception e)
            {
               logger.error("[Pre-Change 2] Problem while saving model " + eMoflonEMFUtil.getIdentifier(tggRule), e);
            }
            diagramTextProvider.modelToDot(tggRule); // Incremental
            try
            {
               // diagramTextProvider.getSynchronizationHelperForObject(tggRule).saveSrc("C:\\tmp\\change2-srcAfter.xmi");
               // diagramTextProvider.getSynchronizationHelperForObject(tggRule).saveTrg("C:\\tmp\\change2-trgAfter.xmi");
            } catch (Exception e)
            {
               logger.error("[Post-Change 2] Problem while saving model " + eMoflonEMFUtil.getIdentifier(tggRule), e);
            }
            final double change2TimeIncremental = diagramTextProvider.getStatisticsOfLastRun().durationInMillis;
            diagramTextProvider.clearCache();
            diagramTextProvider.modelToDot(tggRule); // Batch
            final double change2TimeBatch = diagramTextProvider.getStatisticsOfLastRun().durationInMillis;
            renamedObjectVariablesIncrementalStatistics.addValue(change2TimeIncremental);
            renamedObjectVariablesBatchStatistics.addValue(change2TimeBatch);

            // Change (iii)
            List<ObjectVariable> removedObjectVariables = evaluationTGGRuleModifier.performRemoveObjectVariable(tggRule, 2);
            try
            {
               // diagramTextProvider.getSynchronizationHelperForObject(tggRule).saveSrc("C:\\tmp\\change3-srcBefore.xmi");
               // diagramTextProvider.getSynchronizationHelperForObject(tggRule).saveTrg("C:\\tmp\\change3-trgBefore.xmi");
            } catch (Exception e)
            {
               logger.error("[Pre-Change 3] Problem while saving model " + eMoflonEMFUtil.getIdentifier(tggRule), e);
            }
            diagramTextProvider.modelToDot(tggRule); // Incremental
            try
            {
               // diagramTextProvider.getSynchronizationHelperForObject(tggRule).saveSrc("C:\\tmp\\change3-srcAfter.xmi");
               // diagramTextProvider.getSynchronizationHelperForObject(tggRule).saveTrg("C:\\tmp\\change3-trgAfter.xmi");
            } catch (Exception e)
            {
               logger.error("[Post-Change 3] Problem while saving model " + eMoflonEMFUtil.getIdentifier(tggRule), e);
            }
            final double change3TimeIncremental = diagramTextProvider.getStatisticsOfLastRun().durationInMillis;
            diagramTextProvider.clearCache();
            diagramTextProvider.modelToDot(tggRule); // Batch
            final double change3TimeBatch = diagramTextProvider.getStatisticsOfLastRun().durationInMillis;
            removedObjectVariablesIncrementalStatistics.addValue(change3TimeIncremental);
            removedObjectVariablesBatchStatistics.addValue(change3TimeBatch);

            undoModifications(tggRule, diagramTextProvider, evaluationTGGRuleModifier, tggRule, addedObjectVariables, renamedObjectVariable,
                  removedObjectVariables);
         }

         {
            // Changes (i)+(ii)+(iii) combined
            final List<ObjectVariable> addedObjectVariables = evaluationTGGRuleModifier.performChangeAddObjectVariables(tggRule, 3);
            final ObjectVariable renamedObjectVariable = evaluationTGGRuleModifier.performChangeObjectVariableNameChange(tggRule);
            final List<ObjectVariable> removedObjectVariables = evaluationTGGRuleModifier.performRemoveObjectVariable(tggRule, 2);
            diagramTextProvider.modelToDot(tggRule); // Incremental
            final double changes123TimeIncremental = diagramTextProvider.getStatisticsOfLastRun().durationInMillis;
            diagramTextProvider.clearCache();
            diagramTextProvider.modelToDot(tggRule); // Batch
            final double changes123TimeBatch = diagramTextProvider.getStatisticsOfLastRun().durationInMillis;
            changeSequenceIncrementalStatistics.addValue(changes123TimeIncremental);
            changeSequenceBatchStatistics.addValue(changes123TimeBatch);

            undoModifications(tggRule, diagramTextProvider, evaluationTGGRuleModifier, tggRule, addedObjectVariables, renamedObjectVariable,
                  removedObjectVariables);
         }
      } // repetitions of RQ2 tests

      for (final Pair<DescriptiveStatistics, DescriptiveStatistics> statisticsPair : Arrays.asList(
            Pair.create(addedObjectVariablesIncrementalStatistics, addedObjectVariablesBatchStatistics),
            Pair.create(renamedObjectVariablesIncrementalStatistics, renamedObjectVariablesBatchStatistics),
            Pair.create(removedObjectVariablesIncrementalStatistics, removedObjectVariablesBatchStatistics),
            Pair.create(changeSequenceIncrementalStatistics, changeSequenceBatchStatistics)))
      {
         final DescriptiveStatistics statisticsForIncrementalRun = statisticsPair.getFirst();
         final DescriptiveStatistics statisticsForBatchRun = statisticsPair.getSecond();

         final double statisticsForIncrementalRunMedian = median(statisticsForIncrementalRun);
         final double statisticsForBatchRunMedian = median(statisticsForBatchRun);
         final double statisticsForIncrementalRunMean = statisticsForIncrementalRun.getMean();
         final double statisticsForBatchRunMean = statisticsForBatchRun.getMean();

         appendValuesOfStatisticsToColumns(columns, statisticsForIncrementalRun);
         appendValuesOfStatisticsToColumns(columns, statisticsForBatchRun);
         columns.add(formatTimeInMillis(statisticsForIncrementalRunMean));
         columns.add(formatTimeInMillis(statisticsForIncrementalRunMedian));
         columns.add(formatTimeInMillis(statisticsForBatchRunMean));
         columns.add(formatTimeInMillis(statisticsForBatchRunMedian));
         columns.add(statisticsForIncrementalRunMean / statisticsForBatchRunMean);
         columns.add(statisticsForIncrementalRunMedian / statisticsForBatchRunMedian);
      }

      final double sumOfRuntimeForIndividualChangesIncrementalMean = Arrays
            .asList(addedObjectVariablesIncrementalStatistics, renamedObjectVariablesIncrementalStatistics, removedObjectVariablesIncrementalStatistics)
            .stream().map(DescriptiveStatistics::getMean).reduce((a, b) -> a + b).get();

      final double sumOfRuntimeForIndividualChangesIncrementalMedian = Arrays
            .asList(addedObjectVariablesIncrementalStatistics, renamedObjectVariablesIncrementalStatistics, removedObjectVariablesIncrementalStatistics)
            .stream().map(Visualizer::median).reduce((a, b) -> a + b).get();

      columns.add(formatTimeInMillis(sumOfRuntimeForIndividualChangesIncrementalMean));
      columns.add(formatTimeInMillis(sumOfRuntimeForIndividualChangesIncrementalMedian));
      columns.add(changeSequenceIncrementalStatistics.getMean() / sumOfRuntimeForIndividualChangesIncrementalMean);
      columns.add(median(changeSequenceIncrementalStatistics) / sumOfRuntimeForIndividualChangesIncrementalMedian);
   }

   private static TGGRule createDeepCopy(final TGGRule originalRule)
   {
      final TGGRule tggRule = EcoreUtil.copy(originalRule);
      final ResourceSet resourceSet = eMoflonEMFUtil.createDefaultResourceSet();
      Resource resource = resourceSet.createResource(URI.createFileURI("C:\\tmp\\tmpResourceSet.xmi"));
      resource.getContents().add(tggRule);
      return tggRule;
   }

   // Cannot use List#addAll + Arrays.toList
   private void appendValuesOfStatisticsToColumns(List<Object> columns, DescriptiveStatistics statistics)
   {
      for (final Double value : statistics.getValues())
      {
         columns.add(formatTimeInMillis(value));
      }
   }

   private static double median(DescriptiveStatistics statistics)
   {
      return statistics.getPercentile(50);
   }

   private void undoModifications(final EObject element, final EMoflonDiagramTextProvider diagramTextProvider,
         final EvaluationTGGRuleModifier evaluationTGGRuleModifier, final TGGRule tggRule, List<ObjectVariable> addedObjectVariables,
         ObjectVariable renamedObjectVariable, List<ObjectVariable> removedObjectVariables)
   {
      // Undo changes
      evaluationTGGRuleModifier.undoRemove(tggRule, removedObjectVariables);
      evaluationTGGRuleModifier.undoRename(renamedObjectVariable);
      evaluationTGGRuleModifier.undoAdd(addedObjectVariables);
      diagramTextProvider.clearCache();
      diagramTextProvider.modelToDot(element); // Batch
   }

   private EMoflonDiagramTextProvider determineDiagramTextProvider(final EObject element)
   {
      final EMoflonDiagramTextProvider diagramTextProvider;

      if (sdmDiagramTextProvider.isElementValidInput(element))
      {
         diagramTextProvider = sdmDiagramTextProvider;
      } else if (tggRuleDiagramTextProvider.isElementValidInput(element))
      {
         diagramTextProvider = tggRuleDiagramTextProvider;
      } else if (storyPatternDiagramTextProvider.isElementValidInput(element))
      {
         diagramTextProvider = storyPatternDiagramTextProvider;
      } else if (precendenceGraphTextProvider.isElementValidInput(element))
      {
         diagramTextProvider = precendenceGraphTextProvider;
      } else if (tripleMatchTextProvider.isElementValidInput(element))
      {
         diagramTextProvider = tripleMatchTextProvider;
      } else
      {
         diagramTextProvider = null;
      }
      return diagramTextProvider;
   }

   private String determineTypeOfModel(final EObject element)
   {
      final String type;
      if (sdmDiagramTextProvider.isElementValidInput(element))
      {
         type = SDM_TYPE;
      } else if (tggRuleDiagramTextProvider.isElementValidInput(element))
      {
         type = TGG_RULE_TYPE;
      } else if (storyPatternDiagramTextProvider.isElementValidInput(element))
      {
         type = STORY_PATTERN_TYPE;
      } else if (precendenceGraphTextProvider.isElementValidInput(element))
      {
         type = PRECEDENCE_GRAPH_TYPE;
      } else if (tripleMatchTextProvider.isElementValidInput(element))
      {
         type = TRIPLE_MATCH_TYPE;
      } else
      {
         type = null;
      }
      return type;
   }

   private static String joinWithCsvSeparator(final List<? extends Object> columns)
   {
      return columns.stream().map(Object::toString).collect(Collectors.joining(CSV_SEP));
   }

   private static String formatTimeInMillis(double timeInMillis)
   {
      return String.format(Locale.US, "%.2f", timeInMillis);
   }

   private static String calculateNormalizedIdentifier(final EObject element)
   {
      return IdentifierProvider.calculateIdentifier(element).replaceAll("\\s+", "_").replaceAll(CSV_SEP, "_").replaceAll("'", "");
   }

   private void createRootFolderIfNecessary()
   {
      if (!this.rootFolder.exists())
      {
         this.rootFolder.mkdirs();
      }
   }

   private static File determineRootFolder()
   {
      // Use for instance: -DINCR_VIZ_ROOT_FOLDER=C:\Users\rkluge\tmp
      String rootFolderEnvVar = System.getProperty("INCR_VIZ_ROOT_FOLDER");
      if (rootFolderEnvVar != null)
      {
         return new File(rootFolderEnvVar);
      } else
      {
         return new File("C:/tmp/");
      }
   }

   private void addTitlesIfNecessary() throws IOException
   {
      if (!targetFile.exists())
      {
         FileUtils.writeLines(targetFile, Arrays.asList(joinWithCsvSeparator(this.headerOfResultsFile)), true);
      }
   }

   private List<String> getHeaderOfResultsFile()
   {
      final List<String> header = new ArrayList<>();
      header.addAll(getHeaderForGeneralInformation());
      header.addAll(getHeaderForRQ1());

      this.lengthOfHeaderWithRQ1 = header.size();

      header.addAll(getHeaderForRQ2());
      return header;
   }

   private List<String> getHeaderForGeneralInformation()
   {
      return Arrays.asList("datetime", "project", "type", "id", "objectNodeCount", "objectEdgeCount", "objectSize");
   }

   private List<String> getHeaderForRQ2()
   {
      final List<String> headerForRQ2 = new ArrayList<>();
      final String rqPrefix = "RQ2";
      for (final String changeType : Arrays.asList("_C1_add", "_C2_rename", "_C3_remove", "_C4_sequence"))
      {
         final String prefix = rqPrefix + changeType;
         IntStream.rangeClosed(1, repetitionCountOfIncrementalTransformation).forEach(i -> headerForRQ2.add(prefix + "_timeIncr_" + i));
         IntStream.rangeClosed(1, repetitionCountOfIncrementalTransformation).forEach(i -> headerForRQ2.add(prefix + "_timeBatch_" + i));
         headerForRQ2.add(prefix + "_timeIncr_mean");
         headerForRQ2.add(prefix + "_timeIncr_median");
         headerForRQ2.add(prefix + "_timeBatch_mean");
         headerForRQ2.add(prefix + "_timeBatch_median");
         headerForRQ2.add(prefix + "_timeIncrOverBatch_mean");
         headerForRQ2.add(prefix + "_timeIncrOverBatch_median");
      }
      // Comparison between separate changes and change sequence (only for incremental mode)
      headerForRQ2.add(rqPrefix + "_timeSumOfSeparateChangesIncr_mean");
      headerForRQ2.add(rqPrefix + "_timeSumOfSeparateChangesIncr_median");
      headerForRQ2.add(rqPrefix + "_timeSequenceChangeOverSeparateChangesIncr_mean");
      headerForRQ2.add(rqPrefix + "_timeSequenceChangeOverSeparateChangesIncr_median");
      return headerForRQ2;
   }

   private List<String> getHeaderForRQ1()
   {
      final List<String> headerForRQ1 = new ArrayList<>();
      final String rqPrefix = "RQ1";
      IntStream.rangeClosed(1, repetitionCountOfBatchTransformation).forEach(i -> headerForRQ1.add(rqPrefix + "_timeBatch_" + i));
      headerForRQ1.add(rqPrefix + "_timeBatch_mean");
      headerForRQ1.add(rqPrefix + "_timeBatch_median");
      return headerForRQ1;
   }

   private int getLengthOfHeaderForRQ1andRQ2()
   {
      return this.headerOfResultsFile.size();
   }

   private int getLengthOfHeaderForRQ1Only()
   {
      return this.lengthOfHeaderWithRQ1;
   }

   public static Visualizer getInstance()
   {
      return INSTANCE;
   }

   public void resetCounter()
   {
      this.visualizedItemCounter = 0;
   }

   public int getVisualizedItemCounter()
   {
      return this.visualizedItemCounter;
   }

   public boolean isInterestingCandidate(EObject eObject)
   {
      final EMoflonDiagramTextProvider diagramTextProvider = determineDiagramTextProvider(eObject);

      return diagramTextProvider != null;
   }
}
