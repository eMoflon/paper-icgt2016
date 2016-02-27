package org.moflon.paper.incrviz;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.PackageNotFoundException;
import org.moflon.core.utilities.WorkspaceHelper;
import org.moflon.core.utilities.eMoflonEMFUtil;

final class ICGT2016EvaluationJob extends WorkspaceJob
{
   private Logger logger = Logger.getLogger(ICGT2016EvaluationJob.class);
   private final List<IProject> projects;

   private final Visualizer visualizer;

   ICGT2016EvaluationJob(String name, List<IProject> projects, Visualizer visualizer)
   {
      super(name);
      this.projects = projects;
      this.visualizer = visualizer;
   }

   @Override
   public IStatus runInWorkspace(IProgressMonitor monitor)
   {
      try
      {
         final int objectCounter = countCandidates(projects, monitor);
         monitor.beginTask(String.format("Collect visualization data of %d EObjects", objectCounter), objectCounter);
         for (final IProject project : projects)
         {
            if (project.isOpen())
            {
               logger.info("Current project: " + project);
               project.accept(new IResourceVisitor() {

                  @Override
                  public boolean visit(final IResource resource) throws CoreException
                  {
                     if (isUninterestingResource(resource))
                     {
                        // Do not descend into child resources
                        return false;
                     }

                     final IFile file = resource.getAdapter(IFile.class);
                     if (file != null && hasInterestingName(resource))
                     {
                        logger.info("Collecting statistidcs in " + file);

                        try
                        {
                           final Resource content = readEResource(file);
                           final TreeIterator<EObject> allContentsIterator = content.getAllContents();
                           while (allContentsIterator.hasNext())
                           {
                              try
                              {
                                 final EObject next = allContentsIterator.next();
                                 if (visualizer.isInterestingCandidate(next))
                                 {
                                    visualizer.visualize(next, file);
                                    monitor.worked(1);
                                 }

                                 WorkspaceHelper.checkCanceledAndThrowInterruptedException(monitor);
                              } catch (final InterruptedException e)
                              {
                                 throw e;
                              } catch (final Exception e)
                              {
                                 logException(e);
                              }
                           }
                        } catch (final InterruptedException e)
                        {
                           throw new RuntimeException("User canceled", e);
                        } catch (final RuntimeException | PackageNotFoundException e)
                        {
                           logException(e);
                        }
                     }

                     // continue with next resource
                     return true;
                  }

                  // Filters out folders that are definitely not promising for efficiency purposes.
                  private boolean isUninterestingResource(IResource resource)
                  {
                     return Arrays.asList("bin", "gen", "src").contains(resource.getName());
                  }

                  private boolean hasInterestingName(IResource resource)
                  {
                     return isEcoreFile(resource) || isProtocolFile(resource);
                  }

                  private boolean isProtocolFile(IResource resource)
                  {
                     String name = resource.getName();
                     return name.matches(".*protocol.*.xmi") || name.endsWith("corr.xmi");
                  }

                  /**
                   * Returns whether the given resource is a regular Ecore file.
                   * 
                   * File names ending with ".pre.ecore" are ignored because they are 'non-precompiled TGGs'.
                   * 
                   * @param resource
                   * @return
                   */
                  private boolean isEcoreFile(IResource resource)
                  {
                     return resource.getName().endsWith(".ecore") && !resource.getName().endsWith(".pre.ecore");
                  }

                  private Resource readEResource(IFile file) throws PackageNotFoundException
                  {
                     final Resource resource = eMoflonEMFUtil.getResourceFromFileIntoDefaultResourceSet(file);
                     return resource;
                  }
                  
                  private void logException(Exception e)
                  {
                     logger.error("Problem: " + e.toString() + " - Stacktrace. " + ExceptionUtils.getStackTrace(e));
                  }
               });
            }
         }

         logger.info(String.format("Visualization of %d models done.", visualizer.getVisualizedItemCounter()));

      } catch (final CoreException e)
      {
         logger.error(e);
      } catch (final RuntimeException e)
      {
         if (e.getCause() instanceof InterruptedException)
         {
            logger.info("User canceled visualization");
         } else
         {
            throw e;
         }
      } finally
      {
         monitor.done();
      }
      return Status.OK_STATUS;
   }

   private int countCandidates(final List<IProject> projects, IProgressMonitor monitor) throws CoreException
   {
      int objectCounter = 0;
      for (final IProject project : projects)
      {
         if (project.isOpen())
         {
            final EObjectCounter visitor = new EObjectCounter(monitor);
            project.accept(visitor);
            objectCounter += visitor.visitedObjectCount;
         }
      }
      return objectCounter;
   }
}