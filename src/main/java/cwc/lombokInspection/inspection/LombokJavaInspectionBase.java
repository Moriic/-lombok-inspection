package cwc.lombokInspection.inspection;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.library.JavaLibraryUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiElementVisitor;

import cwc.lombokInspection.LombokClassNames;

import org.jetbrains.annotations.NotNull;

public abstract class LombokJavaInspectionBase extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public final @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    Module module = ModuleUtilCore.findModuleForFile(holder.getFile());
    if (!JavaLibraryUtil.hasLibraryClass(module, LombokClassNames.GETTER)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return createVisitor(holder, isOnTheFly);
  }

  @NotNull
  protected abstract PsiElementVisitor createVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly);
}
