package cwc.lombokInspection.inspection;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.jvm.JvmClassKind;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.ig.psiutils.CommentTracker;

import cwc.lombokInspection.LombokClassNames;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public abstract class LombokGetterOrSetterMayBeUsedInspection extends LombokJavaInspectionBase {

  @NotNull
  @Override
  protected PsiElementVisitor createVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new LombokGetterOrSetterMayBeUsedVisitor(holder, null);
  }

  private class LombokGetterOrSetterMayBeUsedVisitor extends JavaElementVisitor {
    private final @Nullable ProblemsHolder myHolder;

    private final @Nullable LombokGetterOrSetterMayBeUsedInspection.LombokGetterOrSetterMayBeUsedFix
      myLombokGetterOrSetterMayBeUsedFix;

    private LombokGetterOrSetterMayBeUsedVisitor(@Nullable ProblemsHolder holder, @Nullable
    LombokGetterOrSetterMayBeUsedInspection.LombokGetterOrSetterMayBeUsedFix lombokGetterOrSetterMayBeUsedFix) {
      this.myHolder = holder;
      this.myLombokGetterOrSetterMayBeUsedFix = lombokGetterOrSetterMayBeUsedFix;
    }

    @Override
    public void visitJavaFile(@NotNull PsiJavaFile psiJavaFile) {
    }

    @Override
    public void visitClass(@NotNull PsiClass psiClass) {
      if (psiClass.getClassKind() != JvmClassKind.CLASS) {
        return;
      }
      List<PsiField> annotatedFields = new ArrayList<>();
      List<Pair<PsiField, PsiMethod>> instanceCandidates = new ArrayList<>();
      List<Pair<PsiField, PsiMethod>> staticCandidates = new ArrayList<>();
      for (PsiMethod method : psiClass.getMethods()) {
        processMethod(method, instanceCandidates, staticCandidates);
      }
      boolean isLombokAnnotationAtClassLevel = true;
      for (PsiField field : psiClass.getFields()) {
        PsiAnnotation annotation = field.getAnnotation(getAnnotationName());
        if (annotation != null) {
          if (!annotation.getAttributes().isEmpty() || field.hasModifierProperty(PsiModifier.STATIC)) {
            isLombokAnnotationAtClassLevel = false;
          }
          else {
            annotatedFields.add(field);
          }
        }
        // else if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        //   boolean found = false;
        //   for (Pair<PsiField, PsiMethod> instanceCandidate : instanceCandidates) {
        //     if (field.equals(instanceCandidate.getFirst())) {
        //       found = true;
        //       break;
        //     }
        //   }
        //   isLombokAnnotationAtClassLevel = found;
        // }

        if (!isLombokAnnotationAtClassLevel) {
          break;
        }
      }
      List<Pair<PsiField, PsiMethod>> allCandidates = new ArrayList<>(staticCandidates);
      if (isLombokAnnotationAtClassLevel && (!instanceCandidates.isEmpty() || !annotatedFields.isEmpty())) {
        warnOrFix(psiClass, instanceCandidates, annotatedFields);
      } else {
        allCandidates.addAll(instanceCandidates);
      }
      for (Pair<PsiField, PsiMethod> candidate : allCandidates) {
        warnOrFix(candidate.getFirst(), candidate.getSecond());
      }
    }

    public void visitMethodForFix(@NotNull PsiMethod psiMethod) {
      List<Pair<PsiField, PsiMethod>> fieldsAndMethods = new ArrayList<>();
      if (!processMethod(psiMethod, fieldsAndMethods, fieldsAndMethods)) {
        return;
      }
      if (!fieldsAndMethods.isEmpty()) {
        final Pair<PsiField, PsiMethod> psiFieldPsiMethodPair = fieldsAndMethods.get(0);
        warnOrFix(psiFieldPsiMethodPair.getFirst(), psiFieldPsiMethodPair.getSecond());
      }
    }

    private void warnOrFix(@NotNull PsiClass psiClass, @NotNull List<Pair<PsiField, PsiMethod>> fieldsAndMethods,
      @NotNull List<PsiField> annotatedFields) {
      if (myHolder != null) {
        String className = psiClass.getName();
        if (StringUtil.isNotEmpty(className)) {
          final PsiIdentifier psiClassNameIdentifier = psiClass.getNameIdentifier();
          final LocalQuickFix fix = new LombokGetterOrSetterMayBeUsedFix(className);
          myHolder.registerProblem(psiClass, getClassErrorMessage(className),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            psiClassNameIdentifier != null ? psiClassNameIdentifier.getTextRangeInParent() : psiClass.getTextRange(),
            fix);
        }
      } else if (myLombokGetterOrSetterMayBeUsedFix != null) {
        myLombokGetterOrSetterMayBeUsedFix.effectivelyDoFix(psiClass, fieldsAndMethods, annotatedFields);
      }
    }

    private void warnOrFix(@NotNull PsiField field, @NotNull PsiMethod method) {
      if (myHolder != null) {
        String fieldName = field.getName();
        final LocalQuickFix fix = new LombokGetterOrSetterMayBeUsedFix(fieldName);
        myHolder.registerProblem(method, getFieldErrorMessage(fieldName), fix);
      } else if (myLombokGetterOrSetterMayBeUsedFix != null) {
        myLombokGetterOrSetterMayBeUsedFix.effectivelyDoFix(field, method);
      }
    }
  }

  private class LombokGetterOrSetterMayBeUsedFix extends PsiUpdateModCommandQuickFix {
    private final @NotNull String myText;

    Pattern pattern = Pattern.compile("[a-z][A-Z].*");

    private LombokGetterOrSetterMayBeUsedFix(@NotNull String text) {
      myText = text;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
      return getFixName(myText);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return getFixFamilyName();
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (element instanceof PsiMethod) {
        new LombokGetterOrSetterMayBeUsedVisitor(null, this).visitMethodForFix((PsiMethod) element);
      }
      else if (element instanceof PsiClass) {
        new LombokGetterOrSetterMayBeUsedVisitor(null, this).visitClass((PsiClass) element);
      }
    }

    private void effectivelyDoFix(@NotNull PsiField field, @NotNull PsiMethod method) {
      if (!addLombokAnnotation(field)) {
        return;
      }
      removeMethod(field, method);
    }

    public void effectivelyDoFix(@NotNull PsiClass aClass, @NotNull List<Pair<PsiField, PsiMethod>> fieldsAndMethods,
      @NotNull List<PsiField> annotatedFields) {
      if (!addLombokAnnotation(aClass)) return;
      for (Pair<PsiField, PsiMethod> fieldAndMethod : fieldsAndMethods) {
        PsiField field = fieldAndMethod.getFirst();
        PsiMethod method = fieldAndMethod.getSecond();
        removeMethod(field, method);
      }
      for (PsiField annotatedField : annotatedFields) {
        PsiAnnotation oldAnnotation = annotatedField.getAnnotation(getAnnotationName());
        if (oldAnnotation != null) {
          new CommentTracker().deleteAndRestoreComments(oldAnnotation);
        }
      }
    }

    private boolean addLombokAnnotation(@NotNull PsiModifierListOwner fieldOrClass) {
      final PsiModifierList modifierList = fieldOrClass.getModifierList();
      if (modifierList == null) {
        return false;
      }
      PsiAnnotation[] annotations = fieldOrClass.getAnnotations();
      for (PsiAnnotation annotation : annotations) {
        String annotationName = annotation.getQualifiedName();
        if (annotationName != null &&
          (annotationName.equals(getAnnotationName()) || annotationName.equals(LombokClassNames.DATA))) {
          return true;
        }
      }
      Project project = fieldOrClass.getProject();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiAnnotation annotation = factory.createAnnotationFromText("@" + getAnnotationName(), fieldOrClass);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(annotation);
      modifierList.addAfter(annotation, null);
      return true;
    }

    private void removeMethod(@NotNull PsiField field, @NotNull PsiMethod method) {
      CommentTracker tracker = new CommentTracker();
      if (!pattern.matcher(field.getName()).matches()) {
        tracker.delete(method);
      }
    }
  }

  @NotNull
  protected abstract @NonNls String getAnnotationName();

  @NotNull
  protected abstract @Nls String getFieldErrorMessage(String fieldName);

  @NotNull
  protected abstract @Nls String getClassErrorMessage(String className);

  protected abstract boolean processMethod(@NotNull PsiMethod method,
    @NotNull List<Pair<PsiField, PsiMethod>> instanceCandidates,
    @NotNull List<Pair<PsiField, PsiMethod>> staticCandidates);

  @NotNull
  protected abstract @Nls String getFixName(String text);

  @NotNull
  protected abstract @Nls String getFixFamilyName();
}
