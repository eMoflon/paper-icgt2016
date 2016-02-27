package org.moflon.paper.incrviz;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;
import org.moflon.ide.ui.admin.handlers.AbstractCommandHandler;

public class ICGT2016EvaluationHandler extends AbstractCommandHandler
{
   /**
    * This handler triggers a visitor that iterates over all resource in the workspace (except 'uninteresting'
    * resources).
    */
   @Override
   public Object execute(ExecutionEvent event) throws ExecutionException
   {
      final ISelection selection = HandlerUtil.getCurrentSelectionChecked(event);
      final List<IProject> projects = new ArrayList<>();
      if (selection instanceof StructuredSelection)
      {
         final StructuredSelection structuredSelection = (StructuredSelection) selection;
         for (final Iterator<?> selectionIterator = structuredSelection.iterator(); selectionIterator.hasNext();)
         {
            projects.addAll(getProjects(selectionIterator.next()));
         }
      }
      final Visualizer visualizer = Visualizer.getInstance();
      visualizer.resetCounter();

      if (!projects.isEmpty())
      {
         WorkspaceJob job = new ICGT2016EvaluationJob("Performing evaluation", projects, visualizer);
         job.schedule();
      }
      return null;
   }

   public void logException(Exception e)
   {
      logger.error("Problem: " + e.toString() + " - Stacktrace. " + ExceptionUtils.getStackTrace(e));
   }
}
