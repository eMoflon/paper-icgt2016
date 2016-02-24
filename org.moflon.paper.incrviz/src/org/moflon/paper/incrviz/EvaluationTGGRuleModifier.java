package org.moflon.paper.incrviz;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.moflon.tgg.language.TGGRule;

import SDMLanguage.patterns.ObjectVariable;
import SDMLanguage.patterns.PatternsFactory;

/**
 * This class performs the actual changes to the TGG rules
 */
public class EvaluationTGGRuleModifier
{

   private static final String RENAME_CHANGE_SUFFIX = "[RENAMED]";
   private Random random;

   public EvaluationTGGRuleModifier(Random random)
   {
      this.random = random;
   }

   public List<ObjectVariable> performRemoveObjectVariable(final TGGRule rule, final int count)
   {
      final List<ObjectVariable> removedObjectVariables = new ArrayList<>();
     
      for (int i = 0; i < count && !rule.getObjectVariable().isEmpty(); ++i)
      {
         final EList<ObjectVariable> objectVariables = rule.getObjectVariable();
         final int selectedIndex = random.nextInt(objectVariables.size());
         ObjectVariable removedObjectVariable = objectVariables.get(selectedIndex);
         EcoreUtil.remove(removedObjectVariable);
         removedObjectVariables.add(removedObjectVariable);
      }
      
      return removedObjectVariables;
   }

   public ObjectVariable performChangeObjectVariableNameChange(final TGGRule rule)
   {
      final EList<ObjectVariable> objectVariables = rule.getObjectVariable();
      ObjectVariable renamedObjectVariable = objectVariables.get(random.nextInt(objectVariables.size()));
      renamedObjectVariable.setName(renamedObjectVariable.getName() + RENAME_CHANGE_SUFFIX);
      
      return renamedObjectVariable;
   }

   public List<ObjectVariable> performChangeAddObjectVariables(final TGGRule tggRule, final int count)
   {
      final List<ObjectVariable> addedObjectVariables = new ArrayList<>();
      
      final ObjectVariable templateVariable = tggRule.getObjectVariable().isEmpty() ? null : tggRule.getObjectVariable().get(0);
      
      for (final String name : IntStream.rangeClosed(1, count).mapToObj(i -> "New OV" + i).collect(Collectors.toList()))
      {
         final ObjectVariable addedObjectVariable = templateVariable != null ? EcoreUtil.copy(templateVariable) : PatternsFactory.eINSTANCE.createObjectVariable();
         tggRule.getObjectVariable().add(addedObjectVariable);
         addedObjectVariable.setName(name);
         
         addedObjectVariables.add(addedObjectVariable);
      }
      
      return addedObjectVariables;
   }

   public void undoRemove(TGGRule tggRule, List<ObjectVariable> removedObjectVariables)
   {
      tggRule.getObjectVariable().addAll(removedObjectVariables);
   }

   public void undoRename(ObjectVariable renamedObjectVariable)
   {
      renamedObjectVariable.setName(renamedObjectVariable.getName().replace(RENAME_CHANGE_SUFFIX, ""));
   }

   public void undoAdd(List<ObjectVariable> addedObjectVariables)
   {
      addedObjectVariables.forEach(ov -> EcoreUtil.delete(ov));
   }

}
