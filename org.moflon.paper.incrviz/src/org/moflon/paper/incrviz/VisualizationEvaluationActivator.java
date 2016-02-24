package org.moflon.paper.incrviz;

import org.moflon.core.utilities.EMoflonPlugin;

/**
 * Activator of this plugin
 */
public class VisualizationEvaluationActivator extends EMoflonPlugin
{
   public static VisualizationEvaluationActivator getDefault() {
      VisualizationEvaluationActivator plugin = getPlugin(VisualizationEvaluationActivator.class);
      if (plugin == null)
         throw new IllegalStateException("Plugin has not yet been set!");
      return plugin;
   }
}
