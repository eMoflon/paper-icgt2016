package org.moflon.paper.incrviz;

import java.util.Arrays;

import org.apache.log4j.Logger;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.PackageNotFoundException;
import org.moflon.core.utilities.WorkspaceHelper;
import org.moflon.core.utilities.eMoflonEMFUtil;

final class EObjectCounter implements IResourceVisitor
{
   private Logger logger = Logger.getLogger(EObjectCounter.class);

   int visitedObjectCount = 0;

   private IProgressMonitor monitor;

   public EObjectCounter(IProgressMonitor monitor)
   {
      this.monitor = monitor;
   }

   @Override
   public boolean visit(IResource resource) throws CoreException
   {
      if (isUninterestingResource(resource))
      {
         // Do not descend into child resources
         return false;
      }

      IFile file = resource.getAdapter(IFile.class);
      if (file != null && hasInterestingName(resource))
      {
         try
         {
            final Resource content = readEResource(file);
            final TreeIterator<EObject> allContentsIterator = content.getAllContents();
            while (allContentsIterator.hasNext())
            {
               try
               {
                  final EObject eObject = allContentsIterator.next();
                  if (Visualizer.getInstance().isInterestingCandidate(eObject))
                  {
                     ++visitedObjectCount;
                  }
                  WorkspaceHelper.checkCanceledAndThrowInterruptedException(monitor);
               } catch (final InterruptedException e)
               {
                  throw e;
               } catch (final Exception e)
               {
                  logger.error(e);
               }
            }
         } catch (final InterruptedException e)
         {
            throw new RuntimeException("User canceled", e);
         } catch (final RuntimeException | PackageNotFoundException e)
         {
            logger.error(e);
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
      return name.endsWith(".xmi");
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
}