package org.moflon.paper.incrviz;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EOperation;
import org.moflon.core.utilities.MoflonUtil;
import org.moflon.tgg.language.TGGRule;

import SDMLanguage.activities.Activity;
import SDMLanguage.activities.MoflonEOperation;
import SDMLanguage.activities.StoryNode;
import SDMLanguage.patterns.StoryPattern;

/**
 * Utility class for calculating IDs of EMF objects
 */
public class IdentifierProvider
{

   static String calculateIdentifier(final EObject element)
   {
      if (element == null)
         return "null";
      else if (element instanceof MoflonEOperation)
         return calculateIdentifierOfMoflonEOperation((MoflonEOperation) element);
      else if (element instanceof Activity)
         return calculateIdentifierOfActivity((Activity) element);
      else if (element instanceof TGGRule)
         return calculateIdentifierOfTggRule((TGGRule) element);
      else if (element instanceof StoryPattern)
         return calculateIdentifierOfStoryPattern((StoryPattern) element);
      else
         return element.toString();
   }

   private static String calculateIdentifierOfStoryPattern(final StoryPattern storyPattern)
   {
      StoryNode storyNode = storyPattern.getStoryNode();
      if (storyNode == null)
      {
         return calculateIdentifier(storyPattern.eContainer()) + "/" + storyPattern.getComment();
      }
      Activity owningActivity = storyNode.getOwningActivity();
      if (owningActivity == null)
      {
         return calculateIdentifier(storyNode.eContainer()) + "/" + storyNode.getName();
      }
   
      EOperation owningOperation = owningActivity.getOwningOperation();
      if (owningOperation == null)
      {
         return calculateIdentifier(storyNode.eContainer()) + "/" + storyNode.getName();
      }
      String containingClassName = owningOperation.getEContainingClass().getName();
      return containingClassName + "/" + owningOperation.getName() + "/" + storyNode.getName();
   }

   static String calculateIdentifierOfMoflonEOperation(MoflonEOperation eMoflonOperation)
   {
      return MoflonUtil.getFQN(eMoflonOperation.getEContainingClass()) + "/" + eMoflonOperation.getName();
   }

   static String calculateIdentifierOfTggRule(final TGGRule tggRule)
   {
      if (tggRule.getTripleGraphGrammar() == null)
      {
         StoryNode storyNode = tggRule.getStoryNode();
         if (storyNode != null)
         {
            return storyNode.getName() + "/" + tggRule.getName();
         } else
         {
            return tggRule.getName();
         }
      }
      final String tggName = tggRule.getTripleGraphGrammar().getName();
      return tggName + "/" + tggRule.getName();
   }

   private static String calculateIdentifierOfActivity(final Activity activiy)
   {
      EOperation owningOperation = activiy.getOwningOperation();
      if (owningOperation == null)
      {
         EObject container = activiy.eContainer();
         if (container instanceof MoflonEOperation)
         {
            final MoflonEOperation operation = (MoflonEOperation) container;
            return calculateIdentifierOfMoflonEOperation(operation);
         } else
         {
            return container.toString();
         }
      }
      String containingClassName = owningOperation.getEContainingClass().getName();
      return containingClassName + "/" + owningOperation.getName();
   }

}
